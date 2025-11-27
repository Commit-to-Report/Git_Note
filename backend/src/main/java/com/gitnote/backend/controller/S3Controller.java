package com.gitnote.backend.controller;

import com.gitnote.backend.dto.S3UploadRequest;
import com.gitnote.backend.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List; // List import 필요
import java.util.Map;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    // 1. 업로드 (사용자별 폴더 저장)
    @PostMapping("/upload")
    public ResponseEntity<?> uploadCommitLog(@RequestBody S3UploadRequest request) {
        try {
            // [추가] 사용자 아이디 확인 (없으면 에러 처리)
            if (request.getUsername() == null || request.getUsername().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username is required"));
            }

            // 1. 리포지토리 이름 안전하게 변경
            String safeRepoName = request.getRepositoryName().replace("/", "_");

            // 2. 오늘 날짜 가져오기
            String todayDate = LocalDate.now().toString();

            // 3. 기본 파일명 생성 (예: repo_2025-11-27.txt)
            String baseFileName = safeRepoName + "_" + todayDate + ".txt";

            // 4. 업로드 요청 (username을 첫 번째 인자로 전달!)
            // Service가 "username/파일명" 경로로 저장해줌
            String fileUrl = s3Service.uploadLog(request.getUsername(), baseFileName, request.getContent());

            return ResponseEntity.ok(Map.of(
                    "url", fileUrl,
                    "message", "Upload success"
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Upload failed: " + e.getMessage()
            ));
        }
    }

    // [추가] 2. 내 커밋 파일 목록 조회
    // 요청 예시: GET /api/s3/list?username=minji
    @GetMapping("/list")
    public ResponseEntity<?> getMyCommitLogs(@RequestParam String username) {
        try {
            List<String> fileList = s3Service.getUserFileList(username);
            return ResponseEntity.ok(Map.of(
                    "files", fileList,
                    "count", fileList.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to load list: " + e.getMessage()
            ));
        }
    }
}