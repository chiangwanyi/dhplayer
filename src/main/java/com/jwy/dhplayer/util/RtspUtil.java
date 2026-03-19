package com.jwy.dhplayer.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RTSP 地址解析工具类（静态方法版，直接调用）
 */
public class RtspUtil {

    // 正则：提取 @ 后面、/ 前面的 IP
    private static final String IP_REGEX = "(?<=@)(\\d+\\.\\d+\\.\\d+\\.\\d+)(?=/)";
    // 正则：提取 channel 参数
    private static final String CHANNEL_REGEX = "[?&]channel=(\\d+)";

    /**
     * 从 RTSP 地址中提取 IP 地址
     */
    public static String getIpFromRtsp(String rtspUrl) {
        if (rtspUrl == null || rtspUrl.isEmpty()) return null;
        Matcher matcher = Pattern.compile(IP_REGEX).matcher(rtspUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 从 RTSP 地址中提取 channel 号
     */
    public static int getChannelFromRtsp(String rtspUrl) {
        if (rtspUrl == null || rtspUrl.isEmpty()) return -1;
        Matcher matcher = Pattern.compile(CHANNEL_REGEX).matcher(rtspUrl);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    // ===================== 测试 =====================
    public static void main(String[] args) {
        String rtsp = "rtsp://admin:drx123456@192.168.1.10/cam/realmonitor?channel=1&subtype=0";

        String ip = RtspUtil.getIpFromRtsp(rtsp);
        int channel = RtspUtil.getChannelFromRtsp(rtsp);

        System.out.println("IP = " + ip);
        System.out.println("Channel = " + channel);
    }
}