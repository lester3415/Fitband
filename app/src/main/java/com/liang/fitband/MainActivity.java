package com.liang.fitband;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.desay.dsbluetooth.data.DSBLEAutoType;
import com.desay.dsbluetooth.data.DSBLEBandFuncType;
import com.desay.dsbluetooth.data.enums.DSBLEGender;
import com.desay.dsbluetooth.data.model.DSBLESyncData;
import com.desay.dsbluetooth.data.model.DSBLEUserInfo;
import com.desay.dsbluetooth.data.model.DSBLEVersion;
import com.desay.dsbluetooth.device.band.Band;
import com.desay.dsbluetooth.device.band.DSBLEBindCallback;
import com.desay.dsbluetooth.device.band.DSBLESyncCallback;
import com.desay.dsbluetooth.manager.keep.BLEAPIManager;
import com.desay.dsbluetooth.manager.keep.DSBLEDevice;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public class MainActivity extends AppCompatActivity {

    private String TAG = "MainActivity";
    private DSBLEDevice device;
    private DSBLESyncData syncData;
    private RequestQueue requestQueue;

    private String postUrl;
    private String FirmwareVersion;
    private String deviceSN;
    private String deviceBattery;
    private String heartRate;

    private double calorie;
    private int todaySteps;
    private double distance;

    private Thread syncThread;

    private ByteBuffer sendBuffer = ByteBuffer.allocate(128);
    private ByteBuffer dataBuffer = ByteBuffer.allocate(32);
//    private ByteBuffer stepsBuffer = ByteBuffer.allocate(16);
//    private ByteBuffer timeStampBuffer = ByteBuffer.allocate(16);
    private ProgressDialog dialog;

    private TextView tvFirmwareVersion;
    private TextView tvID;
    private TextView tvPower;
    private TextView tvHeartRate;
    private TextView tvCalorie;
    private TextView tvTotalSteps;
    private TextView tvDistance;

    private Button btnHeartRateSync;
    private Button btnHeartRateCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvFirmwareVersion = findViewById(R.id.tv_firmware_version);
        tvID = findViewById(R.id.tv_id);
        tvPower = findViewById(R.id.tv_power);
        tvHeartRate = findViewById(R.id.tv_heart_rate);
        tvCalorie = findViewById(R.id.tv_calorie);
        tvTotalSteps = findViewById(R.id.tv_total_steps);
        tvDistance = findViewById(R.id.tv_distance);

        btnHeartRateSync = findViewById(R.id.btn_heart_rate_sync);
        btnHeartRateCancel = findViewById(R.id.btn_heart_rate_cancel);

        btnHeartRateSync.setOnClickListener(myOnClickListener);
        btnHeartRateCancel.setOnClickListener(myOnClickListener);

        dialog = ProgressDialog.show(MainActivity.this, "", "Synchronizing", true);
        requestQueue = Volley.newRequestQueue(getApplicationContext());
        device = ScanActivity.globalDevice;

        Band band = BLEAPIManager.Companion.getInstance().getBand();
        if (band != null) {
            band.setSyncCallback(dsbleSyncCallback);
            band.setNotify(new Function2<DSBLEAutoType, Object, Unit>() {
                @Override
                public Unit invoke(DSBLEAutoType dsbleAutoType, Object o) {
                    Log.i(TAG, "DSBLEAutoType: " + dsbleAutoType + ". Object: " + o.toString());
                    if (dsbleAutoType == DSBLEAutoType.checkHR && isNumeric(o.toString())) {
                        heartRate = o.toString();
                        Calendar cal = Calendar.getInstance();
                        sendHeartRatePackage(Integer.parseInt(heartRate), cal.getTimeInMillis());
                        mHandler.sendEmptyMessage(2);
                    }
                    return null;
                }
            });

            band.makeFunc(DSBLEBandFuncType.version, null, new Function2<Object, Boolean, Unit>() {
                @Override
                public Unit invoke(Object o, Boolean aBoolean) {
                    Log.i(TAG, "version: " + o + ". Boolean: " + aBoolean);
                    FirmwareVersion = Integer.toString(((DSBLEVersion) o).getVersion());
                    Log.i(TAG, "FirmwareVersion: " + FirmwareVersion);
                    return null;
                }
            });

            band.makeFunc(DSBLEBandFuncType.sn, null, new Function2<Object, Boolean, Unit>() {
                @Override
                public Unit invoke(Object o, Boolean aBoolean) {
                    Log.i(TAG, "sn: " + o + ". Boolean: " + aBoolean);
                    deviceSN = o.toString();
                    return null;
                }
            });

            band.makeFunc(DSBLEBandFuncType.battery, null, new Function2<Object, Boolean, Unit>() {
                @Override
                public Unit invoke(Object o, Boolean aBoolean) {
                    Log.i(TAG, "battery: " + o + ". Boolean: " + aBoolean);
                    deviceBattery = o.toString();
                    return null;
                }
            });

            band.makeFunc(DSBLEBandFuncType.sync, null, new Function2<Object, Boolean, Unit>() {
                @Override
                public Unit invoke(Object o, Boolean aBoolean) {
                    Log.i(TAG, "sync: " + o + ". Boolean: " + aBoolean);
                    return null;
                }
            });
        } else {
            Log.e(TAG, "Band is null");
        }
    }

    private View.OnClickListener myOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_heart_rate_sync:
                    BLEAPIManager.Companion.getInstance().getBand().makeFunc(DSBLEBandFuncType.testHR, true, new Function2<Object, Boolean, Unit>() {
                        @Override
                        public Unit invoke(Object o, Boolean aBoolean) {
                            Log.i(TAG, "testHR: " + o + ". Boolean: " + aBoolean);
                            return null;
                        }
                    });
                    btnHeartRateSync.setVisibility(View.GONE);
                    btnHeartRateCancel.setVisibility(View.VISIBLE);
                    break;

                case R.id.btn_heart_rate_cancel:
                    BLEAPIManager.Companion.getInstance().getBand().makeFunc(DSBLEBandFuncType.testHR, false, new Function2<Object, Boolean, Unit>() {
                        @Override
                        public Unit invoke(Object o, Boolean aBoolean) {
                            Log.i(TAG, "testHR: " + o + ". Boolean: " + aBoolean);


                            return null;
                        }
                    });
                    btnHeartRateSync.setVisibility(View.VISIBLE);
                    btnHeartRateCancel.setVisibility(View.GONE);
                    break;
                default:
                    break;
            }
        }
    };

    //    Handler
    private final MyHandler mHandler = new MyHandler(this);

    private static class MyHandler extends Handler {

        private final WeakReference<MainActivity> mActivity;

        private MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            System.out.println(msg);
            if (mActivity.get() == null) {
                return;
            }
            MainActivity activity = mActivity.get();
            switch (msg.what) {
                case 0:
                    activity.tvFirmwareVersion.setText(activity.FirmwareVersion);
                    activity.tvID.setText(activity.deviceSN);
                    activity.tvPower.setText(activity.deviceBattery);
                    break;

                case 1:
                    activity.tvCalorie.setText(String.valueOf(Math.floor(activity.calorie * 10) / 10));
                    activity.tvTotalSteps.setText(String.valueOf(activity.todaySteps));
                    activity.tvDistance.setText(String.valueOf(Math.floor(activity.distance * 10) / 10));
                    activity.addDeviceRequest();
                    break;

                case 2:
                    activity.tvHeartRate.setText(activity.heartRate);
                    break;

                default:
                    break;
            }
        }
    }

    private Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "Sync Data: " + syncData.getSteps());
            mHandler.sendEmptyMessage(0);
            SharedPreferences sharedPreferences = getSharedPreferences("Personal_Info", MODE_PRIVATE);
