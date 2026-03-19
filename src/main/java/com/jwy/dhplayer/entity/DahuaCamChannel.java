package com.jwy.dhplayer.entity;

import lombok.Data;

/**
 * 大华摄像头通道实体
 */
@Data
public class DahuaCamChannel {
    private Integer channelNo;        // 通道号
//    private String status;           // 状态：在线/离线
    private String channelName;      // 通道名称
    private String ip;               // 摄像头IP
    private String port;             // 端口
//    private String username;         // 用户名
//    private String password;         // 密码
    private Integer remoteChannelNo;  // 远程通道号
//    private String manufacturer;     // 厂商
//    private String deviceType;       // 型号
//    private String sn;               // 序列号
//    private Integer online;          // 在线状态 1=在线
}