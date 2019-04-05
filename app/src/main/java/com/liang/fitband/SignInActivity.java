package com.liang.fitband;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
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

public class SignInActivity extends AppCompatActivity {

    private String TAG = "SignInActivity";

    private AutoCompleteTextView etSignInEmail;
    private EditText etSignInPassword;

    private String strEmail;
    private String strPassword;
    private Boolean autoSignIn;
    public static int user_id;

    private CheckBox chkAutomaticSignIn;
    private SharedPreferences sharedPreferences;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        setTitle("Sign in");

//        populateAutoComplete();

        etSignInEmail = findViewById(R.id.et_sign_in_email);
        etSignInPassword = findViewById(R.id.et_sign_in_password);

        chkAutomaticSignIn = findViewById(R.id.chk_remember);

        Button btnSignIn = findViewById(R.id.btn_sign_in);
        Button btnSignUpPage = findViewById(R.id.btn_sign_up_page);

        btnSignIn.setOnClickListener(myOnClickListener);
        btnSignUpPage.setOnClickListener(myOnClickListener);

        sharedPreferences = getSharedPreferences("Login", MODE_PRIVATE);
        strEmail = sharedPreferences.getString("email", "");
        strPassword = sharedPreferences.getString("password", "");
        autoSignIn = sharedPreferences.getBoolean("autoSignIn", false);

        etSignInEmail.setText(strEmail);
        etSignInPassword.setText(strPassword);
        requestQueue = Volley.newRequestQueue(getApplicationContext());
        if (autoSignIn) {
            chkAutomaticSignIn.setChecked(true);
            signInRequest(strEmail, strPassword);
        }
    }

    private View.OnClickListener myOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_sign_in:
                    etSignInEmail.setError(null);
                    etSignInPassword.setError(null);

                    // Store values at the time of the login attempt.
                    strEmail = etSignInEmail.getText().toString();
                    strPassword = etSignInPassword.getText().toString();

                    boolean cancel = false;
                    View focusView = null;

                    // Check for a valid password, if the user entered one.
                    if (TextUtils.isEmpty(strPassword)) {
                        etSignInPassword.setError(getString(R.string.error_field_required));
                        focusView = etSignInPassword;
                        cancel = true;
                    } else if (!isPasswordValid(strPassword)) {
                        etSignInPassword.setError(getString(R.string.error_invalid_password));
                        focusView = etSignInPassword;
                        cancel = true;
                    }

                    // Check for a valid email address.
                    if (TextUtils.isEmpty(strEmail)) {
                        etSignInEmail.setError(getString(R.string.error_field_required));
                        focusView = etSignInEmail;
                        cancel = true;
                    } else if (!isEmailValid(strEmail)) {
                        etSignInEmail.setError(getString(R.string.error_invalid_email));
                        focusView = etSignInEmail;
                        cancel = true;
                    }

                    if (cancel) {
                        // There was an error; don't attempt login and focus the first
                        // form field with an error.
                        focusView.requestFocus();
                    } else {
                        if (chkAutomaticSignIn.isChecked()) {
                            sharedPreferences.edit()
                                    .putString("email", strEmail)
                                    .putString("password", strPassword)
                                    .putBoolean("autoSignIn", true)
                                    .apply();
                        } else {
                            sharedPreferences.edit()
                                    .putString("email", strEmail)
                                    .putString("password", strPassword)
                                    .putBoolean("autoSignIn", false)
                                    .apply();
                        }
                        signInRequest(strEmail, strPassword);
                    }
                    break;

                case R.id.btn_sign_up_page:
                    Intent intent = new Intent(SignInActivity.this, SignUpActivity.class);
                    startActivity(intent);
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

    private final SignInActivity.MyHandler mHandler = new SignInActivity.MyHandler(this);

    private static class MyHandler extends Handler {

        private final WeakReference<SignInActivity> mActivity;

        private MyHandler(SignInActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            System.out.println(msg);
            if (mActivity.get() == null) {
                return;
            }
            SignInActivity activity = mActivity.get();
            switch (msg.what) {
                case ErrorMessage.Error_Network_Timeout:
                    Toast.makeText(activity, "Error Network Timeout", Toast.LENGTH_SHORT).show();
                    break;

                case ErrorMessage.AuthFailure_Error:
                    activity.etSignInPassword.setError(activity.getString(R.string.error_invalid_account));
                    activity.etSignInPassword.requestFocus();
                    break;

                case ErrorMessage.Network_Error:
                    Toast.makeText(activity, "Server Error", Toast.LENGTH_SHORT).show();
                    break;

                case ErrorMessage.Parse_Error:
                    Toast.makeText(activity, "Network Error", Toast.LENGTH_SHORT).show();
                    break;

                case ErrorMessage.Server_Error:
                    Toast.makeText(activity, "Parse Error", Toast.LENGTH_SHORT).show();
                    break;

                default:
                    break;
            }
        }
    }

    private void signInRequest(String email, String password) {
        try {
            final ProgressDialog dialog = ProgressDialog.show(SignInActivity.this, "", "Signing in", true);
            String postUrl = "https://iotsboard.iots.tw/users/sign_in.json";

            JSONObject jsonData = new JSONObject();
            JSONObject jsonBody = new JSONObject();

            jsonBody.put("email", email);
            jsonBody.put("password", password);
            jsonData.put("user", jsonBody);

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, postUrl, jsonData,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject object) {
                            dialog.dismiss();
                            try {
                                Intent intent = new Intent(SignInActivity.this, ScanActivity.class);
                                startActivity(intent);
                                user_id = object.getInt("id");
                                finish();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            Log.i(TAG, "Sign in response = " + object.toString());    // Get json data from server.
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {

                    Log.e(TAG, "Sign in response error = " + volleyError.getMessage(), volleyError);
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
                    dialog.dismiss();
                }
            });
            requestQueue.add(jsonObjectRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
