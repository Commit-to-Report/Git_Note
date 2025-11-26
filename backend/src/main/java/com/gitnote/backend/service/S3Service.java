package com.gitnote.backend.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.stream.Collectors;

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

    // [추가] S3 버킷의 모든 텍스트 파일 목록 조회
    public List<String> getFileList() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response result = s3Client.listObjectsV2(request);

        return result.contents().stream()
                .map(S3Object::key)
                .filter(key -> key.endsWith(".txt")) // txt 파일만 필터링
                .collect(Collectors.toList());
    }

    // [추가] S3 파일 내용 읽어오기 (String으로 반환)
    public String getFileContent(String fileName) {

        try (InputStream is = s3Template.download(bucketName, fileName).getInputStream()) {
             return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
             throw new RuntimeException("파일 읽기 실패", e);
        }
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