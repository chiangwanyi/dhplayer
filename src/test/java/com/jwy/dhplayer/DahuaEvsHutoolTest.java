package com.jwy.dhplayer;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
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
        printCameraTable(deviceList);
    }
    /**
     * 格式化输出摄像头列表（与截图格式一致）
     */
    private static void printCameraTable(JSONObject deviceList) {
        // 打印表头
        System.out.println("+------+--------+------------+------------------------+----------------+------------------+----------------+--------+--------+----------+------------------------+------------------------+");
        System.out.println("| 通道号 | 状态   | 录像状态   | 通道名                 | 地址           | 视图库通道编号     | 注册编号       | 端口   | 用户名 | 密码     | 制造商                 | 型号                   | 序列号                 |");
        System.out.println("+------+--------+------------+------------------------+----------------+------------------+----------------+--------+--------+----------+------------------------+------------------------+");

        // 遍历设备和通道
        JSONArray infos = deviceList.getJSONObject("params").getJSONArray("info");
        int channelNo = 1;
        for (int i = 0; i < infos.size(); i++) {
            JSONObject device = infos.getJSONObject(i);
            JSONArray channels = device.getJSONArray("channels");
            for (int j = 0; j < channels.size(); j++) {
                JSONObject channel = channels.getJSONObject(j);

                // 状态：在线为✅，离线为❌
                String status = device.getInt("online") == 1 ? "✅" : "❌";
                // 录像状态：这里默认用✅，可根据实际接口字段修改
                String recordStatus = "✅";
                // 通道名
                String channelName = channel.getStr("name", "");
                // 地址
                String address = device.getStr("Address", "");
                // 视图库通道编号
                String viewLibChannelId = "--";
                // 注册编号
                String regId = "--";
                // 端口
                String port = "37777";
                // 用户名
                String username = "admin";
                // 密码
                String password = "******";
                // 制造商
                String manufacturer = "私有";
                // 型号
                String model = channel.getStr("deviceType", "");
                // 序列号
                String sn = channel.getStr("sn", "");

                // 打印一行数据
                System.out.printf("| %-4d | %-6s | %-10s | %-20s | %-14s | %-16s | %-14s | %-6s | %-6s | %-8s | %-22s | %-22s | %-22s |%n",
                        channelNo++,
                        status,
                        recordStatus,
                        channelName,
                        address,
                        viewLibChannelId,
                        regId,
                        port,
                        username,
                        password,
                        manufacturer,
                        model,
                        sn);
            }
        }
        System.out.println("+------+--------+------------+------------------------+----------------+------------------+----------------+--------+--------+----------+------------------------+------------------------+");
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