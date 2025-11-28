package com.gitnote.backend.controller;

import com.gitnote.backend.service.DDBReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

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

    @GetMapping("/report/list")
    public ResponseEntity<?> getAllReports() {
        try {
            return ResponseEntity.ok(reportService.getAllReports());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/report/view")
    public ResponseEntity<?> getReportByPKAndSK(@RequestParam String pk, @RequestParam String sk) {
        Map<String, Object> report = reportService.getReportByPKAndSK(pk, sk);
        if (report == null) return ResponseEntity.status(404).body(Map.of("message", "보고서를 찾을 수 없습니다."));
        return ResponseEntity.ok(report);
    }
}
