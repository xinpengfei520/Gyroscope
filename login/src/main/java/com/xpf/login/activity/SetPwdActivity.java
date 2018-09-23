package com.xpf.login.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xpf.login.R;
import com.xpf.login.api.Contants;
import com.xpf.login.bean.RegisterBean;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import okhttp3.Call;
import okhttp3.MediaType;

/**
 * 设置密码界面
 */
public class SetPwdActivity extends AppCompatActivity {

    private EditText etPwd, etPwd2;
    private Button btnComplete;
    private String phoneNumber, smsCode;
    private TelephonyManager tm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_pwd);

        tm = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);

        Intent intent = getIntent();
        phoneNumber = intent.getStringExtra("phone");
        smsCode = intent.getStringExtra("smscode");

        etPwd = (EditText) findViewById(R.id.etPwd);
        etPwd2 = (EditText) findViewById(R.id.etPwd2);
        btnComplete = (Button) findViewById(R.id.btnComplete);

        btnComplete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendPwdtoServer();
            }
        });
    }

    private void sendPwdtoServer() {
        String pwd1 = etPwd.getText().toString().trim();
        String pwd2 = etPwd2.getText().toString().trim();
        if (pwd1.equals(pwd2)) {
            getDataFromNet();
        } else {
            Toast.makeText(SetPwdActivity.this, "两次输入密码不一样", Toast.LENGTH_SHORT).show();
            etPwd.setText("");
            etPwd2.setText("");
        }
    }

    private void getDataFromNet() {
        String pwd = etPwd.getText().toString().trim();
        String url = Contants.REGISTER;
        byte[] encode = Base64.encode(pwd.getBytes(), Base64.DEFAULT);
        String imei = tm.getDeviceId() == null ? tm.getDeviceId() : tm.getDeviceId();
        String imsi = tm.getSubscriberId() == null ? tm.getDeviceSoftwareVersion() : tm.getSubscriberId();

        RegisterBean registerBean = new RegisterBean("" + 0.1, phoneNumber,
                smsCode,  new String(encode), imei,imsi);
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        String temp = gson.toJson(registerBean);

        Log.d("sms",temp);
        OkHttpUtils
                .postString()
                .url(url)
                .content(temp)
                .mediaType(MediaType.parse("application/json; charset=utf-8"))
                .build()
                .execute(new MyStringCallback());
    }

    class MyStringCallback extends StringCallback {

        @Override
        public void onError(Call call, Exception e, int id) {
            Log.e("TAG", "注册联网失败===" + e.toString());
        }

        @Override
        public void onResponse(String response, int id) {
            Log.e("TAG", "注册联网成功===" + response.toString());
            parseJson(response);
        }
    }

    private void parseJson(String json) {

    }

}
