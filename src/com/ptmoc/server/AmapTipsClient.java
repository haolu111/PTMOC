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
    private static final String DRIVING_V5_URL = "https://restapi.amap.com/v5/direction/driving";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_POLYLINE_POINTS = 1500;
    /** 32 推荐 / 33 躲避拥堵 / 38 速度最快（展示标签用；默认只请求 32） */
    private static final int STRATEGY_RECOMMEND = 32;
    private static final int STRATEGY_AVOID = 33;
    private static final int STRATEGY_FASTEST = 38;
    /** 免费 Key 常见 QPS≈1~3，两次高德调用至少间隔这么久 */
    private static final long AMAP_MIN_INTERVAL_MS = 400L;
    private static final Object AMAP_LOCK = new Object();
    private static long lastAmapCallAtMs = 0L;


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

    /**
     * 规划最多 3 条驾车路线：推荐路线 / 躲避拥堵 / 速度最快。
     * 默认只打 1 次高德（alternative_route=3），避免免费 Key 触发 CUQPS 限流；
     * 若备选不足，间隔后再补 1 次不同策略。
     */
    public Map<String, Object> planDrivingRoutes(double originLng, double originLat,
                                                 double destLng, double destLat) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("高德 Key 未配置，请设置环境变量 AMAP_WEB_KEY");
        }

        List<Map<String, Object>> pool = new ArrayList<Map<String, Object>>();
        try {
            pool.addAll(fetchAllDrivingPaths(originLng, originLat, destLng, destLat, STRATEGY_RECOMMEND, 3));
        } catch (IllegalStateException e) {
            throw new IllegalStateException(friendlyAmapError(e.getMessage()), e);
        }

        // 仅当互异路线不足 2 条时，再补一次（带间隔，降低 QPS）
        if (dedupeRoutes(pool).size() < 2) {
            sleepQuietly(AMAP_MIN_INTERVAL_MS);
            try {
                pool.addAll(fetchAllDrivingPaths(originLng, originLat, destLng, destLat, STRATEGY_AVOID, 3));
            } catch (IllegalStateException e) {
                if (pool.isEmpty()) {
                    throw new IllegalStateException(friendlyAmapError(e.getMessage()), e);
                }
                // 已有主路线则忽略补充失败
            }
        }
        if (pool.isEmpty()) {
            throw new IllegalStateException("无可用路径");
        }

        List<Map<String, Object>> routes = labelThreeRoutes(pool);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("routes", routes);
        return response;
    }

    /** 从候选中选出三条并打上展示名（不再为每个名字单独请求高德） */
    private static List<Map<String, Object>> labelThreeRoutes(List<Map<String, Object>> candidates) {
        List<Map<String, Object>> unique = dedupeRoutes(candidates);
        Map<String, Object> recommend = unique.get(0);

        Map<String, Object> fastest = recommend;
        for (Map<String, Object> r : unique) {
            if (asLong(r.get("durationSeconds")) < asLong(fastest.get("durationSeconds"))) {
                fastest = r;
            }
        }
        if (sameRoute(fastest, recommend)) {
            for (Map<String, Object> r : unique) {
                if (!sameRoute(r, recommend)) {
                    if (sameRoute(fastest, recommend)
                            || asLong(r.get("durationSeconds")) < asLong(fastest.get("durationSeconds"))) {
                        fastest = r;
                    }
                }
            }
        }

        Map<String, Object> avoid = null;
        long bestDiff = -1;
        for (Map<String, Object> r : unique) {
            if (sameRoute(r, recommend) || sameRoute(r, fastest)) continue;
            long diff = routeDiffScore(r, recommend);
            if (diff > bestDiff) {
                bestDiff = diff;
                avoid = r;
            }
        }
        if (avoid == null) {
            for (Map<String, Object> r : unique) {
                if (!sameRoute(r, recommend) && !sameRoute(r, fastest)) {
                    avoid = r;
                    break;
                }
            }
        }
        if (avoid == null) {
            for (Map<String, Object> r : unique) {
                if (!sameRoute(r, recommend)) {
                    avoid = r;
                    break;
                }
            }
        }
        if (avoid == null) avoid = recommend;

        List<Map<String, Object>> routes = new ArrayList<Map<String, Object>>();
        routes.add(cloneNamed(recommend, "route-recommend", "推荐路线", STRATEGY_RECOMMEND));
        routes.add(cloneNamed(avoid, "route-avoid-congestion", "躲避拥堵", STRATEGY_AVOID));
        routes.add(cloneNamed(fastest, "route-fastest", "速度最快", STRATEGY_FASTEST));
        return routes;
    }

    private static long routeDiffScore(Map<String, Object> r, Map<String, Object> base) {
        if (base == null) return 0;
        return Math.abs(asLong(r.get("distanceMeters")) - asLong(base.get("distanceMeters")))
                + Math.abs(asLong(r.get("durationSeconds")) - asLong(base.get("durationSeconds"))) * 10
                + Math.abs(asLong(r.get("trafficLights")) - asLong(base.get("trafficLights"))) * 100;
    }

    private static String friendlyAmapError(String raw) {
        if (raw == null) return "路径规划失败";
        String u = raw.toUpperCase();
        if (u.contains("CUQPS") || u.contains("CQPS") || u.contains("EXCEEDED_THE_LIMIT") || u.contains("DAILY_QUERY")) {
            return "地图服务请求过于频繁，请等待几秒后再点「规划路线」（免费 Key 有并发/次数限制）";
        }
        if (u.contains("INVALID_USER_KEY") || u.contains("USERKEY_PLAT_NOMATCH")) {
            return "高德 Key 无效或平台不匹配，请检查 AMAP_WEB_KEY 配置";
        }
        return raw;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Map<String, Object>> fetchAllDrivingPaths(
            double originLng, double originLat, double destLng, double destLat,
            int strategy, int alternativeRoute) throws Exception {
        StringBuilder url = new StringBuilder(DRIVING_V5_URL);
        url.append("?key=").append(URLEncoder.encode(apiKey, "UTF-8"));
        url.append("&origin=").append(originLng).append(",").append(originLat);
        url.append("&destination=").append(destLng).append(",").append(destLat);
        url.append("&strategy=").append(strategy);
        // v5：不传则默认只返回 1 条；传 3 才给多备选
        url.append("&alternative_route=").append(alternativeRoute);
        url.append("&show_fields=").append(URLEncoder.encode("cost,polyline,tmcs", "UTF-8"));

        String body = httpGet(url.toString());
        JsonParser parser = new JsonParser();
        JsonObject root = parser.parse(body).getAsJsonObject();
        String status = root.has("status") ? root.get("status").getAsString() : "0";
        if (!"1".equals(status)) {
            String info = root.has("info") ? root.get("info").getAsString() : "UNKNOWN";
            throw new IllegalStateException(info);
        }
        if (!root.has("route") || !root.get("route").isJsonObject()) {
            return new ArrayList<Map<String, Object>>();
        }
        JsonObject routeObj = root.getAsJsonObject("route");
        if (!routeObj.has("paths") || !routeObj.get("paths").isJsonArray()
                || routeObj.getAsJsonArray("paths").size() == 0) {
            return new ArrayList<Map<String, Object>>();
        }

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        JsonArray paths = routeObj.getAsJsonArray("paths");
        for (int i = 0; i < paths.size(); i++) {
            JsonObject path = paths.get(i).getAsJsonObject();
            Map<String, Object> dto = parsePathObject(path, "candidate-" + strategy + "-" + i, "备选" + (i + 1), strategy);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> poly = (List<Map<String, Object>>) dto.get("polyline");
            if (poly != null && !poly.isEmpty()) list.add(dto);
        }
        return list;
    }

    private Map<String, Object> parsePathObject(JsonObject path, String routeId, String name, int strategy) {
        Map<String, Object> dto = new LinkedHashMap<String, Object>();
        dto.put("routeId", routeId);
        dto.put("name", name);
        dto.put("strategyCode", strategy);
        dto.put("distanceMeters", parseLong(path, "distance"));
        long duration = 0;
        int lights = 0;
        if (path.has("cost") && path.get("cost").isJsonObject()) {
            JsonObject cost = path.getAsJsonObject("cost");
            duration = parseLong(cost, "duration");
            lights = (int) parseLong(cost, "traffic_lights");
        } else {
            duration = parseLong(path, "duration");
        }
        dto.put("durationSeconds", duration);
        dto.put("trafficLights", lights);
        dto.put("trafficSummary", summarizeTraffic(path));

        List<Map<String, Object>> points = new ArrayList<Map<String, Object>>();
        if (path.has("polyline") && path.get("polyline").isJsonPrimitive()) {
            points.addAll(parsePolyline(path.get("polyline").getAsString()));
        } else if (path.has("steps") && path.get("steps").isJsonArray()) {
            for (JsonElement stepEl : path.getAsJsonArray("steps")) {
                if (!stepEl.isJsonObject()) continue;
                JsonObject step = stepEl.getAsJsonObject();
                if (step.has("polyline")) {
                    points.addAll(parsePolyline(asString(step.get("polyline"))));
                }
            }
        }
        dto.put("polyline", downsample(points, MAX_POLYLINE_POINTS));
        return dto;
    }

    private static List<Map<String, Object>> dedupeRoutes(List<Map<String, Object>> routes) {
        List<Map<String, Object>> unique = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> r : routes) {
            boolean dup = false;
            for (Map<String, Object> u : unique) {
                if (sameRoute(u, r)) { dup = true; break; }
            }
            if (!dup) unique.add(r);
        }
        return unique;
    }

    private static boolean sameRoute(Map<String, Object> a, Map<String, Object> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Math.abs(asLong(a.get("distanceMeters")) - asLong(b.get("distanceMeters"))) <= 80
                && Math.abs(asLong(a.get("durationSeconds")) - asLong(b.get("durationSeconds"))) <= 45
                && polylineFingerprint(a).equals(polylineFingerprint(b));
    }

    @SuppressWarnings("unchecked")
    private static String polylineFingerprint(Map<String, Object> r) {
        List<Map<String, Object>> pts = (List<Map<String, Object>>) r.get("polyline");
        if (pts == null || pts.isEmpty()) {
            return asLong(r.get("distanceMeters")) + "|" + asLong(r.get("durationSeconds"));
        }
        Map<String, Object> first = pts.get(0);
        Map<String, Object> mid = pts.get(pts.size() / 2);
        Map<String, Object> last = pts.get(pts.size() - 1);
        return String.format("%.4f,%.4f|%.4f,%.4f|%.4f,%.4f|%d",
                asDouble(first.get("lng")), asDouble(first.get("lat")),
                asDouble(mid.get("lng")), asDouble(mid.get("lat")),
                asDouble(last.get("lng")), asDouble(last.get("lat")),
                pts.size());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cloneNamed(Map<String, Object> src, String routeId, String name, int strategyCode) {
        Map<String, Object> dto = new LinkedHashMap<String, Object>();
        dto.put("routeId", routeId);
        dto.put("name", name);
        dto.put("strategyCode", strategyCode);
        dto.put("distanceMeters", src.get("distanceMeters"));
        dto.put("durationSeconds", src.get("durationSeconds"));
        dto.put("trafficLights", src.get("trafficLights"));
        dto.put("trafficSummary", src.get("trafficSummary"));
        List<Map<String, Object>> poly = (List<Map<String, Object>>) src.get("polyline");
        dto.put("polyline", poly == null ? new ArrayList<Map<String, Object>>() : new ArrayList<Map<String, Object>>(poly));
        return dto;
    }

    private static long asLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Math.round(Double.parseDouble(String.valueOf(v))); } catch (Exception e) { return 0; }
    }

    private static double asDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    private static String summarizeTraffic(JsonObject path) {
        int[] counts = new int[5];
        boolean any = false;
        if (path.has("steps") && path.get("steps").isJsonArray()) {
            for (JsonElement stepEl : path.getAsJsonArray("steps")) {
                if (!stepEl.isJsonObject()) continue;
                JsonObject step = stepEl.getAsJsonObject();
                if (!step.has("tmcs") || !step.get("tmcs").isJsonArray()) continue;
                for (JsonElement tmcEl : step.getAsJsonArray("tmcs")) {
                    if (!tmcEl.isJsonObject()) continue;
                    int status = (int) parseLong(tmcEl.getAsJsonObject(), "tmc_status");
                    if (status >= 0 && status < counts.length) {
                        counts[status]++;
                        any = true;
                    }
                }
            }
        }
        if (!any) return "未知";
        int dominant = 1;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > counts[dominant]) dominant = i;
        }
        if (dominant == 1) return "畅通";
        if (dominant == 2) return "缓行";
        if (dominant == 3) return "拥堵";
        if (dominant == 4) return "严重拥堵";
        return "未知";
    }

    private static List<Map<String, Object>> parsePolyline(String polyline) {
        List<Map<String, Object>> points = new ArrayList<Map<String, Object>>();
        if (polyline == null || polyline.isEmpty()) return points;
        String[] segs = polyline.split(";");
        for (int i = 0; i < segs.length; i++) {
            String[] xy = segs[i].split(",");
            if (xy.length < 2) continue;
            try {
                Map<String, Object> p = new LinkedHashMap<String, Object>();
                p.put("lng", Double.parseDouble(xy[0].trim()));
                p.put("lat", Double.parseDouble(xy[1].trim()));
                points.add(p);
            } catch (NumberFormatException ignored) {
            }
        }
        return points;
    }

    private static List<Map<String, Object>> downsample(List<Map<String, Object>> points, int maxPoints) {
        if (points.size() <= maxPoints) return points;
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        int n = points.size();
        double step = (double) (n - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.round(i * step);
            if (idx >= n) idx = n - 1;
            out.add(points.get(idx));
        }
        return out;
    }

    private static long parseLong(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) return 0;
        try {
            if (obj.get(field).isJsonPrimitive()) {
                String s = obj.get(field).getAsString();
                if (s == null || s.trim().isEmpty()) return 0;
                return Math.round(Double.parseDouble(s));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static String asString(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonArray()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        return "";
    }

    private static String httpGet(String urlStr) throws Exception {
        // 全局限流：tips + driving 共用，避免免费 Key 触发 CUQPS
        synchronized (AMAP_LOCK) {
            long now = System.currentTimeMillis();
            long wait = AMAP_MIN_INTERVAL_MS - (now - lastAmapCallAtMs);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastAmapCallAtMs = System.currentTimeMillis();
        }

        try {
            return httpGetJava(urlStr);
        } catch (javax.net.ssl.SSLException sslEx) {
            // 本机常见：JDK8 直连高德 CDN 时 TLS 握手被对端掐断；curl 通常正常
            System.err.println("[Amap] Java HTTPS failed (" + sslEx.getMessage() + "), fallback to curl");
            return httpGetCurl(urlStr);
        } catch (java.net.SocketException se) {
            System.err.println("[Amap] Java socket failed (" + se.getMessage() + "), fallback to curl");
            return httpGetCurl(urlStr);
        }
    }

    private static String httpGetJava(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "PTMOC-Demo/1.0");
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

    /** macOS/本地代理环境下，用系统 curl 绕过 Java TLS 指纹被高德 CDN 拒绝的问题 */
    private static String httpGetCurl(String urlStr) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "curl", "--noproxy", "*", "-sS", "-f",
                "--connect-timeout", "5",
                "--max-time", "12",
                "-H", "Accept: application/json",
                "-H", "User-Agent: PTMOC-Demo/1.0",
                urlStr
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        } finally {
            reader.close();
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException(
                    "地图服务 HTTPS 握手失败（Java SSL + curl 均不可用）。"
                            + "请检查网络/代理；若开了 Clash 等代理，可尝试关闭系统 HTTPS 代理或换网络。详情: "
                            + sb.toString());
        }
        return sb.toString();
    }
}
