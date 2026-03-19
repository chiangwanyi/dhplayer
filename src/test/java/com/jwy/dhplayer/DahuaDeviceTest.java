package com.jwy.dhplayer;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class DahuaDeviceTest {
    public static void main(String[] args) throws Exception {

        String url = "http://192.168.1.108/cgi-bin/configManager.cgi?action=getConfig&name=RemoteDevice";

        // 认证
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope("192.168.1.108", 80),
                new UsernamePasswordCredentials("admin", "drx123456".toCharArray())
        );

        CloseableHttpClient client = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        HttpGet request = new HttpGet(url);

        // 模拟浏览器（关键！！）
        request.setHeader("User-Agent", "Mozilla/5.0");
        request.setHeader("Accept", "*/*");
        request.setHeader("Accept-Encoding", "gzip, deflate");

        CloseableHttpResponse response = client.execute(request);

        String result = EntityUtils.toString(response.getEntity(), "UTF-8");

        System.out.println("=== 原始返回 ===");
        System.out.println(result);

        // 解析
        Map<String, Camera> map = parse(result);

        System.out.println("=== 摄像头列表 ===");
        map.values().forEach(System.out::println);
    }

    static class Camera {
        String id;
        String ip;
        String name;
        String type;
        String serialNo;
        String vendor;
        String version;

        public String toString() {
            return id + " | " + ip + " | " + name + " | " + type;
        }
    }

    private static Map<String, Camera> parse(String text) {
        Map<String, Camera> map = new LinkedHashMap<>();

        for (String line : text.split("\n")) {
            line = line.trim();
            if (!line.startsWith("table.RemoteDevice")) continue;

            String[] kv = line.split("=", 2);
            if (kv.length != 2) continue;

            String key = kv[0];
            String value = kv[1];

            String[] parts = key.split("\\.");
            if (parts.length < 4) continue;

            // ✅ 正确位置
            String deviceKey = parts[2]; // System_Manager_00000001
            String field = parts[3];     // Address / DeviceType

            String deviceId = deviceKey.substring(deviceKey.lastIndexOf("_") + 1);

            Camera cam = map.computeIfAbsent(deviceId, k -> {
                Camera c = new Camera();
                c.id = k;
                return c;
            });

            switch (field) {
                case "Address":
                    cam.ip = value;
                    break;
                case "Name":
                    cam.name = value;
                    break;
                case "DeviceType":
                    cam.type = value;
                    break;
                case "SerialNo":
                    cam.serialNo = value;
                    break;
                case "Vendor":
                    cam.vendor = value;
                    break;
                case "Version":
                    cam.version = value;
                    break;
            }
        }

        return map;
    }
}