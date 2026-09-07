package com.ptmoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "amap")
public class AmapProperties {
    /**
     * 高德 Web 服务 Key，仅服务端使用。推荐通过环境变量 AMAP_WEB_KEY 注入。
     */
    private String key = "";

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isConfigured() {
        return key != null && !key.trim().isEmpty();
    }
}
