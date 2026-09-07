package com.ptmoc.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ptmoc.config.AmapProperties;
import com.ptmoc.dto.GeoPointDto;
import com.ptmoc.dto.LocationSuggestion;
import com.ptmoc.dto.RoutePlanDto;
import com.ptmoc.dto.RoutePlanRequest;
import com.ptmoc.dto.RoutePlanResponse;
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
    private static final String DRIVING_V5_URL = "https://restapi.amap.com/v5/direction/driving";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_POLYLINE_POINTS = 1500;

    /** Demo 展示三条：推荐路线 / 躲避拥堵 / 速度最快（名称不出现「高德」） */
    private static final int STRATEGY_RECOMMEND = 32;
    private static final int STRATEGY_AVOID = 33;
    private static final int STRATEGY_FASTEST = 38;
    private static final long AMAP_MIN_INTERVAL_MS = 400L;
    private static final Object AMAP_LOCK = new Object();
    private static long lastAmapCallAtMs = 0L;


    private final AmapProperties amapProperties;

    public AmapService(AmapProperties amapProperties) {
        this.amapProperties = amapProperties;
    }

    public boolean isConfigured() {
        return amapProperties.isConfigured();
    }

    public List<LocationSuggestion> searchTips(String keywords, String city) {
        if (!amapProperties.isConfigured()) {
            throw new IllegalStateException("高德 Key 未配置，请设置环境变量 AMAP_WEB_KEY 或 application-local.yml");
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
                    continue;
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

    public RoutePlanResponse planDrivingRoutes(RoutePlanRequest request) {
        if (!amapProperties.isConfigured()) {
            throw new IllegalStateException("高德 Key 未配置，请设置环境变量 AMAP_WEB_KEY 或 application-local.yml");
        }
        if (request == null || request.getOrigin() == null || request.getDestination() == null) {
            throw new IllegalStateException("origin 和 destination 不能为空");
        }
        GeoPointDto origin = request.getOrigin();
        GeoPointDto dest = request.getDestination();

        try {
            // 默认只打 1 次高德（alternative_route=3），避免免费 Key 触发 CUQPS
            List<RoutePlanDto> pool = new ArrayList<>();
            try {
                pool.addAll(fetchAllDrivingPaths(origin, dest, STRATEGY_RECOMMEND, 3));
            } catch (IllegalStateException e) {
                throw new IllegalStateException(friendlyAmapError(e.getMessage()), e);
            }
            if (dedupeRoutes(pool).size() < 2) {
                sleepQuietly(AMAP_MIN_INTERVAL_MS);
                try {
                    pool.addAll(fetchAllDrivingPaths(origin, dest, STRATEGY_AVOID, 3));
                } catch (IllegalStateException ignored) {
                    // 已有主路线则忽略补充失败
                }
            }
            if (pool.isEmpty()) {
                throw new IllegalStateException("无可用路径");
            }

            RoutePlanResponse response = new RoutePlanResponse();
            response.setRoutes(labelThreeRoutes(pool));
            return response;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("路径规划失败: " + e.getMessage(), e);
        }
    }

    private static List<RoutePlanDto> labelThreeRoutes(List<RoutePlanDto> candidates) {
        List<RoutePlanDto> unique = dedupeRoutes(candidates);
        RoutePlanDto recommend = unique.get(0);

        RoutePlanDto fastest = recommend;
        for (RoutePlanDto r : unique) {
            if (r.getDurationSeconds() < fastest.getDurationSeconds()) {
                fastest = r;
            }
        }
        if (sameRoute(fastest, recommend)) {
            for (RoutePlanDto r : unique) {
                if (!sameRoute(r, recommend)
                        && (sameRoute(fastest, recommend) || r.getDurationSeconds() < fastest.getDurationSeconds())) {
                    fastest = r;
                }
            }
        }

        RoutePlanDto avoid = null;
        long bestDiff = -1;
        for (RoutePlanDto r : unique) {
            if (sameRoute(r, recommend) || sameRoute(r, fastest)) continue;
            long diff = routeDiffScore(r, recommend);
            if (diff > bestDiff) {
                bestDiff = diff;
                avoid = r;
            }
        }
        if (avoid == null) {
            for (RoutePlanDto r : unique) {
                if (!sameRoute(r, recommend)) {
                    avoid = r;
                    break;
                }
            }
        }
        if (avoid == null) avoid = recommend;

        List<RoutePlanDto> routes = new ArrayList<>();
        routes.add(cloneNamed(recommend, "route-recommend", "推荐路线", STRATEGY_RECOMMEND));
        routes.add(cloneNamed(avoid, "route-avoid-congestion", "躲避拥堵", STRATEGY_AVOID));
        routes.add(cloneNamed(fastest, "route-fastest", "速度最快", STRATEGY_FASTEST));
        return routes;
    }

    private static long routeDiffScore(RoutePlanDto r, RoutePlanDto base) {
        if (base == null) return 0;
        return Math.abs(r.getDistanceMeters() - base.getDistanceMeters())
                + Math.abs(r.getDurationSeconds() - base.getDurationSeconds()) * 10
                + Math.abs(r.getTrafficLights() - base.getTrafficLights()) * 100L;
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

    private static List<RoutePlanDto> dedupeRoutes(List<RoutePlanDto> routes) {
        List<RoutePlanDto> unique = new ArrayList<>();
        for (RoutePlanDto r : routes) {
            boolean dup = false;
            for (RoutePlanDto u : unique) {
                if (sameRoute(u, r)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) unique.add(r);
        }
        return unique;
    }

    private static boolean sameRoute(RoutePlanDto a, RoutePlanDto b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Math.abs(a.getDistanceMeters() - b.getDistanceMeters()) <= 80
                && Math.abs(a.getDurationSeconds() - b.getDurationSeconds()) <= 45
                && polylineFingerprint(a).equals(polylineFingerprint(b));
    }

    private static String polylineFingerprint(RoutePlanDto r) {
        List<GeoPointDto> pts = r.getPolyline();
        if (pts == null || pts.isEmpty()) {
            return r.getDistanceMeters() + "|" + r.getDurationSeconds();
        }
        GeoPointDto first = pts.get(0);
        GeoPointDto mid = pts.get(pts.size() / 2);
        GeoPointDto last = pts.get(pts.size() - 1);
        return String.format("%.4f,%.4f|%.4f,%.4f|%.4f,%.4f|%d",
                first.getLng(), first.getLat(),
                mid.getLng(), mid.getLat(),
                last.getLng(), last.getLat(),
                pts.size());
    }

    private static RoutePlanDto cloneNamed(RoutePlanDto src, String routeId, String name, int strategyCode) {
        RoutePlanDto dto = new RoutePlanDto();
        dto.setRouteId(routeId);
        dto.setName(name);
        dto.setStrategyCode(strategyCode);
        dto.setDistanceMeters(src.getDistanceMeters());
        dto.setDurationSeconds(src.getDurationSeconds());
        dto.setTrafficLights(src.getTrafficLights());
        dto.setTrafficSummary(src.getTrafficSummary());
        dto.setPolyline(src.getPolyline() == null ? new ArrayList<>() : new ArrayList<>(src.getPolyline()));
        return dto;
    }

    private List<RoutePlanDto> fetchAllDrivingPaths(GeoPointDto origin, GeoPointDto dest, int strategy, int alternativeRoute) throws Exception {
        StringBuilder url = new StringBuilder(DRIVING_V5_URL);
        url.append("?key=").append(URLEncoder.encode(amapProperties.getKey().trim(), StandardCharsets.UTF_8));
        url.append("&origin=").append(origin.getLng()).append(",").append(origin.getLat());
        url.append("&destination=").append(dest.getLng()).append(",").append(dest.getLat());
        url.append("&strategy=").append(strategy);
        url.append("&alternative_route=").append(alternativeRoute);
        url.append("&show_fields=").append(URLEncoder.encode("cost,polyline,tmcs", StandardCharsets.UTF_8));

        String body = httpGet(url.toString());
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String status = root.has("status") ? root.get("status").getAsString() : "0";
        if (!"1".equals(status)) {
            String info = root.has("info") ? root.get("info").getAsString() : "UNKNOWN";
            throw new IllegalStateException(info);
        }
        if (!root.has("route") || !root.get("route").isJsonObject()) {
            return List.of();
        }
        JsonObject routeObj = root.getAsJsonObject("route");
        if (!routeObj.has("paths") || !routeObj.get("paths").isJsonArray() || routeObj.getAsJsonArray("paths").size() == 0) {
            return List.of();
        }

        List<RoutePlanDto> list = new ArrayList<>();
        JsonArray paths = routeObj.getAsJsonArray("paths");
        for (int i = 0; i < paths.size(); i++) {
            JsonObject path = paths.get(i).getAsJsonObject();
            RoutePlanDto dto = parsePathObject(path, "candidate-" + strategy + "-" + i, "备选" + (i + 1), strategy);
            if (dto != null && dto.getPolyline() != null && !dto.getPolyline().isEmpty()) {
                list.add(dto);
            }
        }
        return list;
    }

    private RoutePlanDto parsePathObject(JsonObject path, String routeId, String name, int strategy) {
        RoutePlanDto dto = new RoutePlanDto();
        dto.setRouteId(routeId);
        dto.setName(name);
        dto.setStrategyCode(strategy);
        dto.setDistanceMeters(parseLong(path, "distance"));
        dto.setDurationSeconds(0);
        dto.setTrafficLights(0);
        dto.setTrafficSummary("");

        if (path.has("cost") && path.get("cost").isJsonObject()) {
            JsonObject cost = path.getAsJsonObject("cost");
            dto.setDurationSeconds(parseLong(cost, "duration"));
            dto.setTrafficLights((int) parseLong(cost, "traffic_lights"));
        } else {
            dto.setDurationSeconds(parseLong(path, "duration"));
        }

        List<GeoPointDto> points = new ArrayList<>();
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
        dto.setPolyline(downsample(points, MAX_POLYLINE_POINTS));
        dto.setTrafficSummary(summarizeTraffic(path));
        return dto;
    }

    private static String summarizeTraffic(JsonObject path) {
        // 尽量从 tmcs 统计；没有则空字符串
        int[] counts = new int[5]; // 0未知 1畅通 2缓行 3拥堵 4严重拥堵
        boolean any = false;
        if (path.has("steps") && path.get("steps").isJsonArray()) {
            for (JsonElement stepEl : path.getAsJsonArray("steps")) {
                if (!stepEl.isJsonObject()) continue;
                JsonObject step = stepEl.getAsJsonObject();
                if (!step.has("tmcs") || !step.get("tmcs").isJsonArray()) continue;
                for (JsonElement tmcEl : step.getAsJsonArray("tmcs")) {
                    if (!tmcEl.isJsonObject()) continue;
                    JsonObject tmc = tmcEl.getAsJsonObject();
                    int status = (int) parseLong(tmc, "tmc_status");
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
        return switch (dominant) {
            case 1 -> "畅通";
            case 2 -> "缓行";
            case 3 -> "拥堵";
            case 4 -> "严重拥堵";
            default -> "未知";
        };
    }

    private static List<GeoPointDto> parsePolyline(String polyline) {
        List<GeoPointDto> points = new ArrayList<>();
        if (polyline == null || polyline.isBlank()) return points;
        String[] segs = polyline.split(";");
        for (String seg : segs) {
            String[] xy = seg.split(",");
            if (xy.length < 2) continue;
            try {
                double lng = Double.parseDouble(xy[0].trim());
                double lat = Double.parseDouble(xy[1].trim());
                points.add(new GeoPointDto(lng, lat));
            } catch (NumberFormatException ignored) {
            }
        }
        return points;
    }

    private static List<GeoPointDto> downsample(List<GeoPointDto> points, int maxPoints) {
        if (points.size() <= maxPoints) return points;
        List<GeoPointDto> out = new ArrayList<>();
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
                if (s == null || s.isBlank()) return 0;
                return Math.round(Double.parseDouble(s));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static String asString(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonArray()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        return el.toString();
    }

    private static String httpGet(String urlStr) throws Exception {
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
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException(
                    "地图服务 HTTPS 握手失败（Java SSL + curl 均不可用）。"
                            + "请检查网络/代理；若开了 Clash 等代理，可尝试关闭系统 HTTPS 代理或换网络。详情: "
                            + sb);
        }
        return sb.toString();
    }
}
