package com.jwy.dhplayer;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 大华EVS登录 + 获取摄像头列表（纯Hutool实现）
 */
public class DahuaEvsHutoolTest {

    // ====================== 配置项（自行修改）======================
    private static final String BASE_URL = "http://192.168.1.108";
    private static final String LOGIN_URL = BASE_URL + "/RPC2_Login";
    private static final String DEVICE_URL = BASE_URL + "/RPC2";

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "drx123456"; // 你的真实密码
    // =============================================================

    public static void main(String[] args) {
        testGetCameraList();
    }

    private static void testGetCameraList() {
        // 1. 第一次登录：获取挑战信息（session、random、realm）
        JSONObject firstResult = firstLogin();
        System.out.println("=== 第一次登录响应 ===");
        System.out.println(firstResult.toStringPretty());

        // 解析参数
        String session1 = firstResult.getStr("session");
        JSONObject params = firstResult.getJSONObject("params");
        String realm = params.getStr("realm");
        String random = params.getStr("random");

        // 2. 大华加密规则：MD5(用户名:realm:密码:random) 大写
        String encryptPwd = encryptPassword(USERNAME, realm, PASSWORD);
        System.out.println("加密后密码：" + encryptPwd);

        // 3. 第二次登录：完成认证
        JSONObject secondResult = secondLogin(session1, encryptPwd);
        System.out.println("=== 第二次登录响应 ===");
        System.out.println(secondResult.toStringPretty());

        // 登录结果校验
        boolean success = secondResult.getBool("result", false);
        if (!success) {
            System.out.println("登录失败！");
            return;
        }
        String session2 = secondResult.getStr("session");

        // 4. 获取摄像头列表
        JSONObject deviceList = getDeviceList(session2);
        System.out.println("==================== 摄像头列表 ====================");
        System.out.println(deviceList.toStringPretty());
    }

    /**
     * 第一次登录：获取challenge信息
     */
    private static JSONObject firstLogin() {
        JSONObject body = JSONUtil.createObj()
                .set("method", "global.login")
                .set("params", JSONUtil.createObj()
                        .set("userName", USERNAME)
                        .set("password", "")
                        .set("clientType", "Web3.0")
                )
                .set("id", 5)
                .set("session", null);

        try (HttpResponse response = HttpRequest.post(LOGIN_URL)
                .body(body.toString())
                .charset(CharsetUtil.UTF_8)
                .execute()) {

            return JSONUtil.parseObj(response.body());
        }
    }

    /**
     * 大华EVS 正确 WEB 登录加密算法（必须用这个！）
     * 规则：MD5(用户名:realm:密码) → 大写
     */
    private static String encryptPassword(String username, String realm, String password) {
        // 正确格式：username:realm:password
        String raw = StrUtil.format("{}:{}:{}", username, realm, password);
        return DigestUtil.md5Hex(raw).toUpperCase();
    }

    /**
     * 第二次登录：携带加密密码完成认证
     */
    private static JSONObject secondLogin(String session, String encryptPwd) {
        JSONObject body = JSONUtil.createObj()
                .set("method", "global.login")
                .set("params", JSONUtil.createObj()
                        .set("userName", USERNAME)
                        .set("clientType", "Web3.0")
                        .set("authorityType", "Default")
                        .set("passwordType", "Default")
                        .set("password", encryptPwd)
                )
                .set("id", 6)
                .set("session", session);

        try (HttpResponse response = HttpRequest.post(LOGIN_URL)
                .body(body.toString())
                .charset(CharsetUtil.UTF_8)
                .execute()) {

            return JSONUtil.parseObj(response.body());
        }
    }

    /**
     * 获取设备信息（摄像头列表）
     */
    private static JSONObject getDeviceList(String session) {
        JSONObject body = JSONUtil.createObj()
                .set("method", "AsyncDeviceManager.getDeviceInfoEx")
                .set("params", JSONUtil.createObj()
                        .set("deviceIDs", new Object[0])
                )
                .set("id", 47)
                .set("session", session);

        try (HttpResponse response = HttpRequest.post(DEVICE_URL)
                .body(body.toString())
                .charset(CharsetUtil.UTF_8)
                .execute()) {

            return JSONUtil.parseObj(response.body());
        }
    }
}