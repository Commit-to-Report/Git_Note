package com.gitnote.backend.controller;

import com.gitnote.backend.dto.S3UploadRequest;
import com.gitnote.backend.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadCommitLog(@RequestBody S3UploadRequest request) {
        try {
            // 1. 리포지토리 이름 안전하게 변경
            String safeRepoName = request.getRepositoryName().replace("/", "_");

            // 2. [변경] 오늘 날짜 가져오기 (yyyy-MM-dd)
            String todayDate = LocalDate.now().toString();

            // 3. 파일명 생성 ( repo_2025-11-26.txt )
            // 이제 Service가 알아서 (1), (2)를 붙여주므로 기본 이름만 넘깁니다.
            String baseFileName = safeRepoName + "_" + todayDate + ".txt";

            // 4. 업로드 요청
            String fileUrl = s3Service.uploadLog(baseFileName, request.getContent());

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
}