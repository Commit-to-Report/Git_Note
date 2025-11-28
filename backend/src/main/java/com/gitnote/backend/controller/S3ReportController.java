package com.gitnote.backend.controller;

import com.gitnote.backend.service.GeminiApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
public class S3ReportController {

    private final GeminiApiService geminiApiService;
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @GetMapping("/report")
    public ResponseEntity<?> generateReport(
            @RequestParam String key,
            @RequestParam(defaultValue = "summary") String style
    ) {
        try {
            key = java.net.URLDecoder.decode(key, StandardCharsets.UTF_8);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            InputStream s3InputStream = s3Client.getObject(request);
            String content = new Scanner(s3InputStream, StandardCharsets.UTF_8).useDelimiter("\\A").next();

            String summary = geminiApiService.generateContent(content, style);

            return ResponseEntity.ok(Map.of("summary", summary));

        } catch (S3Exception e) {
            if(e.statusCode() == 404){
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "S3 파일을 찾을 수 없음: " + key));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "S3 파일 로딩 실패: " + e.awsErrorDetails().errorMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Gemini 요약 실패: " + e.getMessage()));
        }
    }


}
