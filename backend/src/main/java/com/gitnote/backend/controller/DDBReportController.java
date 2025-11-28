package com.gitnote.backend.controller;

import com.gitnote.backend.service.DDBReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class DDBReportController {

    private final DDBReportService reportService;

    @PostMapping("/report")
    public ResponseEntity<?> saveReport(@RequestBody Map<String, String> request) {
        try {
            reportService.saveUserReport(
                    request.get("userId"),
                    request.get("reportId"),
                    request.get("content")
            );
            return ResponseEntity.ok(Map.of("message", "보고서 저장 성공"));
        } catch(Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }
}