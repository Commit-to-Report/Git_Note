package com.gitnote.backend.controller;

import com.gitnote.backend.service.DynamoDBService;
import com.gitnote.backend.service.GeminiService;
import com.gitnote.backend.service.S3Service;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final GeminiService geminiService;
    private final DynamoDBService dynamoDBService;

    // ▼▼▼ [이 부분이 빠져서 에러가 난 겁니다!] ▼▼▼
    private final S3Service s3Service;
    // ▲▲▲ 꼭 추가해주세요!

    // 1. 텍스트 직접 입력해서 생성 (기존)
    @PostMapping("/generate")
    public ResponseEntity<?> generateAndSave(@RequestBody ReportRequest request) {
        try {
            String aiReport = geminiService.generateReport(request.getCommitLogs());
            dynamoDBService.saveReport(request.getRepoName(), aiReport);
            return ResponseEntity.ok(Map.of("report", aiReport));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 2. S3 파일명으로 생성 (프론트엔드가 호출하는 API)
    @PostMapping("/generate-from-file")
    public ResponseEntity<?> generateReportFromFile(@RequestBody Map<String, String> payload) {
        try {
            String fileName = payload.get("fileName");

            // 1) S3에서 파일 내용 읽어오기
            String commitLogs = s3Service.getFileContent(fileName);

            // 2) AI에게 보고서 작성 요청
            String aiReport = geminiService.generateReport(commitLogs);

            // 3) DB에 저장 (리포지토리 이름은 파일명으로 대체)
            dynamoDBService.saveReport(fileName, aiReport);

            // 4) 결과 반환 (화면에 표시하기 위해)
            return ResponseEntity.ok(Map.of("report", aiReport));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    public static class ReportRequest {
        private String repoName;
        private String commitLogs;
    }
}