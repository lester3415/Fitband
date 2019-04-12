package com.liang.fitband;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MQTTSettingPage extends AppCompatActivity {

    private MqttHelper mqttHelper = ScanActivity.mqttHelper;

    private String host;
    private String port;
    private String username;
    private String password;

    EditText etHost;
    EditText etPort;
    EditText etUsername;
    EditText etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqttsetting_page);
        setTitle("MQTT setting");

        Button btnSetting = findViewById(R.id.btn_setting);
        Button btnPreviousPage = findViewById(R.id.btn_previous_page);

        btnSetting.setOnClickListener(myOnClickListener);
        btnPreviousPage.setOnClickListener(myOnClickListener);

        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);

        SharedPreferences shared = getSharedPreferences("MQTT_Config", MODE_PRIVATE);
        host = shared.getString("host", "34.217.228.207");
        port = shared.getString("port", "1883");
        username = shared.getString("username", "iot");
        password = shared.getString("password", "iotTECH");

        etHost.setText(host);
        etPort.setText(port);
        etUsername.setText(username);
        etPassword.setText(password);

    }

    private View.OnClickListener myOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_setting:
                    host = etHost.getText().toString();
                    port = etPort.getText().toString();
                    username = etUsername.getText().toString();
                    password = etPassword.getText().toString();

                    SharedPreferences shared = getSharedPreferences("MQTT_Config", MODE_PRIVATE);
                    shared.edit()
                            .putString("host", host)
                            .putString("port", port)
                            .putString("username", username)
                            .putString("password", password)
                            .apply();
                    mqttHelper.disconnect();
                    Toast.makeText(MQTTSettingPage.this, "設定成功", Toast.LENGTH_SHORT).show();
                    break;

                case R.id.btn_previous_page:
                    finish();
                    break;

                default:
                    break;
            }
        }
    };
}
