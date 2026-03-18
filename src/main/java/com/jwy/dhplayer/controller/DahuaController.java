package com.jwy.dhplayer.controller;

import com.jwy.dhplayer.service.DahuaDownloadService;
import com.netsdk.lib.NetSDKLib;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@RestController
@RequestMapping("/camera")
public class DahuaController {

    @Resource
    DahuaDownloadService service;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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

        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startDate);

        NetSDKLib.NET_TIME start = new NetSDKLib.NET_TIME();
        start.dwYear = startCal.get(Calendar.YEAR);
        start.dwMonth = startCal.get(Calendar.MONTH) + 1;
        start.dwDay = startCal.get(Calendar.DAY_OF_MONTH);
        start.dwHour = startCal.get(Calendar.HOUR_OF_DAY);
        start.dwMinute = startCal.get(Calendar.MINUTE);
        start.dwSecond = startCal.get(Calendar.SECOND);

        Calendar endCal = (Calendar) startCal.clone();
        endCal.add(Calendar.SECOND, duration);

        NetSDKLib.NET_TIME end = new NetSDKLib.NET_TIME();
        end.dwYear = endCal.get(Calendar.YEAR);
        end.dwMonth = endCal.get(Calendar.MONTH) + 1;
        end.dwDay = endCal.get(Calendar.DAY_OF_MONTH);
        end.dwHour = endCal.get(Calendar.HOUR_OF_DAY);
        end.dwMinute = endCal.get(Calendar.MINUTE);
        end.dwSecond = endCal.get(Calendar.SECOND);

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

        // ===================== 核心改造：读入内存 → 立即删除文件 =====================
        byte[] videoBytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            // 一次性读入内存
            videoBytes = new byte[(int) file.length()];
            fis.read(videoBytes);
        } finally {
            // 无论如何都删除磁盘文件！！！
            file.delete();
        }

        // 后续全部使用内存字节流，不再操作磁盘文件
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

                // 安全校验
                if (endByte >= fileLength) endByte = fileLength - 1;
                long contentLength = endByte - startByte + 1;

                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + fileLength);
                response.setHeader("Content-Length", String.valueOf(contentLength));

                // 内存流定位
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
}