package com.gitnote.backend.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;
    private final S3Client s3Client; // 존재 여부 확인을 위해 추가

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    public String uploadLog(String baseFileName, String content) {
        // 1. 중복되지 않는 파일명 생성 (예: file.txt -> file(1).txt)
        String uniqueFileName = getUniqueFileName(baseFileName);

        // 2. 내용 변환 (UTF-8)
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // 3. 메타데이터 설정 (한글 깨짐 방지)
        ObjectMetadata metadata = ObjectMetadata.builder()
                .contentType("text/plain; charset=UTF-8")
                .build();

        // 4. 업로드
        s3Template.upload(bucketName, uniqueFileName, inputStream, metadata);

        // 5. URL 반환
        return "https://" + bucketName + ".s3.ap-northeast-2.amazonaws.com/" + uniqueFileName;
    }

    // 중복 파일명 처리 로직
    private String getUniqueFileName(String originalFileName) {
        String fileName = originalFileName;
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        String ext = fileName.substring(fileName.lastIndexOf(".")); // .txt

        int count = 1;
        // 파일이 존재하는 동안 계속 (1), (2)... 숫자를 늘려가며 확인
        while (doesFileExist(fileName)) {
            fileName = nameWithoutExt + "(" + count + ")" + ext;
            count++;
        }
        return fileName;
    }

    // S3에 파일이 있는지 확인하는 메서드
    private boolean doesFileExist(String fileName) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build());
            return true; // 에러가 안 나면 파일이 있다는 뜻
        } catch (NoSuchKeyException e) {
            return false; // 파일이 없음 (사용 가능!)
        }
    }
}