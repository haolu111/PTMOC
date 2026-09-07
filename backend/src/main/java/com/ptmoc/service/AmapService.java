package com.ptmoc.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ptmoc.config.AmapProperties;
import com.ptmoc.dto.LocationSuggestion;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class AmapService {

    private static final String TIPS_URL = "https://restapi.amap.com/v3/assistant/inputtips";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final AmapProperties amapProperties;

    public AmapService(AmapProperties amapProperties) {
        this.amapProperties = amapProperties;
    }

    public boolean isConfigured() {
        return amapProperties.isConfigured();
    }

    public List<LocationSuggestion> searchTips(String keywords, String city) {
        if (!amapProperties.isConfigured()) {
            throw new IllegalStateException("高德 Key 未配置，请设置环境变量 AMAP_WEB_KEY");
        }
        if (keywords == null || keywords.trim().length() < 2) {
            return List.of();
        }

        try {
            StringBuilder url = new StringBuilder(TIPS_URL);
            url.append("?key=").append(URLEncoder.encode(amapProperties.getKey().trim(), StandardCharsets.UTF_8));
            url.append("&keywords=").append(URLEncoder.encode(keywords.trim(), StandardCharsets.UTF_8));
            if (city != null && !city.isBlank()) {
                url.append("&city=").append(URLEncoder.encode(city.trim(), StandardCharsets.UTF_8));
                url.append("&citylimit=true");
            }

            String body = httpGet(url.toString());
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            String status = root.has("status") ? root.get("status").getAsString() : "0";
            if (!"1".equals(status)) {
                String info = root.has("info") ? root.get("info").getAsString() : "UNKNOWN";
                throw new IllegalStateException("高德输入提示失败: " + info);
            }

            List<LocationSuggestion> result = new ArrayList<>();
            if (!root.has("tips") || !root.get("tips").isJsonArray()) {
                return result;
            }
            JsonArray tips = root.getAsJsonArray("tips");
            for (JsonElement el : tips) {
                if (!el.isJsonObject()) continue;
                JsonObject tip = el.getAsJsonObject();
                String location = asString(tip.get("location"));
                if (location == null || location.isBlank() || !location.contains(",")) {
                    continue; // 无坐标的候选跳过
                }
                String[] parts = location.split(",");
                if (parts.length < 2) continue;

                LocationSuggestion s = new LocationSuggestion();
                s.setId(asString(tip.get("id")));
                s.setName(asString(tip.get("name")));
                s.setDistrict(asString(tip.get("district")));
                s.setAddress(asString(tip.get("address")));
                s.setLng(Double.parseDouble(parts[0].trim()));
                s.setLat(Double.parseDouble(parts[1].trim()));
                if (s.getName() == null || s.getName().isBlank()) continue;
                result.add(s);
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用高德输入提示失败: " + e.getMessage(), e);
        }
    }

    private static String asString(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonArray()) return ""; // 高德偶发返回 []
        if (el.isJsonPrimitive()) return el.getAsString();
        return el.toString();
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            if (code >= 400) {
                throw new IllegalStateException("HTTP " + code + ": " + sb);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
