package com.liang.fitband;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

public class SignUpActivity extends AppCompatActivity {

    private String TAG = "SignUpActivity";

    private EditText etSignPpEmail;
    private EditText etSignUnPassword;
    private EditText etPasswordConfirmation;

    private String strEmail;
    private String strPassword;
    private String strPasswordConfirmation;

    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        setTitle("Sign up");

        etSignPpEmail = findViewById(R.id.et_sign_up_email);
        etSignUnPassword = findViewById(R.id.et_sign_up_password);
        etPasswordConfirmation = findViewById(R.id.et_sign_up_password_confirmation);

        Button btnSignUpCancel = findViewById(R.id.btn_sign_up_cancel);
        Button btnSignUp = findViewById(R.id.btn_sign_up);

        btnSignUpCancel.setOnClickListener(myOnClickListener);
        btnSignUp.setOnClickListener(myOnClickListener);

        requestQueue = Volley.newRequestQueue(getApplicationContext());
    }

    private View.OnClickListener myOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_sign_up:
                    etSignPpEmail.setError(null);
                    etSignUnPassword.setError(null);
                    etPasswordConfirmation.setError(null);

                    // Store values at the time of the login attempt.
                     strEmail = etSignPpEmail.getText().toString();
                    strPassword = etSignUnPassword.getText().toString();
                    strPasswordConfirmation = etPasswordConfirmation.getText().toString();

                    boolean cancel = false;
                    View focusView = null;

                    // Check for a valid password confirmation, if the user entered one.
                    if (TextUtils.isEmpty(strPasswordConfirmation)) {
                        etPasswordConfirmation.setError(getString(R.string.error_field_required));
                        focusView = etPasswordConfirmation;
                        cancel = true;
                    } else if (!isPasswordConfirmationValid(strPassword, strPasswordConfirmation)) {
                        etPasswordConfirmation.setError(getString(R.string.error_Inconsistent_password));
                        focusView = etPasswordConfirmation;
                        cancel = true;
                    }

                    // Check for a valid password, if the user entered one.
                    if (TextUtils.isEmpty(strPassword)) {
                        etSignUnPassword.setError(getString(R.string.error_field_required));
                        focusView = etSignUnPassword;
                        cancel = true;
                    } else if (!isPasswordValid(strPassword)) {
                        etSignUnPassword.setError(getString(R.string.error_invalid_password));
                        focusView = etSignUnPassword;
                        cancel = true;
                    }

                    // Check for a valid email address.
                    if (TextUtils.isEmpty(strEmail)) {
                        etSignPpEmail.setError(getString(R.string.error_field_required));
                        focusView = etSignPpEmail;
                        cancel = true;
                    } else if (!isEmailValid(strEmail)) {
                        etSignPpEmail.setError(getString(R.string.error_invalid_email));
                        focusView = etSignPpEmail;
                        cancel = true;
                    }

                    if (cancel) {
                        // There was an error; don't attempt login and focus the first
                        // form field with an error.
                        focusView.requestFocus();
                    } else {
                        signUpRequest(strEmail, strPassword, strPasswordConfirmation);
                    }
                    break;

                case R.id.btn_sign_up_cancel:
                    finish();
                    break;

                default:
                    break;

            }
        }
    };

    private boolean isEmailValid(String email) {
        return email.contains("@") && email.contains(".");
    }

    private boolean isPasswordValid(String password) {
        return password.length() >= 6;
    }

    private boolean isPasswordConfirmationValid(String password, String passwordConfirmation) {
        return password.equals(passwordConfirmation);
    }

    private final SignUpActivity.MyHandler mHandler = new SignUpActivity.MyHandler(this);

    private static class MyHandler extends Handler {

        private final WeakReference<SignUpActivity> mActivity;

        private MyHandler(SignUpActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            System.out.println(msg);
            if (mActivity.get() == null) {
                return;
            }
            SignUpActivity activity = mActivity.get();
            switch (msg.what) {
                case ErrorMessage.Error_Network_Timeout:
                    Toast.makeText(activity, "Error Network Timeout", Toast.LENGTH_SHORT).show();
                    break;

                case ErrorMessage.AuthFailure_Error:
                    Toast.makeText(activity, "AuthFailure Error", Toast.LENGTH_SHORT).show();
                    break;

                case ErrorMessage.Network_Error:
                    Toast.makeText(activity, "Server Error", Toast.LENGTH_SHORT).show();
                    break;

                case ErrorMessage.Parse_Error:
                    Toast.makeText(activity, "Network Error", Toast.LENGTH_SHORT).show();
                    break;

                case ErrorMessage.Server_Error:
//                    Toast.makeText(activity, "Parse Error", Toast.LENGTH_SHORT).show();
                    activity.etSignPpEmail.setError(activity.getString(R.string.error_invalid_account));
                    activity.etSignPpEmail.requestFocus();
                    break;

                default:
                    break;
            }
        }
    }

    private void signUpRequest(String email, String password, String password_confirmation) {
        try {
            final ProgressDialog dialog = ProgressDialog.show(SignUpActivity.this, "", "註冊中", true);
            String postUrl = "https://iotsboard.iots.tw/users.json";

            JSONObject jsonData = new JSONObject();
            JSONObject jsonBody = new JSONObject();

            jsonBody.put("email", email);
            jsonBody.put("password", password);
            jsonBody.put("password_confirmation", password_confirmation);
            jsonData.put("user", jsonBody);
            Log.i(TAG, "jsonData = " + jsonData);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, postUrl, jsonData,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject s) {
                            dialog.dismiss();
                            finish();
                            Log.i(TAG, "Sign up response = " + s.toString());    // Get json data from server.
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    dialog.dismiss();
                    Log.e(TAG, "Sign up response error = " + volleyError.getMessage(), volleyError);
                    if (volleyError instanceof TimeoutError || volleyError instanceof NoConnectionError) {
                        mHandler.sendEmptyMessage(ErrorMessage.Error_Network_Timeout);
                    } else if (volleyError instanceof AuthFailureError) {
                        mHandler.sendEmptyMessage(ErrorMessage.AuthFailure_Error);
                    } else if (volleyError instanceof ServerError) {
                        mHandler.sendEmptyMessage(ErrorMessage.Server_Error);
                    } else if (volleyError instanceof NetworkError) {
                        mHandler.sendEmptyMessage(ErrorMessage.Network_Error);
                    } else if (volleyError instanceof ParseError) {
                        mHandler.sendEmptyMessage(ErrorMessage.Parse_Error);
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
}
