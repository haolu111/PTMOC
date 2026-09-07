package com.ptmoc.controller;

import com.ptmoc.dto.LocationSuggestion;
import com.ptmoc.dto.RoutePlanRequest;
import com.ptmoc.dto.RoutePlanResponse;
import com.ptmoc.service.AmapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private final AmapService amapService;

    public MapController(AmapService amapService) {
        this.amapService = amapService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("configured", amapService.isConfigured());
        body.put("service", "PTMOC Map Proxy");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/tips")
    public ResponseEntity<?> tips(
            @RequestParam("keywords") String keywords,
            @RequestParam(value = "city", required = false, defaultValue = "上海") String city) {
        try {
            List<LocationSuggestion> tips = amapService.searchTips(keywords, city);
            return ResponseEntity.ok(tips);
        } catch (IllegalStateException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "地图服务暂时不可用");
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @PostMapping("/routes")
    public ResponseEntity<?> routes(@RequestBody RoutePlanRequest request) {
        try {
            RoutePlanResponse response = amapService.planDrivingRoutes(request);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "路径规划暂时不可用");
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
