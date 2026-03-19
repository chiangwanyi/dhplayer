package com.jwy.dhplayer.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jwy.dhplayer.entity.DahuaCamChannel;
import com.netsdk.lib.NetSDKLib;
import com.netsdk.lib.NetSDKLib.*;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.springframework.stereotype.Service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Service
public class DahuaEvsService {
    private final NetSDKLib netsdk = NetSDKLib.NETSDK_INSTANCE;

    private static String formatTime(NET_TIME t) {
        Calendar c = Calendar.getInstance();
        c.set(t.dwYear, t.dwMonth - 1, t.dwDay, t.dwHour, t.dwMinute, t.dwSecond);
        Date d = c.getTime();
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(d);
    }

    public String download(
            String ip,
            int port,
            String user,
            String password,
            int channelId,
            NET_TIME start,
            NET_TIME end) throws Exception {

        NET_DEVICEINFO_Ex deviceInfo = new NET_DEVICEINFO_Ex();
        IntByReference error = new IntByReference(0);

        LLong loginHandle = netsdk.CLIENT_LoginEx2(
                ip, port, user, password,
                0, null, deviceInfo, error);

        if (loginHandle.longValue() == 0) {
            throw new RuntimeException("Login failed");
        }

        String file = "test.dav";

        CountDownLatch latch = new CountDownLatch(1);

        fTimeDownLoadPosCallBack callback = new fTimeDownLoadPosCallBack() {
            @Override
            public void invoke(
                    LLong lLoginID,
                    int total,
                    int download,
                    int index,
                    NET_RECORDFILE_INFO.ByValue recordfileinfo,
                    Pointer dwUser) {

                if (download == -1) {
                    latch.countDown();
                }
            }
        };

        LLong downloadHandle = netsdk.CLIENT_DownloadByTimeEx2(
                loginHandle,
                channelId,
                0,
                start,
                end,
                file,
                callback,
                null,
                null,
                null,
                0,
                null
        );

        latch.await();

        // 生成输出文件名
        String startStr = formatTime(start);
        String endStr = formatTime(end);

        String outputFile = ip + "__" + channelId + "__" + startStr + "__" + endStr + ".mp4";

        System.out.println("Start ffmpeg remux...");

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", "test.dav",
                "-c:v", "copy",
                "-c:a", "aac",
                "-fflags", "+genpts",
                "-movflags", "+faststart",
                outputFile
        );

        pb.inheritIO();   // 显示ffmpeg输出

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("Remux success: " + outputFile);

            // 删除原始dav
            File dav = new File("test.dav");
            if (dav.exists()) {
                dav.delete();
                System.out.println("Deleted test.dav");
            }

        } else {
            System.out.println("ffmpeg failed");
        }

        netsdk.CLIENT_StopDownload(downloadHandle);
        netsdk.CLIENT_Logout(loginHandle);

        return outputFile;
    }

    /**
     * 【核心方法】获取大华EVS下所有摄像头通道
     * @param evsIp     EVS地址 例：192.168.1.108
     * @param user      登录账号
     * @param password  登录密码
     * @return 通道列表
     */
    public List<DahuaCamChannel> channels(String evsIp, String user, String password) {
        List<DahuaCamChannel> channelList = new ArrayList<>();
        String baseUrl = "http://" + evsIp;
        String loginUrl = baseUrl + "/RPC2_Login";
        String deviceUrl = baseUrl + "/RPC2";
        int channelNo = 1;

        try {
            // ====================== 1. 第一次登录，获取挑战信息 ======================
            JSONObject firstBody = JSONUtil.createObj()
                    .set("method", "global.login")
                    .set("params", JSONUtil.createObj().set("userName", user).set("password", "").set("clientType", "Web3.0"))
                    .set("id", 5).set("session", null);

            HttpResponse firstResp = HttpRequest.post(loginUrl).body(firstBody.toString()).execute();
            JSONObject firstResult = JSONUtil.parseObj(firstResp.body());
            String session1 = firstResult.getStr("session");
            String realm = firstResult.getJSONObject("params").getStr("realm");

            // ====================== 2. 加密密码 ======================
            String encryptPwd = DigestUtil.md5Hex(StrUtil.format("{}:{}:{}", user, realm, password)).toUpperCase();

            // ====================== 3. 第二次登录 ======================
            JSONObject secondBody = JSONUtil.createObj()
                    .set("method", "global.login")
                    .set("params", JSONUtil.createObj()
                            .set("userName", user).set("clientType", "Web3.0")
                            .set("authorityType", "Default").set("passwordType", "Default").set("password", encryptPwd))
                    .set("id", 6).set("session", session1);

            HttpResponse secondResp = HttpRequest.post(loginUrl).body(secondBody.toString()).execute();
            JSONObject secondResult = JSONUtil.parseObj(secondResp.body());
            if (!secondResult.getBool("result", false)) {
                throw new RuntimeException("大华EVS登录失败：账号或密码错误");
            }
            String session2 = secondResult.getStr("session");

            // ====================== 4. 获取设备列表 ======================
            JSONObject deviceBody = JSONUtil.createObj()
                    .set("method", "AsyncDeviceManager.getDeviceInfoEx")
                    .set("params", JSONUtil.createObj().set("deviceIDs", new Object[0]))
                    .set("id", 47).set("session", session2);

            HttpResponse deviceResp = HttpRequest.post(deviceUrl).body(deviceBody.toString()).execute();
            JSONObject deviceResult = JSONUtil.parseObj(deviceResp.body());

            // ====================== 5. 解析通道 ======================
            for (JSONObject device : deviceResult.getJSONObject("params").getJSONArray("info").toList(JSONObject.class)) {
                for (JSONObject ch : device.getJSONArray("channels").toList(JSONObject.class)) {
                    DahuaCamChannel channel = new DahuaCamChannel();
                    channel.setChannelNo(channelNo++);
                    channel.setOnline(device.getInt("online", 0));
                    channel.setStatus(device.getInt("online", 0) == 1 ? "在线" : "离线");
                    channel.setChannelName(ch.getStr("name", "未知通道"));
                    channel.setIp(device.getStr("Address", ""));
                    channel.setPort("37777");
                    channel.setUsername("admin");
                    channel.setPassword("******");
                    channel.setManufacturer("私有");
                    channel.setDeviceType(ch.getStr("deviceType", ""));
                    channel.setSn(ch.getStr("sn", ""));

                    channelList.add(channel);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("获取大华摄像头通道异常：" + e.getMessage(), e);
        }

        return channelList;
    }
}