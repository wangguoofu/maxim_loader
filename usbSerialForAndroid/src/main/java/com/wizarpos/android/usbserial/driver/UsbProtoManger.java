package com.wizarpos.android.usbserial.driver;

import android.util.Log;

//import com.wizarpos.android.usbserial.util.HexDump;

import java.io.IOException;

public class UsbProtoManger {
    private final String TAG = UsbProtoManger.class.getSimpleName();

    private final byte FRAME_START_BYTE = (byte)0xAA;
    private final byte FRAME_SIZEOF_START = 1;

    private final byte FRAME_RESERVE_OFFSET = FRAME_SIZEOF_START;
    private final byte FRAME_SIZEOF_RESERVE = 1;

    private final byte FRAME_LENGTH_OFFSET = FRAME_RESERVE_OFFSET+FRAME_SIZEOF_START;
    private final byte FRAME_SIZEOF_LENGTH = 2;

    private final byte FRAME_SEQ_NUM_OFFSET = FRAME_LENGTH_OFFSET+FRAME_SIZEOF_LENGTH;
    private final byte FRAME_SIZEOF_SEQ_NUM = 1;

    private final byte FRAME_SIZEOF_HEADER = FRAME_SIZEOF_START+FRAME_SIZEOF_RESERVE+FRAME_SIZEOF_LENGTH+FRAME_SIZEOF_SEQ_NUM;
    private final byte FRAME_CONTENT_OFFSET = FRAME_SEQ_NUM_OFFSET+FRAME_SIZEOF_SEQ_NUM;

    private final byte FRAME_SIZEOF_LRC = 1;

    private final byte FRAME_LOOKING_FOR_START = 0x00;
    private final byte FRAME_LOOKING_FOR_HEADER = 0x01;
    private final byte FRAME_LOOKING_FOR_CONTENT = 0x03;

    private UsbSerialPort serialPort;
    static private char seqenceNumber = 0;

    public UsbProtoManger(UsbSerialPort port) {
        serialPort = port;
    }

    private byte calculate_lrc(byte[] data, int dataLen){
        return calculate_lrc(data, 0, dataLen);
    }

    private byte calculate_lrc(byte[] data, int offset, int dataLen) {
        int loop = 0;
        byte lrc = 0;


        for(loop = offset; loop < dataLen; loop++)
        {
            lrc ^= data[loop];
        }

        return lrc;
    }

    private String readData(int timeOutMs) throws IOException{
        final byte[] block = new byte[64];
        final byte[] data = new byte[2048+FRAME_SIZEOF_HEADER+1];
        int offset = -1, dstOffset = 0;
        int ret;
        int i;
        int low, high;
        int contentLen;
        byte lrc;

        Log.d(TAG, "enter readData");

        //return "test\n";

        try{
            do {
                ret = serialPort.read(block, timeOutMs);
                Log.d(TAG, "ret = " + ret);
                for (i = 0; i < ret; i++) {
                    if (block[i] == FRAME_START_BYTE) {
                        offset = i;
                        break;
                    }
                }
            }while(offset == -1);
            Log.d(TAG, "offset = " + offset);
            System.arraycopy(block, offset, data, 0, ret-offset);
            dstOffset = ret - offset;

            if(ret-offset < FRAME_SIZEOF_HEADER)
            {
                ret = serialPort.read(block, timeOutMs);
                Log.d(TAG, "read left header");
                System.arraycopy(block, 0, data, dstOffset, ret);
                dstOffset += ret;
            }

            Log.d(TAG, "data[FRAME_LENGTH_OFFSET] = " + data[FRAME_LENGTH_OFFSET]);
            Log.d(TAG, "data[FRAME_LENGTH_OFFSET+1] = " + data[FRAME_LENGTH_OFFSET+1]);
            low = data[FRAME_LENGTH_OFFSET];
            low &= 0x0ff;
            Log.d(TAG, "low = " + low);
            high =data[FRAME_LENGTH_OFFSET+1]<<0x08;
            high &=0x0ff00;
            Log.d(TAG, "high = " + high);

            contentLen = high|low;
            Log.d(TAG, "contenLen = " + contentLen);

            if(contentLen > (dstOffset-FRAME_SIZEOF_HEADER))
            {
                Log.d(TAG, "downloading left data");
                do{
                    ret = serialPort.read(block, timeOutMs);
                    System.arraycopy(block, 0, data, dstOffset, ret);
                    dstOffset += ret;
                }
                while(dstOffset<(contentLen+FRAME_SIZEOF_HEADER));
            }

            dstOffset = dstOffset>contentLen+FRAME_SIZEOF_HEADER?contentLen+FRAME_SIZEOF_HEADER:dstOffset;
            lrc = calculate_lrc(data, dstOffset);
            Log.d(TAG, "lrc = " + lrc);
            //Log.d(TAG,"read data:"+HexDump.dumpHexString(data, 0, dstOffset));
            if(lrc != 0)
                throw new IOException("lrc error");
            if(data[FRAME_SEQ_NUM_OFFSET] != (byte)seqenceNumber)
                throw new IOException("seq error");

        } catch(IOException e){
            Log.e(TAG, "sPort.read failed");
            Log.e(TAG, e.getMessage());
            throw new IOException("usb read failed");
        }

        String retString = new String(data, FRAME_SIZEOF_HEADER, contentLen-1);
        Log.d(TAG, "content:" + retString);

        return retString;

    }

