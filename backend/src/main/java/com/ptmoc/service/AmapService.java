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
    private static final int[] EXTRA_STRATEGIES = {35, 36, 34};

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
            // 必须带 alternative_route=3，并按策略分别请求，避免三条变成同一条克隆
            List<RoutePlanDto> recommendPaths = fetchAllDrivingPaths(origin, dest, STRATEGY_RECOMMEND, 3);
            List<RoutePlanDto> avoidPaths = fetchAllDrivingPaths(origin, dest, STRATEGY_AVOID, 3);
            List<RoutePlanDto> fastestPaths = fetchAllDrivingPaths(origin, dest, STRATEGY_FASTEST, 3);

            List<RoutePlanDto> pool = new ArrayList<>();
            pool.addAll(recommendPaths);
            pool.addAll(avoidPaths);
            pool.addAll(fastestPaths);
            if (dedupeRoutes(pool).size() < 3) {
                for (int s : EXTRA_STRATEGIES) {
                    pool.addAll(fetchAllDrivingPaths(origin, dest, s, 3));
                    if (dedupeRoutes(pool).size() >= 3) break;
                }
            }
            if (pool.isEmpty()) {
                throw new IllegalStateException("无可用路径");
            }

            RoutePlanDto recommend = firstOrBest(recommendPaths, pool, false);
            RoutePlanDto avoid = pickDifferent(avoidPaths, pool, recommend, null, false);
            RoutePlanDto fastest = pickDifferent(fastestPaths, pool, recommend, avoid, true);

            List<RoutePlanDto> routes = new ArrayList<>();
            routes.add(cloneNamed(recommend, "route-recommend", "推荐路线", STRATEGY_RECOMMEND));
            routes.add(cloneNamed(avoid, "route-avoid-congestion", "躲避拥堵", STRATEGY_AVOID));
            routes.add(cloneNamed(fastest, "route-fastest", "速度最快", STRATEGY_FASTEST));

            RoutePlanResponse response = new RoutePlanResponse();
            response.setRoutes(routes);
            return response;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("路径规划失败: " + e.getMessage(), e);
        }
    }

    private static RoutePlanDto firstOrBest(List<RoutePlanDto> preferred, List<RoutePlanDto> pool, boolean preferFast) {
        if (preferred != null && !preferred.isEmpty()) return preferred.get(0);
        if (pool == null || pool.isEmpty()) throw new IllegalStateException("无可用路径");
        if (!preferFast) return pool.get(0);
        RoutePlanDto best = pool.get(0);
        for (RoutePlanDto r : pool) {
            if (r.getDurationSeconds() < best.getDurationSeconds()) best = r;
        }
        return best;
    }

    private static RoutePlanDto pickDifferent(
            List<RoutePlanDto> preferred,
            List<RoutePlanDto> pool,
            RoutePlanDto excludeA,
            RoutePlanDto excludeB,
            boolean preferFast) {
        if (preferred != null) {
            RoutePlanDto best = null;
            for (RoutePlanDto r : preferred) {
                if (sameRoute(r, excludeA) || sameRoute(r, excludeB)) continue;
                if (best == null) best = r;
                else if (preferFast && r.getDurationSeconds() < best.getDurationSeconds()) best = r;
                else if (!preferFast && routeDiffScore(r, excludeA) > routeDiffScore(best, excludeA)) best = r;
            }
            if (best != null) return best;
        }
        RoutePlanDto best = null;
        for (RoutePlanDto r : pool) {
            if (sameRoute(r, excludeA) || sameRoute(r, excludeB)) continue;
            if (best == null) {
                best = r;
                continue;
            }
            if (preferFast) {
                if (r.getDurationSeconds() < best.getDurationSeconds()) best = r;
            } else if (routeDiffScore(r, excludeA) > routeDiffScore(best, excludeA)) {
                best = r;
            }
        }
        if (best != null) return best;
        if (preferred != null && !preferred.isEmpty()) return preferred.get(0);
        if (excludeA != null) return excludeA;
        return pool.get(0);
    }

    private static long routeDiffScore(RoutePlanDto r, RoutePlanDto base) {
        if (base == null) return 0;
        return Math.abs(r.getDistanceMeters() - base.getDistanceMeters())
                + Math.abs(r.getDurationSeconds() - base.getDurationSeconds()) * 10
                + Math.abs(r.getTrafficLights() - base.getTrafficLights()) * 100L;
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
