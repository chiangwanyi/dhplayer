package com.jwy.dhplayer.service;

import com.netsdk.lib.NetSDKLib;
import com.netsdk.lib.NetSDKLib.*;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.springframework.stereotype.Service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

@Service
public class DahuaDownloadService {

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

        String outputFile = startStr + "__" + endStr + ".mp4";

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
}