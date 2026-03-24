package com.jwy.dhplayer.controller;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.jwy.dhplayer.entity.DahuaCamChannel;
import com.jwy.dhplayer.service.DahuaEvsService;
import com.jwy.dhplayer.util.DahuaDateUtil;
import com.jwy.dhplayer.util.RtspUtil;
import com.netsdk.lib.NetSDKLib;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/camera")
public class DahuaController {

    @Resource
    DahuaEvsService service;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 抓取视频
     * @param evsIp 磁盘阵列 IP
     * @param username 磁盘阵列 用户名
     * @param password 磁盘阵列 密码
     * @param targetCamRtsp 通常是 rtsp://admin:drx123456@192.168.1.10/cam/realmonitor?channel=1&subtype=0 这种格式
     * @param left
     * @param right
     * @throws Exception
     */
    @GetMapping("/capVideo")
    public String capVideo(@RequestParam("evsIp") String evsIp,
                         @RequestParam("username") String username,
                         @RequestParam("password") String password,
                         @RequestParam("targetCamRtsp") String targetCamRtsp,
                         @RequestParam("time") String timeStr,
                         @RequestParam("left") Integer left,
                         @RequestParam("right") Integer right) throws Exception {
        // 去掉 evsIp 中可能存在的 http://
        evsIp = evsIp.replace("http://", "");
        // 使用正则表达式将 targetCamRtsp 中的 ip地址、channel 提取出来
        String ip = RtspUtil.getIpFromRtsp(targetCamRtsp);
        int remoteChannelNo = RtspUtil.getChannelFromRtsp(targetCamRtsp);
        List<DahuaCamChannel> channels = service.channels(evsIp, username, password);
        for (DahuaCamChannel channel : channels) {
            if (Objects.equals(channel.getIp(), ip) && Objects.equals(channel.getRemoteChannelNo(), remoteChannelNo)) {
                Date time = DATE_FORMAT.parse(timeStr);
                NetSDKLib.NET_TIME start = DahuaDateUtil.getNetTime(DateUtil.offsetSecond(time, -left));
                NetSDKLib.NET_TIME end = DahuaDateUtil.getNetTime(DateUtil.offsetSecond(time, right));
                String videoFilePath = service.download(
                        evsIp,
                        37777,
                        username,
                        password,
                        channel.getChannelNo(),
                        start,
                        end
                );
                return videoFilePath;
            }
        }
        return "未找到对应通道";
    }

    @GetMapping("/channels")
    public List<DahuaCamChannel> channels(@RequestParam("ip") String ip,
                           @RequestParam("username") String username,
                           @RequestParam("password") String password) throws Exception {
        List<DahuaCamChannel> channels = service.channels(ip, username, password);
        return channels;
    }

    @GetMapping("/playback")
    public void playback(@RequestParam("ip") String ip,
                         @RequestParam("username") String username,
                         @RequestParam("password") String password,
                         @RequestParam("start") String startTimeStr,
                         @RequestParam("duration") Integer duration,
                         @RequestParam("channelId") Integer channelId,
                         HttpServletRequest request,
                         HttpServletResponse response) throws Exception {

        Date startDate = DATE_FORMAT.parse(startTimeStr);
        NetSDKLib.NET_TIME start = DahuaDateUtil.getNetTime(startDate);

        DateTime endDate = DateUtil.offsetSecond(startDate, duration);
        NetSDKLib.NET_TIME end = DahuaDateUtil.getNetTime(endDate);

        String videoFilePath = service.download(
                ip,
                37777,
                username,
                password,
                channelId,
                start,
                end
        );

        File file = new File(videoFilePath);

        if (!file.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 读入内存后立即删除文件
        byte[] videoBytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            videoBytes = new byte[(int) file.length()];
            fis.read(videoBytes);
        } finally {
            file.delete();
        }

        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(videoBytes);
             OutputStream out = response.getOutputStream()) {

            long fileLength = videoBytes.length;
            String range = request.getHeader("Range");

            response.setHeader("Accept-Ranges", "bytes");
            response.setContentType("video/mp4");

            if (range == null) {
                response.setHeader("Content-Length", String.valueOf(fileLength));
                byte[] buffer = new byte[8192];
                int len;
                while ((len = byteIn.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            } else {
                long startByte = 0;
                long endByte = fileLength - 1;
                String[] ranges = range.replace("bytes=", "").split("-");

                startByte = Long.parseLong(ranges[0]);
                if (ranges.length > 1) {
                    endByte = Long.parseLong(ranges[1]);
                }

                if (endByte >= fileLength) endByte = fileLength - 1;
                long contentLength = endByte - startByte + 1;

                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + fileLength);
                response.setHeader("Content-Length", String.valueOf(contentLength));

                byteIn.skip(startByte);
                byte[] buffer = new byte[8192];
                long remaining = contentLength;
                int len;

                while (remaining > 0 && (len = byteIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    out.write(buffer, 0, len);
                    remaining -= len;
                }
            }
        }
    }

    // ====================== 新增：实时预览摄像头接口 ======================
    @GetMapping("/review")
    public void review(
            @RequestParam("ip") String ip,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("channelId") Integer channelId,
            HttpServletResponse response,
            HttpServletRequest request
    ) {
        Process ffmpeg = null;
        BufferedReader errorReader = null;

        try {
            // 1. 构建大华摄像头 RTSP 地址（大华标准格式）
            String rtspUrl = String.format(
                    "rtsp://%s:%s@%s:554/cam/realmonitor?channel=%d&subtype=0",
                    username, password, ip, channelId
            );

            // 2. FFmpeg 命令（超低延迟转 FLV 流）
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-rtsp_transport", "tcp",
                    "-i", rtspUrl,
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-tune", "zerolatency",
                    "-an", // 禁用音频（可选，打开就删此行）
                    "-f", "flv",
                    "-"
            );

            pb.redirectErrorStream(true);
            ffmpeg = pb.start();

            // 3. 设置响应头（完全兼容你给的 Python 版本）
            response.setContentType("video/x-flv");
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Cache-Control", "no-cache,no-store");
            response.setHeader("Connection", "close");

            // 4. 实时流输出
            try (InputStream in = ffmpeg.getInputStream();
                 OutputStream out = response.getOutputStream()) {

                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                }
            }

        } catch (Exception e) {
            System.err.println("实时预览流异常: " + e.getMessage());
        } finally {
            // 5. 前端断连 → 立即杀死 FFmpeg 进程
            if (ffmpeg != null) {
                try {
                    ffmpeg.destroy();
                    ffmpeg.waitFor();
                } catch (Exception ignored) {}
            }
        }
    }
}