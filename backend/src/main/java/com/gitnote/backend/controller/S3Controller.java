package com.gitnote.backend.controller;

import com.gitnote.backend.dto.S3UploadRequest;
import com.gitnote.backend.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.s3.model.S3Object;
import java.util.Date;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    // 1. 업로드 (기존 유지)
    @PostMapping("/upload")
    public ResponseEntity<?> uploadCommitLog(@RequestBody S3UploadRequest request) {
        try {
            if (request.getUsername() == null || request.getUsername().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username is required"));
            }
            String safeRepoName = request.getRepositoryName().replace("/", "_");
            String todayDate = LocalDate.now().toString();
            String baseFileName = safeRepoName + "_" + todayDate + ".txt";

            // 업로드 실행
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

    // [수정됨] 2. 내 커밋 파일 목록 조회
    // 단순히 파일명만 주는 게 아니라, '열람 가능한 URL'도 같이 줍니다!
    @GetMapping("/list")
    public ResponseEntity<?> getMyCommitLogs(@RequestParam String username) {
        try {
            // [변경] 반환 타입이 S3Object로 바뀜
            List<S3Object> summaries = s3Service.getUserFileSummaries(username);

            List<Map<String, Object>> fileList = new ArrayList<>();

            for (S3Object summary : summaries) { // 타입 S3Object
                String key = summary.key(); // .getKey() -> .key()

                if (key.endsWith("/")) continue;

                String fileName = key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key;
                String presignedUrl = s3Service.getPresignedUrl(key);

                // [핵심 변경] v2는 get...()이 아니라 그냥 .size(), .lastModified() 입니다.
                long size = summary.size();

                // Instant 타입을 Date 타입으로 변환 (프론트엔드 호환용)
                Date lastModified = Date.from(summary.lastModified());

                fileList.add(Map.of(
                        "fileName", fileName,
                        "url", presignedUrl,
                        "key", key,
                        "size", size,
                        "lastModified", lastModified
                ));
            }

            // [정렬 로직 유지]
            fileList.sort((a, b) -> {
                Date dateA = (Date) a.get("lastModified");
                Date dateB = (Date) b.get("lastModified");
                return dateB.compareTo(dateA);
            });

            return ResponseEntity.ok(Map.of("files", fileList, "count", fileList.size()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to load list: " + e.getMessage()
            ));
        }
    }
}