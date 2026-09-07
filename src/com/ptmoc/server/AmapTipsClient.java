package com.ptmoc.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 高德输入提示代理（JDK8）。Key 仅从环境变量 AMAP_WEB_KEY 读取。
 */
public class AmapTipsClient {

    private static final String TIPS_URL = "https://restapi.amap.com/v3/assistant/inputtips";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final String apiKey;

    public AmapTipsClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public static AmapTipsClient fromEnv() {
        String key = System.getenv("AMAP_WEB_KEY");
        if (key == null || key.trim().isEmpty()) {
            key = System.getProperty("amap.web.key", "");
        }
        return new AmapTipsClient(key);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public List<Map<String, Object>> searchTips(String keywords, String city) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("高德 Key 未配置，请设置环境变量 AMAP_WEB_KEY");
        }
        if (keywords == null || keywords.trim().length() < 2) {
            return new ArrayList<Map<String, Object>>();
        }

        StringBuilder url = new StringBuilder(TIPS_URL);
        url.append("?key=").append(URLEncoder.encode(apiKey, "UTF-8"));
        url.append("&keywords=").append(URLEncoder.encode(keywords.trim(), "UTF-8"));
        if (city != null && !city.trim().isEmpty()) {
            url.append("&city=").append(URLEncoder.encode(city.trim(), "UTF-8"));
            url.append("&citylimit=true");
        }

        String body = httpGet(url.toString());
        JsonParser parser = new JsonParser();
        JsonObject root = parser.parse(body).getAsJsonObject();
        String status = root.has("status") ? root.get("status").getAsString() : "0";
        if (!"1".equals(status)) {
            String info = root.has("info") ? root.get("info").getAsString() : "UNKNOWN";
            throw new IllegalStateException("高德输入提示失败: " + info);
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (!root.has("tips") || !root.get("tips").isJsonArray()) {
            return result;
        }
        JsonArray tips = root.getAsJsonArray("tips");
        for (JsonElement el : tips) {
            if (!el.isJsonObject()) continue;
            JsonObject tip = el.getAsJsonObject();
            String location = asString(tip.get("location"));
            if (location == null || location.isEmpty() || location.indexOf(',') < 0) continue;
            String[] parts = location.split(",");
            if (parts.length < 2) continue;

            String name = asString(tip.get("name"));
            if (name == null || name.isEmpty()) continue;

            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", asString(tip.get("id")));
            item.put("name", name);
            item.put("district", asString(tip.get("district")));
            item.put("address", asString(tip.get("address")));
            item.put("lng", Double.parseDouble(parts[0].trim()));
            item.put("lat", Double.parseDouble(parts[1].trim()));
            result.add(item);
        }
        return result;
    }

    private static String asString(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonArray()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        return "";
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            if (code >= 400) {
                throw new IllegalStateException("HTTP " + code + ": " + sb);
            }
            return sb.toString();
        } finally {
            reader.close();
            conn.disconnect();
        }
    }
}
