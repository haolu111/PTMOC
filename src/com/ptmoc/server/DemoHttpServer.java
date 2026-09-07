package com.ptmoc.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Lightweight JDK8-compatible HTTP server exposing the same API contract as Spring Boot backend:
 *   GET  /api/ptmoc/health
 *   POST /api/ptmoc/verify
 *   GET  /api/map/health
 *   GET  /api/map/tips?keywords=...&city=上海
 */
public class DemoHttpServer {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final PtmocDemoService SERVICE = new PtmocDemoService();
    private static final AmapTipsClient AMAP = AmapTipsClient.fromEnv();

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/ptmoc/health", new HealthHandler());
        server.createContext("/api/ptmoc/verify", new VerifyHandler());
        server.createContext("/api/map/health", new MapHealthHandler());
        server.createContext("/api/map/tips", new MapTipsHandler());
        server.createContext("/", new RootHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("PTMOC Demo HTTP Server started on http://localhost:" + port);
        System.out.println("  GET  /api/ptmoc/health");
        System.out.println("  POST /api/ptmoc/verify");
        System.out.println("  GET  /api/map/health");
        System.out.println("  GET  /api/map/tips");
        System.out.println("Amap configured: " + AMAP.isConfigured());
        System.out.println("Uses real PTMOC core: Setup → KeyGen → Encode → Encrypt → Eval → Decrypt");
    }

    private static void addCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        addCors(headers);
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> queryParams(URI uri) throws IOException {
        Map<String, String> map = new HashMap<String, String>();
        String q = uri.getRawQuery();
        if (q == null || q.isEmpty()) return map;
        String[] pairs = q.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                map.put(URLDecoder.decode(pair, "UTF-8"), "");
            } else {
                String k = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                map.put(k, v);
            }
        }
        return map;
    }

    static class RootHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("service", "PTMOC JDK8 Demo Server");
            body.put("health", "/api/ptmoc/health");
            body.put("verify", "POST /api/ptmoc/verify");
            body.put("mapTips", "GET /api/map/tips");
            writeJson(exchange, 200, body);
        }
    }

    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, error("Method Not Allowed"));
                return;
            }
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("status", "UP");
            body.put("service", "PTMOC Backend");
            body.put("version", "1.0.0-jdk8-demo");
            body.put("crypto", "real");
            writeJson(exchange, 200, body);
        }
    }

    static class MapHealthHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, error("Method Not Allowed"));
                return;
            }
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("configured", AMAP.isConfigured());
            body.put("service", "PTMOC Map Proxy");
            writeJson(exchange, 200, body);
        }
    }

    static class MapTipsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, error("Method Not Allowed"));
                return;
            }
            try {
                Map<String, String> params = queryParams(exchange.getRequestURI());
                String keywords = params.get("keywords");
                String city = params.get("city");
                if (city == null || city.isEmpty()) city = "上海";
                List<Map<String, Object>> tips = AMAP.searchTips(keywords, city);
                writeJson(exchange, 200, tips);
            } catch (IllegalStateException e) {
                writeJson(exchange, 400, error(e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                writeJson(exchange, 500, error("地图服务暂时不可用（" + msg + "）"));
            }
        }
    }

    static class VerifyHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, error("Method Not Allowed"));
                return;
            }
            try {
                String json = readBody(exchange);
                VerifyRequest request = GSON.fromJson(json, VerifyRequest.class);
                if (request == null
                        || request.getUserTrajectory() == null
                        || request.getReferenceTrajectory() == null) {
                    writeJson(exchange, 400, error("userTrajectory and referenceTrajectory are required"));
                    return;
                }
                VerifyResponse response = SERVICE.executeVerification(request);
                writeJson(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace();
                writeJson(exchange, 500, error(e.getMessage()));
            }
        }
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("error", message == null ? "unknown" : message);
        return m;
    }
}
