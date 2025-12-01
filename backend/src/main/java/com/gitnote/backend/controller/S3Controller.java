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
import java.util.stream.Collectors;

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
            // 1. S3에서 파일 키(경로) 목록 가져오기
            List<String> fileKeys = s3Service.getUserFileList(username);

            // 2. 각 파일마다 '프리사인 URL' 발급해서 리스트로 만들기
            List<Map<String, String>> fileList = new ArrayList<>();

            for (String key : fileKeys) {
                // 파일명만 추출 (예: MinJ-i/abc.txt -> abc.txt)
                String fileName = key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key;

                // [핵심] 10분짜리 임시 열람 주소 발급
                String presignedUrl = s3Service.getPresignedUrl(key);

                // 정보 담기
                fileList.add(Map.of(
                        "fileName", fileName,   // 화면 표시용 이름
                        "url", presignedUrl,    // 실제 데이터 가져올 주소 (서명 포함됨)
                        "key", key              // 원본 경로
                ));
            }

            // 3. 변경된 리스트 반환
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