//            int begin = sharedPreferences.getInt("begin", 7);
//            int end = sharedPreferences.getInt("end", 23);
            int height = sharedPreferences.getInt("height", 172);
            int weight = sharedPreferences.getInt("weight", 60);
//            int gender = sharedPreferences.getInt("gender", 1);
            int age = sharedPreferences.getInt("age", 22);

            BLEAPIManager.Companion.getInstance().getBand().makeFunc(DSBLEBandFuncType.userInfo, new DSBLEUserInfo(height, weight, DSBLEGender.male, age), new Function2<Object, Boolean, Unit>() {
                @Override
                public Unit invoke(Object o, Boolean aBoolean) {
                    Log.i(TAG, "sync: " + o + ". Boolean: " + aBoolean);
                    return null;
                }
            });

            String getCombine;
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH", Locale.TRADITIONAL_CHINESE);
            int length = syncData.getSteps().size();
            todaySteps = 0;
            ArrayList<Long> datesNumberArray = new ArrayList<>();

            Calendar cal = Calendar.getInstance();
            Calendar cal2 = Calendar.getInstance();
            getCombine = cal2.get(Calendar.YEAR) + "-"
                    + cal2.get(Calendar.MONTH) + "-"
                    + cal2.get(Calendar.DAY_OF_MONTH);
            try {
                cal2.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.TRADITIONAL_CHINESE).parse(getCombine));
            } catch (ParseException ex) {
                ex.printStackTrace();
            }

            for (int i = 0; i < length; i++) {
                cal.setTime(syncData.getSteps().get(i).getStartTime());
                getCombine = cal.get(Calendar.YEAR) + "-"
                        + cal.get(Calendar.MONTH) + "-"
                        + cal.get(Calendar.DAY_OF_MONTH) + " "
                        + cal.get(Calendar.HOUR);
                try {
                    cal.setTime(simpleDateFormat.parse(getCombine));
                } catch (ParseException ex) {
                    ex.printStackTrace();
                }

                if (!datesNumberArray.contains(cal.getTimeInMillis())) {
                    datesNumberArray.add(cal.getTimeInMillis());
                }

                if (cal.getTimeInMillis() >= cal2.getTimeInMillis()) {
                    todaySteps += syncData.getSteps().get(i).getStep();
                }
            }

            for (int i = 0; i < datesNumberArray.size(); i++) {
                int hourSteps = 0;
                for (int j = 0; j < length; j++) {
                    cal.setTime(syncData.getSteps().get(j).getStartTime());
                    getCombine = cal.get(Calendar.YEAR) + "-"
                            + cal.get(Calendar.MONTH) + "-"
                            + cal.get(Calendar.DAY_OF_MONTH) + " "
                            + cal.get(Calendar.HOUR);
                    try {
                        cal.setTime(simpleDateFormat.parse(getCombine));
                    } catch (ParseException ex) {
                        ex.printStackTrace();
                    }

                    if (datesNumberArray.get(i) == cal.getTimeInMillis()) {
                        hourSteps += syncData.getSteps().get(j).getStep();
                    }
                }

                sendStepPackage(hourSteps, datesNumberArray.get(i));
//                synchronized(ScanActivity.mutex) {
//                    try {
//                        sendPackage(todaySteps, datesNumberArray.get(i));
//                        ScanActivity.mutex.wait();
//                    } catch (InterruptedException ex) {
//                        ex.printStackTrace();
//                    } finally {
//                        ScanActivity.mutex.notifyAll();
//                    }
//                }
            }
            distance = (height / 3.0 / 100.0 * todaySteps / 1000);
            calorie = (1.56468 * weight * distance);
            mHandler.sendEmptyMessage(1);
            Log.i(TAG, "Dates list: " + datesNumberArray.size());
            dialog.dismiss();
        }
    };

    private void sendStepPackage (int steps, long timeStamp) {
        try {
            sendBuffer.clear();
            dataBuffer.clear();

            sendBuffer.put((byte) 0xAA);    // HEADER
            sendBuffer.put((byte) 0xAA);    // HEADER
            sendBuffer.put((byte) 0x02);    // COUNT

            dataBuffer.put((byte) 0x01);   // TID
            dataBuffer.put((byte) 0x01);   // TID
            dataBuffer.put((byte) 0x00);   // TYPE
            dataBuffer.put((byte) 0x55);   // TYPE = step
            dataBuffer.put((byte) 0x04);   // LENGTH
            dataBuffer.put(intToBytes(steps));     // DATA

            dataBuffer.put((byte) 0x02);   // TID
            dataBuffer.put((byte) 0x02);   // TID
            dataBuffer.put((byte) 0x00);   // TYPE
            dataBuffer.put((byte) 0x81);   // TYPE = time
            dataBuffer.put((byte) 0x08);   // LENGTH
            dataBuffer.put(longToBytes(timeStamp));     // DATA

            int XOR = 0;    // CHECKSUM
            for (int i = 0; i < dataBuffer.position(); i++) {
                XOR ^= dataBuffer.get(i);
            }
            dataBuffer.flip();
            sendBuffer.put(dataBuffer);

            sendBuffer.put((byte) 0xFF);    // FOOTER
            sendBuffer.put((byte) 0xFF);    // FOOTER
            sendBuffer.put((byte) XOR);     // CHECKSUM
            sendBuffer.flip();

            byte[] sendData = new byte[sendBuffer.limit()];
            sendBuffer.get(sendData);
            ScanActivity.mqttHelper.publishMessage(sendData, 1);

        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
    }

    private void sendHeartRatePackage (int heartRate, long timeStamp) {
        try {
            sendBuffer.clear();
            dataBuffer.clear();

            sendBuffer.put((byte) 0xAA);    // HEADER
            sendBuffer.put((byte) 0xAA);    // HEADER
            sendBuffer.put((byte) 0x02);    // COUNT

            dataBuffer.put((byte) 0x01);   // TID
            dataBuffer.put((byte) 0x01);   // TID
            dataBuffer.put((byte) 0x00);   // TYPE
            dataBuffer.put((byte) 0x52);   // TYPE = step
            dataBuffer.put((byte) 0x04);   // LENGTH
            dataBuffer.put(intToBytes(heartRate));     // DATA

            dataBuffer.put((byte) 0x02);   // TID
            dataBuffer.put((byte) 0x02);   // TID
            dataBuffer.put((byte) 0x00);   // TYPE
            dataBuffer.put((byte) 0x81);   // TYPE = time
            dataBuffer.put((byte) 0x08);   // LENGTH
            dataBuffer.put(longToBytes(timeStamp));     // DATA

            int XOR = 0;    // CHECKSUM
            for (int i = 0; i < dataBuffer.position(); i++) {
                XOR ^= dataBuffer.get(i);
            }
            dataBuffer.flip();
            sendBuffer.put(dataBuffer);

            sendBuffer.put((byte) 0xFF);    // FOOTER
            sendBuffer.put((byte) 0xFF);    // FOOTER
            sendBuffer.put((byte) XOR);     // CHECKSUM
            sendBuffer.flip();

            byte[] sendData = new byte[sendBuffer.limit()];
            sendBuffer.get(sendData);
            ScanActivity.mqttHelper.publishMessage(sendData, 1);

        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
    }

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(0, x);
        return buffer.array();
    }

    public static byte[] intToBytes(int x) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(0, x);
        return buffer.array();
    }

    public static boolean isNumeric(String str){
        for (int i = str.length();--i>=0;){
            if (!Character.isDigit(str.charAt(i))){
                return false;
            }
        }
        return true;
    }

    private void addDeviceRequest() {
        try {
            String postUrl = "https://iotsboard.iots.tw/v1/devices";

            JSONObject jsonBody = new JSONObject();

            jsonBody.put("did", deviceSN);
            jsonBody.put("token", System.currentTimeMillis());
            jsonBody.put("user_id", SignInActivity.user_id);

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, postUrl, jsonBody,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject s) {
                            Log.i(TAG, "Add device response = " + s.toString());    // Get json data from server.
                            getRequest();
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    Log.e(TAG, "Add device response error = " + volleyError.getMessage(), volleyError);
                    if (volleyError instanceof TimeoutError || volleyError instanceof NoConnectionError) {
                        Toast.makeText(MainActivity.this, "Error Network Timeout", Toast.LENGTH_SHORT).show();
                    } else if (volleyError instanceof AuthFailureError) {
                        Toast.makeText(MainActivity.this, "AuthFailure Error", Toast.LENGTH_SHORT).show();
                    } else if (volleyError instanceof ServerError) {
                        Toast.makeText(MainActivity.this, "Server Error", Toast.LENGTH_SHORT).show();
                    } else if (volleyError instanceof NetworkError) {
                        Toast.makeText(MainActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                    } else if (volleyError instanceof ParseError) {
                        Toast.makeText(MainActivity.this, "Parse Error", Toast.LENGTH_SHORT).show();
                    }
                    try {
                        byte[] htmlBodyBytes = volleyError.networkResponse.data;
                        Log.e("VolleyError body---->", new String(htmlBodyBytes));
                    } catch (NullPointerException ex) {
                        ex.printStackTrace();
                    }
                }
            });
            requestQueue.add(jsonObjectRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void getRequest() {         // Use volley library.
        String getUrl = "https://iotsboard.iots.tw/v1/devices/find/" + deviceSN;
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, getUrl, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject getData) {
                        try {
                            Log.i(TAG, "Get response = " + getData.toString());    // Get json data from server.
                            postRequest(getData.getJSONObject("devices").getInt("id"),
                                    getData.getJSONObject("devices").getString("token")
                            );
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                Log.e(TAG, "Get response error = " + volleyError.getMessage(), volleyError);
                if (volleyError instanceof TimeoutError || volleyError instanceof NoConnectionError) {
                    Toast.makeText(MainActivity.this, "Error Network Timeout", Toast.LENGTH_SHORT).show();
                } else if (volleyError instanceof AuthFailureError) {
                    Toast.makeText(MainActivity.this, "AuthFailure Error", Toast.LENGTH_SHORT).show();
                } else if (volleyError instanceof ServerError) {
                    Toast.makeText(MainActivity.this, "Server Error", Toast.LENGTH_SHORT).show();
                } else if (volleyError instanceof NetworkError) {
                    Toast.makeText(MainActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                } else if (volleyError instanceof ParseError) {
                    Toast.makeText(MainActivity.this, "Parse Error", Toast.LENGTH_SHORT).show();
                }
                try {
                    byte[] htmlBodyBytes = volleyError.networkResponse.data;
                    Log.e("VolleyError body---->", new String(htmlBodyBytes));
                } catch (NullPointerException ex) {
                    ex.printStackTrace();
                }
            }
        });
        requestQueue.add(jsonObjectRequest);
    }

    private void postRequest(int id, String token) {
//        try {
            postUrl = "https://iotsboard.iots.tw/v1/devices/" + id + "/" + token;
//
//            JSONObject jsonBody = new JSONObject();
//            JSONObject jsonData = new JSONObject();
//            JSONObject jsonDeviceDate = new JSONObject();
//
//            jsonData.put("power", deviceBattery);
//            jsonData.put("calorie", Math.floor(calorie * 10) / 10);
//            jsonData.put("steps", todaySteps);
//            jsonData.put("distance", Math.floor(distance * 10) / 10);
//            jsonData.put("activity_data", activityDataArray);
//
//            jsonDeviceDate.put("firmware", FirmwareVersion);
//
//            jsonBody.put("device_type", device.getDevice().getName());
//            jsonBody.put("device_data", jsonData.toString());
//            jsonBody.put("device_config", jsonDeviceDate.toString());
//            jsonBody.put("online", true);
//            jsonBody.put("update_user", "app");
//
//            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, postUrl, jsonBody,
//                    new Response.Listener<JSONObject>() {
//                        @Override
//                        public void onResponse(JSONObject s) {
//                            Log.i(TAG, "Post response = " + s.toString());    // Get json data from server.
//                            Toast.makeText(MainActivity.this, "同步完成", Toast.LENGTH_SHORT).show();
//                        }
//                    }, new Response.ErrorListener() {
//                @Override
//                public void onErrorResponse(VolleyError volleyError) {
//                    Log.e(TAG, "Post response error = " + volleyError.getMessage(), volleyError);
//                    if (volleyError instanceof TimeoutError || volleyError instanceof NoConnectionError) {
//                        Toast.makeText(MainActivity.this, "Error Network Timeout", Toast.LENGTH_SHORT).show();
//                    } else if (volleyError instanceof AuthFailureError) {
//                        Toast.makeText(MainActivity.this, "AuthFailure Error", Toast.LENGTH_SHORT).show();
//                    } else if (volleyError instanceof ServerError) {
//                        Toast.makeText(MainActivity.this, "Server Error", Toast.LENGTH_SHORT).show();
//                    } else if (volleyError instanceof NetworkError) {
//                        Toast.makeText(MainActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
//                    } else if (volleyError instanceof ParseError) {
//                        Toast.makeText(MainActivity.this, "Parse Error", Toast.LENGTH_SHORT).show();
//                    }
//                    try {
//                        byte[] htmlBodyBytes = volleyError.networkResponse.data;
//                        Log.e("VolleyError body---->", new String(htmlBodyBytes));
//                    } catch (NullPointerException ex) {
//                        ex.printStackTrace();
//                    }
//                }
//            });
//            requestQueue.add(jsonObjectRequest);
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
    }

    private DSBLESyncCallback dsbleSyncCallback = new DSBLESyncCallback() {

        @Override
        public void onWillStart() {
            Log.i(TAG, "onWillStart");
        }

        @Override
        public void onWillEnd() {
            Log.i(TAG, "onWillEnd");
        }

        @Override
        public void onSyncProgress(double v) {
            Log.i(TAG, "onSyncProgress");
        }

        @Override
        public void onSuccess(@Nullable DSBLESyncData dsbleSyncData) {
            Log.i(TAG, "onSuccess");
            Log.i(TAG, "DSBLESyncData: " + dsbleSyncData);

            if (dsbleSyncData != null) {
                syncData = dsbleSyncData;
                syncThread.start();
            }

//            Log.i(TAG, "DSBLESyncData: " + dsbleSyncData.getSteps().get(0).getStep());
//            Log.i(TAG, "DSBLESyncData: " + dsbleSyncData.component2().get(1));
        }

        @Override
        public void onFailed() {
            Log.i(TAG, "onFailed");
        }
    };

    private DSBLEBindCallback dsbleBindCallback = new DSBLEBindCallback() {

        @Override
        public void onSuccess() {
            Log.i(TAG, "Bind : onSuccess");
        }

        @Override
        public void onStart() {
            Log.i(TAG, "Bind : onStart");
        }

        @Override
        public void onFailed() {
            Log.i(TAG, "Bind : onFailed");
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        syncThread = new Thread(syncRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("online", false);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, postUrl, jsonBody,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject s) {
                            Log.i(TAG, "Post data = " + s.toString());    // Get json data from server.
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    Log.e(TAG, "Post error = " + volleyError.getMessage(), volleyError);
                }
            });
            requestQueue.add(jsonObjectRequest);

            mHandler.removeCallbacksAndMessages(null);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        BLEAPIManager.Companion.getInstance().stopScan();
        mHandler.removeCallbacksAndMessages(null);
        BLEAPIManager.Companion.getInstance().disconnect(device);
    }
}