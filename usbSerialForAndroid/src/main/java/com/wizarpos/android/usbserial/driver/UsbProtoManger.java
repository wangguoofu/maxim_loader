package com.wizarpos.android.usbserial.driver;

import android.util.Log;
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

    private UsbSerialPort serialPort;
    static private char seqenceNumber = 0;

    public UsbProtoManger(UsbSerialPort port) {
        serialPort = port;
    }

    private byte calculate_lrc(byte[] data, int dataLen)
    {
        int loop = 0;
        byte lrc = 0;


        for(loop = 0; loop < dataLen; loop++)
        {
            lrc ^= data[loop];
        }

        return lrc;
    }

    public String  sendCmd(String request, int timeOutMs) throws IOException{
        int ret = 0;
        byte lrc = 0;
        byte[] out = new byte[FRAME_SIZEOF_HEADER + request.getBytes().length + FRAME_SIZEOF_LRC];

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
        seqenceNumber++;

        return "nothing";
    }

    public String requestForConnection(int timeOutMs){
        return "nothing";
    }

    public String  sendResponse(String reponse){
        return "nothing";
    }
}
