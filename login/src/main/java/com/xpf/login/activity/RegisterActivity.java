package com.xpf.login.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.xpf.login.api.Contants;
import com.xpf.login.R;
import com.xpf.login.bean.SMS;
import com.xpf.login.bean.SmsSuccess;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import okhttp3.Call;
import okhttp3.MediaType;

/**
 * 用户注册页面
 */
public class RegisterActivity extends AppCompatActivity implements View.OnClickListener {

    private EditText etPhone, etCheckCode;
    private Button btnSend, btnNext;
    private String phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etPhone = (EditText) findViewById(R.id.etPhone);
        etCheckCode = (EditText) findViewById(R.id.etCheckCode);
        btnSend = (Button) findViewById(R.id.btnSend);
        btnNext = (Button) findViewById(R.id.btnNext);

        btnSend.setOnClickListener(this);
        btnNext.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.btnSend:
                getCodeFromNet();
                break;
            case R.id.btnNext:
                if (etCheckCode.length() == 6) {
                    // TODO 联网去检查验证码是否正确,如果正确的话就跳转到设置密码界面
                    // toCheckCodeIsRight();
                    Intent intent = new Intent(RegisterActivity.this, SetPwdActivity.class);
                    intent.putExtra("phone", phone);
                    intent.putExtra("smscode", etCheckCode.getText().toString().trim());
                    startActivity(intent);
                } else {
                    Toast.makeText(RegisterActivity.this, "验证码位数不对", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void getCodeFromNet() {

        phone = etPhone.getText().toString().trim();
        String url = Contants.SMS;

        if (!TextUtils.isEmpty(phone) && (phone.length() == 11)) {

//            OkHttpUtils
//                    .post()
//                    .url(url)
//                    .addParams("phone", phone)
//                    .build()
//                    .execute(new MyCallback());

            OkHttpUtils
                    .postString()
                    .url(url)
                    .content(new Gson().toJson(new SMS(phone)))
                    .mediaType(MediaType.parse("application/json; charset=utf-8"))
                    .build()
                    .execute(new MyCallback());

        } else {
            Toast.makeText(RegisterActivity.this, "手机号为空或者不合法", Toast.LENGTH_SHORT).show();
        }
    }

    class MyCallback extends StringCallback {

        @Override
        public void onError(Call call, Exception e, int id) {
            Log.e("TAG", "短信联网失败===" + e.toString());
        }

        @Override
        public void onResponse(String response, int id) {
            Log.e("TAG", "短信联网成功===" + response.toString());
            parseJson(response);
        }
    }

    private void parseJson(String json) {
        SmsSuccess sms = new Gson().fromJson(json, SmsSuccess.class);
        if (sms.getCode() == 200) {
            Toast.makeText(RegisterActivity.this, "发送成功", Toast.LENGTH_SHORT).show();
        }
    }
}
