package com.xpf.login.api;

/**
 * Created by xpf on 2017/2/24:)
 * Function:联网请求常量类
 */

public class Contants {

    /**
     * HOST
     */
    public static final String HOST = "101.201.68.248:3000";

    /**
     * 请求短信验证码：
     * 接口：http://host/api/smsverify
     * 访问方式：post方式
     * 参数说明：
     * phone:用户手机号码
     */
    public static final String SMS = "http://101.201.68.248:3000/api/smsverify";

    /**
     * 注册接口：http://host/api/register
     * 访问方式：post方式
     * 参数说明：
     * cv：客户端版本号，目前暂定版本号为0.1
     * phone:用户手机号码
     * smscode：短信验证码
     * imei：imei号，获取不到就给手机系统名
     * imsi：imsi号，获取不到就给手机系统版本号
     * password：用户输入的base64后的密码
     */
    public static final String REGISTER = "http://101.201.68.248:3000/api/register";

    /**
     * 登陆接口：http://host /api/login
     * 访问方式：post方式
     * 参数说明：
     * phone:用户手机号码
     * password：用户输入的base64后的密码
     */
    public static final String LOGIN = "http://101.201.68.248:3000/api/login";

    /**
     * 修改密码接口：http://host /api/changepwd?uid=x&token=y
     * 访问方式：post方式
     * 参数说明：
     * oldpwd:旧密码
     * newpwd:新密码
     */
    public static final String CHANGEPWD = "http://101.201.68.248:3000/api/changepwd?uid=x&token=y";
}
