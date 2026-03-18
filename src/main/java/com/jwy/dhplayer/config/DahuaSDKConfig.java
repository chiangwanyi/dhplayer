package com.jwy.dhplayer.config;

import com.netsdk.lib.NetSDKLib;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;


@Configuration
public class DahuaSDKConfig {

    private final NetSDKLib netsdk = NetSDKLib.NETSDK_INSTANCE;

    @PostConstruct
    public void init() {

        System.out.println("Init Dahua SDK");

        netsdk.CLIENT_Init(null, null);
        netsdk.CLIENT_SetAutoReconnect(null, null);
    }

    @PreDestroy
    public void destroy() {

        System.out.println("Cleanup Dahua SDK");

        netsdk.CLIENT_Cleanup();
    }
}