    private void sendData(String request, int timeOutMs) throws IOException{
        int ret = 0;
        byte lrc = 0;
        byte[] out = new byte[FRAME_SIZEOF_HEADER + request.getBytes().length + FRAME_SIZEOF_LRC];

        Log.d(TAG, "enter sendData");

        out[0] = FRAME_START_BYTE;
        out[FRAME_RESERVE_OFFSET] = 0;
        out[FRAME_LENGTH_OFFSET] = (byte)((request.getBytes().length + FRAME_SIZEOF_LRC)&0xff);
        out[FRAME_LENGTH_OFFSET+1] = (byte)(((request.getBytes().length + FRAME_SIZEOF_LRC)>>0x08)&0xff);
        out[FRAME_SEQ_NUM_OFFSET] = (byte)seqenceNumber;
        System.arraycopy(request.getBytes(), 0, out, FRAME_CONTENT_OFFSET, request.getBytes().length);
        lrc = calculate_lrc(out, out.length-1);
        out[FRAME_CONTENT_OFFSET + request.getBytes().length] = lrc;

        Log.d(TAG, "\nheader=" + FRAME_SIZEOF_HEADER + " conten offset =" + FRAME_CONTENT_OFFSET);
        Log.d(TAG, "total size=" + out.length);
        Log.d(TAG, "request:\n" + request);
        Log.d(TAG, "lrc =" + Integer.toHexString(lrc));

        try {
            ret = serialPort.write(out, timeOutMs);
            Log.d(TAG, "sendCmd: write return=" + ret);
        } catch(IOException e){
            Log.e(TAG, "sPort.write failed");
            throw new IOException("usb write failed");
        }
    }

    public String  sendCmd(String request, int timeOutMs) throws IOException{
        synchronized (this) {
            try {
                sendData(request, timeOutMs);
            } catch (IOException e) {
                Log.e(TAG, "sendData failed");
                throw new IOException("sendData failed");
            }

            String ret = readData(timeOutMs);
            seqenceNumber++;

            return ret;
        }
    }

    public String requestForConnection(int timeOutMs) throws IOException{
        synchronized (this) {
            try {
                seqenceNumber = 0;
                String ret = readData(timeOutMs);
                return ret;
            } catch (IOException e) {
                Log.e(TAG, "readResonse failed");
                throw new IOException("readResonse failed");
            }
        }
    }

    public String  sendResponse(String response) throws IOException{
        synchronized (this) {
            try {
                sendData(response, 2000);
            } catch (IOException e) {
                Log.e(TAG, "sendData failed");
                throw new IOException("sendData failed");
            }

            String ret = readData(2000);
            seqenceNumber++;

            return ret;
        }
    }
}
