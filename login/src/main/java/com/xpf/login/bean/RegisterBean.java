package com.xpf.login.bean;

/**
 * Created by xpf on 2017/2/24:)
 * Function:注册信息的Bean类
 */

public class RegisterBean {
    private String cv;
    private String phone;
    private String smscode;
    private String password;
    private String imei;
    private String imsi;

    public RegisterBean(String cv, String phone,
                        String smscode, String password,
                        String imei, String imsi) {
        this.cv = cv;
        this.phone = phone;
        this.smscode = smscode;
        this.password = password;
        this.imei = imei;
        this.imsi = imsi;
    }
}
