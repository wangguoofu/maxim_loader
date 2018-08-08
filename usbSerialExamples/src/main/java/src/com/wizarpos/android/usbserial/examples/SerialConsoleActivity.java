/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 anonymous <opensource@wizarpos.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.wizarpos.android.usbserial.maxim;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import com.wizarpos.android.usbserial.driver.UsbProtoManger;
import com.wizarpos.android.usbserial.driver.UsbSerialPort;
import com.wizarpos.android.usbserial.util.HexDump;
import com.wizarpos.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Calendar;
import java.util.Date;
import java.security.MessageDigest;

/**
 * Monitors a single {@link UsbSerialPort} instance, showing all data
 * received.
 *
 * @author anonymous (opensource@wizarpos.com)
 */
public class SerialConsoleActivity extends Activity {

    private final String TAG = SerialConsoleActivity.class.getSimpleName();

    /**
     * Driver instance, passed in statically via
     * {@link #show(Context, UsbSerialPort)}.
     *
     * <p/>
     * This is a devious hack; it'd be cleaner to re-create the driver using
     * arguments passed in with the {@link #startActivity(Intent)} intent. We
     * can get away with it because both activities will run in the same
     * process, and this is a simple demo.
     */
    private static UsbSerialPort sPort = null;

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;
    private CheckBox chkDTR;
    private CheckBox chkRTS;
    private EditText testInput;
    private Button testSend;
    private Spinner spinner;
    private Button testSendMethod;
    private Button clear;
    private UsbProtoManger usbProtoManger;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
            SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SerialConsoleActivity.this.updateReceivedData(data);
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        //chkDTR = (CheckBox) findViewById(R.id.checkBoxDTR);
        //chkRTS = (CheckBox) findViewById(R.id.checkBoxRTS);
        testInput =(EditText) findViewById(R.id.inputTest);
        testSend = (Button) findViewById(R.id.testSend);
        clear = (Button) findViewById(R.id.contextClear);
        spinner = (Spinner) findViewById(R.id.spinner);
        testSendMethod = (Button) findViewById(R.id.testSendMethod);
/*
        chkDTR.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    sPort.setDTR(isChecked);
                }catch (IOException x){}
            }
        });

        chkRTS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    sPort.setRTS(isChecked);
                }catch (IOException x){}
            }
        });
*/
        testSend.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            usbProtoManger.sendCmd(testInput.getText().toString(), 2000);
                        } catch (IOException e) {
                            SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SerialConsoleActivity.this,
                                            "sPort.write failed",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                            Log.e(TAG, "sPort.write failed");
                        }
                    }
                }.start();
            }
        });

        testSendMethod.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread() {
                    @Override
                    public void run() {
                        testMethod(spinner.getSelectedItem().toString());
                    }
                }.start();
            }
        });

        clear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mDumpTextView.setText("");
            }
        });

    }

    private final static String  md5Encrypt(String plaintext) {
        char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f' };
        try {
            byte[] btInput = plaintext.getBytes();
            // 获得MD5摘要算法的 MessageDigest 对象
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            // 使用指定的字节更新摘要
            mdInst.update(btInput);
            // 获得密文
            byte[] md = mdInst.digest();
            // 把密文转换成十六进制的字符串形式
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }

    private void testMethod(String method)
    {
        final String response;
        String apiversion = "1.0.1";
        String deviceId = "A380A7DE0100001EMD";
        String apiSecret = "95:7D:E0:10:00:01";

        long time = System.currentTimeMillis()/1000;//获取系统时间的10位的时间戳
        String  timeStamp =String.valueOf(time);
        String  sign = md5Encrypt(deviceId+apiSecret+method+timeStamp);

        StringBuffer stringBuffer = new StringBuffer("api_version:");
        stringBuffer.append(apiversion).append("\n");
        stringBuffer.append("method:").append(method).append("\n");
        stringBuffer.append("timestamp:").append(timeStamp).append("\n");
        stringBuffer.append("device_id:").append(deviceId).append("\n");
        stringBuffer.append("sign:").append(sign).append("\n");

        try {
            if(method.equals("device_request_for_connection")) {
                stringBuffer.append("user_unique_code:").append("wizarpos").append('\n');
                response = usbProtoManger.sendResponse(stringBuffer.toString());
            } else if(method.equals("app_request_for_show_pwd_keyboard")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_input_pwd_finish")){
                stringBuffer.append("num_index:").append("1,2,3,4,5,6").append('\n');
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_tap_pwd")){
                stringBuffer.append("num_index:").append("1").append('\n');
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_backspace_number")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_init")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_set_pwd")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_mnemonic_page")){
                stringBuffer.append("page:").append("2").append('\n');
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_init_check")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_restore_start")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_word_input")){
                stringBuffer.append("letter_index:").append("1,2,3,4,5,6,7,8").append('\n');
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_word_input_finish")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_delete_word")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_backspace_word")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_tap_word")){
                stringBuffer.append("letter_index:").append("1").append('\n');
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_restore_check")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_add_token")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_get_token_list")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_remove_token")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_signature_start")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_signature")){
                stringBuffer.append("token_type:").append("coin").append('\n');
                stringBuffer.append("token_index:").append("1").append('\n');
                stringBuffer.append("token_address:").append('\n');
                stringBuffer.append("content:").append("02000000018450788654925a6e9af85e116" +
                        "cf922779a54959566eff5bbf4a15ff86ec2d3f1000000001976a91416d169823039" +
                        "b0c73096e44a31ee82f592403fd088acffffffff0210270000000000001976a914c7" +
                        "f73a655db4ffe121ddbbfe8f282da3586b6a3988ac706f9800000000001976a91416" +
                        "d169823039b0c73096e44a31ee82f592403fd088ac0000000001000000").append('\n');
                stringBuffer.append("decimal:").append("8").append('\n');
                stringBuffer.append("unit:").append("BTC").append('\n');
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_signature_finish")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_signature_check")){
                stringBuffer.append("pwd_ticket:").append(testInput.getText().toString()).append('\n');
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 20000);
            } else if(method.equals("app_request_for_device_reset")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_mnemonic_check")){
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            } else if(method.equals("app_request_for_mnemonic_check_input")){
                stringBuffer.append("index:").append("1,2,6,4").append('\n');
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);
            }
            else
                response = usbProtoManger.sendCmd(stringBuffer.toString(), 2000);

            SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDumpTextView.append("==========================================\n");
                    mDumpTextView.append(response);
                    mDumpTextView.append("==========================================\n");
                }
            });
        } catch(IOException e){
            Log.e(TAG, "send cmd/response failed");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                Log.d(TAG, "ready to sport.close");
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
        finish();
    }

    void showStatus(TextView theTextView, String theLabel, boolean theValue){
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        theTextView.append(msg);
    }

    @Override
    protected void onResume() {
        int ret;
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            mTitleTextView.setText("No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                mTitleTextView.setText("Opening device failed");
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(921600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                usbProtoManger = new UsbProtoManger(sPort);


                /*
                showStatus(mDumpTextView, "CD  - Carrier Detect", sPort.getCD());
                showStatus(mDumpTextView, "CTS - Clear To Send", sPort.getCTS());
                showStatus(mDumpTextView, "DSR - Data Set Ready", sPort.getDSR());
                showStatus(mDumpTextView, "DTR - Data Terminal Ready", sPort.getDTR());
                showStatus(mDumpTextView, "DSR - Data Set Ready", sPort.getDSR());
                showStatus(mDumpTextView, "RI  - Ring Indicator", sPort.getRI());
                showStatus(mDumpTextView, "RTS - Request To Send", sPort.getRTS());
                */
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private boolean debugging = false;
    private void onDeviceStateChange() {
        Log.e(TAG, "+DEBUG.TEMP");
        stopIoManager();
        if (debugging) {
            startIoManager();
            return;
        }

        new Thread() {
            @Override
            public void run() {
                try {
                    final String connectString = usbProtoManger.requestForConnection(2000);
                    SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDumpTextView.append("==========================================\n");
                            mDumpTextView.append(connectString);
                            mDumpTextView.append("==========================================\n");
                        }
                    });
                } catch (IOException e) {
                    Log.e(TAG, "requestForConnection failed");
                }
            }
        }.start();

    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: \n"
                + HexDump.dumpHexString(data) + "\n\n";
        mDumpTextView.append(message);
        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param driver
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, SerialConsoleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

}
