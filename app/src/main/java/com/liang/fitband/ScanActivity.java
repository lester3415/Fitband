package com.liang.fitband;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.desay.dsbluetooth.manager.keep.BLEAPIManager;
import com.desay.dsbluetooth.manager.keep.DSBLECallback;
import com.desay.dsbluetooth.manager.keep.DSBLEDevice;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ScanActivity extends AppCompatActivity {
    private String TAG = "ScanActivity";
    private String MQTT_TAG = "MQTT_Debug";

    public static MqttHelper mqttHelper;
    private static boolean mqttFlag;
    public final static Object mutex = new Object();

    private BluetoothAdapter mBluetoothAdapter;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
    public static DSBLEDevice globalDevice;

    private Button btnScan;

//    private AlertDialog dialog;
private ProgressDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        setTitle("Scan");

        btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(myOnClickListener);

//        AlertDialog.Builder builder = new AlertDialog.Builder(ScanActivity.this, R.style.Theme_AppCompat_Dialog);
//        builder.setView(R.layout.custom_progress_bar);
//        dialog = builder.create();

        ListView listview = findViewById(R.id.listview);
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        listview.setAdapter(mLeDeviceListAdapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> listView, View v, int position, long id) {
                BLEAPIManager.Companion.getInstance().stopScan();
                Log.i(TAG, "Stop scan");

                mHandler.removeCallbacksAndMessages(null);
                globalDevice = mLeDeviceListAdapter.getDevice(position);  // Get position device.
                mLeDeviceListAdapter.clear();
                mLeDeviceListAdapter.notifyDataSetChanged();

                BLEAPIManager.Companion.getInstance().connect(globalDevice);
                dialog = ProgressDialog.show(ScanActivity.this, "", "Connecting", true);
//                dialog.show();
            }
        });

        mqttFlag = false;
        Check_Permission();
        BLEAPIManager.Companion.setContext(getApplicationContext());
        BLEAPIManager.Companion.getInstance();
        BLEAPIManager.Companion.setDebug(true);
        BLEAPIManager.Companion.getInstance().setCallback(dsbleCallback);
    }

    private View.OnClickListener myOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_scan:
                    mHandler.sendEmptyMessage(0);
                    BLEAPIManager.Companion.getInstance().scan(null, null);
                    break;

                default:
                    break;
            }
        }
    };

    private final MyHandler mHandler = new MyHandler(this);

    private static class MyHandler extends Handler {

        private final WeakReference<ScanActivity> mActivity;

        private MyHandler(ScanActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            System.out.println(msg);
            if (mActivity.get() == null) {
                return;
            }
            ScanActivity activity = mActivity.get();
            switch (msg.what) {
                case 0:
                    activity.mLeDeviceListAdapter.clear();
                    activity.mLeDeviceListAdapter.notifyDataSetChanged();
                    break;

                case 1:
                    activity.mLeDeviceListAdapter.notifyDataSetChanged();
                    break;

                default:
                    break;
            }
        }
    }

    public void startMqtt() {
        SharedPreferences shared = getSharedPreferences("MQTT_Config", MODE_PRIVATE);   // Save configuration
        String host = shared.getString("host", "34.217.228.207");
        String port = shared.getString("port", "1883");
        String username = shared.getString("username", "iot");
        String password = shared.getString("password", "iotTECH");

        String serverUri = "tcp://" + host + ":" + port;
        mqttHelper = new MqttHelper(getApplicationContext(), serverUri, username, password, getMqttActionListener());
        setMQTTCallback();
    }

    private void setMQTTCallback() {
        mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    Log.w(MQTT_TAG, "Reconnected");
                    // Because Clean Session is true, we need to re-subscribe
//                        subscribeToTopic();
                } else {
                    btnScan.setEnabled(true);
                    Log.w(MQTT_TAG, "Connected to " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable throwable) {
                mqttFlag = false;
                btnScan.setEnabled(false);
                Toast.makeText(ScanActivity.this, "Connection Lost", Toast.LENGTH_SHORT).show();
                Log.w(MQTT_TAG, "Connection Lost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                Log.w(MQTT_TAG, mqttMessage.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
//                Log.w(MQTT_TAG, "deliveryComplete");
//                synchronized (mutex) {
//                    mutex.notifyAll();
//                }
            }
        });
    }

    private IMqttActionListener getMqttActionListener() {
        IMqttActionListener listener = new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                mqttFlag = true;
                btnScan.setEnabled(true);
                Toast.makeText(ScanActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                Log.w(MQTT_TAG, "onSuccess. ");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                mqttFlag = false;
                btnScan.setEnabled(false);
                Toast.makeText(ScanActivity.this, exception.toString(), Toast.LENGTH_SHORT).show();
                Log.w(MQTT_TAG, "Failed to connect. " + exception.toString());
            }
        };
        return listener;
    }

    private void Check_Permission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Toast.makeText(this, "need_location_permission", Toast.LENGTH_SHORT).show();
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "error_bluetooth_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void signOutRequest(String email, String password) {
        try {
            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            String postUrl = "https://iotsboard.iots.tw/users/sign_out.json";

            JSONObject jsonData = new JSONObject();
            JSONObject jsonBody = new JSONObject();

            jsonBody.put("email", email);
            jsonBody.put("password", password);
            jsonData.put("user", jsonBody);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.DELETE, postUrl, jsonData,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject s) {
                            Intent intent = new Intent(ScanActivity.this, SignInActivity.class);
                            startActivity(intent);
                            finish();
                            Log.i(TAG, "Sign out response = " + s.toString());    // Get data from server.
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    Intent intent = new Intent(ScanActivity.this, SignInActivity.class);
                    startActivity(intent);
                    finish();
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

    private DSBLECallback dsbleCallback = new DSBLECallback() {

        @Override
        public void onBLEStateChange(boolean b) {
            Log.i(TAG, String.valueOf(b));
        }

        @Override
        public void onConnectDFUDevice(@NotNull DSBLEDevice dsbleDevice) {
            Log.i(TAG, "ConnectDFUDevice");
        }

        @Override
        public void onConnectDevice(@NotNull DSBLEDevice dsbleDevice) {
            Log.i(TAG, "ConnectDevice");
//            Function_Test();
            dialog.dismiss();
            Intent intent = new Intent(ScanActivity.this, MainActivity.class);
            startActivity(intent);
        }

        @Override
        public void onDisconnectDevice(@NotNull DSBLEDevice dsbleDevice) {
            Log.i(TAG, "DisconnectDevice");
            dialog.dismiss();
        }

        @Override
        public void onDiscoverDevice(@NotNull DSBLEDevice dsbleDevice) {
            Log.i(TAG, dsbleDevice.getDevice().getName());
            Log.i(TAG, dsbleDevice.getDevice().getAddress());
//            if (dsbleDevice.getDevice().getName().equals("DS-Z10F")) {
            mLeDeviceListAdapter.addDevice(dsbleDevice);
            mHandler.sendEmptyMessage(1);
//            }
        }
    };

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<DSBLEDevice> mLeDevices;
        private LayoutInflater mInflater;

        private LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflater = ScanActivity.this.getLayoutInflater();
        }

        private void addDevice(DSBLEDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        private DSBLEDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        private void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ScanActivity.ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflater.inflate(R.layout.device_list_style, null);
                viewHolder = new ScanActivity.ViewHolder();
                viewHolder.deviceName = view.findViewById(R.id.tv_device_name);
                viewHolder.deviceMac = view.findViewById(R.id.tv_device_mac);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ScanActivity.ViewHolder) view.getTag();
            }

            DSBLEDevice device = mLeDevices.get(i);
            final String deviceName = device.getDevice().getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText("unknown_device");

            viewHolder.deviceMac.setText(device.getAddress());

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceMac;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_setting) {
            Intent intent = new Intent(ScanActivity.this, MQTTSettingPage.class);
            startActivity(intent);
            return true;
        }
        if (id == R.id.menu_sign_out) {
            new AlertDialog.Builder(ScanActivity.this)
                    .setTitle("")
                    .setMessage("確定要登出?")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences sharedPreferences = getSharedPreferences("Login", MODE_PRIVATE);
                            String email = sharedPreferences.getString("email", "");
                            String password = sharedPreferences.getString("password", "");
                            signOutRequest(email, password);

                            sharedPreferences.edit()
                                    .putBoolean("autoSignIn", false)
                                    .apply();
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == AppCompatActivity.RESULT_CANCELED) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // if MQTT disconnected, reconnect.
        if (mqttFlag) {
            btnScan.setEnabled(true);
        } else {
            btnScan.setEnabled(false);
            startMqtt();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mqttHelper.disconnect();
        mHandler.removeCallbacksAndMessages(null);
        BLEAPIManager.Companion.getInstance().stopScan();
    }
}
