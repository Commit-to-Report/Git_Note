package com.gitnote.backend.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

// [추가된 import] Presigned URL 관련
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner; // [핵심] Config에 있는 프리사이너 주입

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // 1. 파일 업로드 (기존 유지)
    public String uploadLog(String username, String baseFileName, String content) {
        String uniqueFileName = getUniqueFileName(username, baseFileName);
        String fullKey = username + "/" + uniqueFileName;

        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ObjectMetadata metadata = ObjectMetadata.builder()
                .contentType("text/plain; charset=UTF-8")
                .build();

        s3Template.upload(bucketName, fullKey, inputStream, metadata);

        // [팁] DB에는 긴 URL 대신 'fullKey'(경로)만 저장하는 게 좋습니다.
        // 하지만 기존 코드 호환성을 위해 일단 둡니다.
        return fullKey;
    }

    // 2. [NEW] 임시 열람 URL 생성 (이게 없어서 안 보이는 겁니다!)
    public String getPresignedUrl(String keyName) {
        if (keyName == null || keyName.isEmpty()) return "";

        // 혹시 DB에 전체 URL(https://...)로 저장되어 있다면 경로만 잘라냅니다.
        if (keyName.startsWith("http")) {
            // ".com/" 뒷부분부터 가져옴
            int index = keyName.indexOf(".com/");
            if (index != -1) {
                keyName = keyName.substring(index + 5);
            }
        }

        try {
            // S3에게 "이 파일 10분만 보여줘" 요청 생성
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName) // 파일 경로 (예: MinJ-i/file.txt)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10)) // 10분 유효
                    .getObjectRequest(objectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            // 암호가 잔뜩 붙은 긴 URL 반환
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("[S3Service] Presigned URL 생성 실패: {}", e.getMessage());
            return "";
        }
    }

    // ... (나머지 getUserFileList, getUniqueFileName 등 기존 코드 유지) ...
    public List<String> getUserFileList(String username) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(username + "/")
                .build();
        ListObjectsV2Response result = s3Client.listObjectsV2(request);
        return result.contents().stream().map(S3Object::key).collect(Collectors.toList());
    }

    // [수정/추가] ★정보(날짜, 크기 등)를 통째로 주는 메서드★
    public List<S3Object> getUserFileSummaries(String username) {
        String prefix = username + "/";

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        // s3Client를 사용해야 합니다! (amazonS3 아님)
        ListObjectsV2Response result = s3Client.listObjectsV2(request);

        // v2에서는 getObjectSummaries()가 아니라 contents()입니다.
        return result.contents();
    }

    private String getUniqueFileName(String username, String originalFileName) {
        String fileName = originalFileName;
        String nameWithoutExt = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : "";
        int count = 1;
        while (doesFileExist(username + "/" + fileName)) {
            fileName = nameWithoutExt + "(" + count + ")" + ext;
            count++;
        }
        return fileName;
    }

    private boolean doesFileExist(String fullKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(fullKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}