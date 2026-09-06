package com.ptmoc.controller;

import com.ptmoc.dto.VerifyRequest;
import com.ptmoc.dto.VerifyResponse;
import com.ptmoc.service.PtmocService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ptmoc")
public class PtmocController {

    private final PtmocService ptmocService;

    public PtmocController(PtmocService ptmocService) {
        this.ptmocService = ptmocService;
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(@RequestBody VerifyRequest request) {
        VerifyResponse response = ptmocService.executeVerification(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "PTMOC Backend",
            "version", "1.0.0"
        ));
    }
}
