package com.liang.fitband;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class MqttHelper {
    private MqttAndroidClient mqttAndroidClient;

    private final String clientId = "ExampleAndroidClient" + System.currentTimeMillis();
    private final String subscriptionTopic = "exampleAndroidTopic";
//    private final String publishTopic = "/device/NiJiaTEST/token/testtoken/protocol";

    private IMqttActionListener listener;

    private String username;
    private String password;

    public MqttHelper(Context context, String serverUri, String username, String password, IMqttActionListener listener) {
        this.username = username;
        this.password = password;
        this.listener = listener;
        mqttAndroidClient = new MqttAndroidClient(context, serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

            }

            @Override
            public void connectionLost(Throwable throwable) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                Log.w("MQTT_Debug", mqttMessage.toString());
            }

            @Override
            public synchronized void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });
        connect();
    }

    public void setCallback(MqttCallbackExtended callback) {
        mqttAndroidClient.setCallback(callback);
    }

    private void connect() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setUserName(username);
        mqttConnectOptions.setPassword(password.toCharArray());
        mqttConnectOptions.setKeepAliveInterval(60);
        mqttConnectOptions.setMaxInflight(1000);

        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, listener);

        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (mqttAndroidClient != null) {
                IMqttToken disconnectToken = mqttAndroidClient.disconnect();
                disconnectToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.e("MQTT_Debug", "Disconnect successful");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken,
                                          Throwable exception) {
                        Log.e("MQTT_Debug", "Disconnect failed");
                    }
                });
            }
        } catch (MqttException ex) {
            Log.e("MQTT_Debug", "Exception occurred during disconnect = " + ex.getMessage());
        }
    }

    private void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.w("MQTT_Debug", "Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w("MQTT_Debug", "Subscribed fail!");
                }
            });

        } catch (MqttException ex) {
            System.err.println("Exceptionst subscribing");
            ex.printStackTrace();
        }
    }

    public void publishMessage(String publishTopic, byte[] value, int Qos) {
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(value);
            message.setQos(Qos);
            mqttAndroidClient.publish(publishTopic, message);
            Log.w("MQTT_Debug", "Message Published = " + Arrays.toString(value));
            if (!mqttAndroidClient.isConnected()) {
                Log.w("MQTT_Debug", mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing = " + e.getMessage());
            e.printStackTrace();
        }
    }

//    public void publishMessage(String key, String value, int Qos) {
//        try {
//            JSONObject object = new JSONObject();
//            object.put(key, value);
//
//            MqttMessage message = new MqttMessage();
//            message.setPayload(object.toString().getBytes());
//            message.setQos(Qos);
//            mqttAndroidClient.publish(publishTopic, message);
//            Log.w("MQTT_Debug", "Message Published = " + object.toString());
//            if (!mqttAndroidClient.isConnected()) {
//                Log.w("MQTT_Debug", mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
//            }
//        } catch (JSONException e) {
//            e.printStackTrace();
//        } catch (MqttException e) {
//            System.err.println("Error Publishing = " + e.getMessage());
//            e.printStackTrace();
//        }
//    }

//    public void publishMessage(JSONObject object, int Qos) {
//        try {
//            MqttMessage message = new MqttMessage();
//            message.setPayload(object.toString().getBytes());
//            message.setQos(Qos);
//            mqttAndroidClient.publish(publishTopic, message);
//            Log.w("MQTT_Debug", "Message Published = " + object.toString());
//            if (!mqttAndroidClient.isConnected()) {
//                Log.w("MQTT_Debug", mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
//            }
//        } catch (MqttException e) {
//            e.printStackTrace();
//        }
//    }
}
