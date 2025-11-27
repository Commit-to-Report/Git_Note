package com.gitnote.backend.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // [변경 1] username을 받아서 폴더 경로로 사용
    public String uploadLog(String username, String baseFileName, String content) {

        // 1. 중복되지 않는 파일명 생성 (사용자 폴더 내부에서 검사)
        String uniqueFileName = getUniqueFileName(username, baseFileName);

        // 2. 최종 저장 경로: username/파일명
        String fullKey = username + "/" + uniqueFileName;

        // 3. 내용 변환
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // 4. 메타데이터 설정
        ObjectMetadata metadata = ObjectMetadata.builder()
                .contentType("text/plain; charset=UTF-8")
                .build();

        // 5. 업로드 (경로 포함)
        s3Template.upload(bucketName, fullKey, inputStream, metadata);

        // URL 반환
        return "https://" + bucketName + ".s3.ap-northeast-2.amazonaws.com/" + fullKey;
    }

    // [추가] 해당 사용자의 파일 목록 조회하기
    public List<String> getUserFileList(String username) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(username + "/") // "username/" 으로 시작하는 것만 검색
                .build();

        ListObjectsV2Response result = s3Client.listObjectsV2(request);

        return result.contents().stream()
                .map(S3Object::key) // 전체 경로(키)를 가져옴
                .collect(Collectors.toList());
    }

    // [변경 2] 중복 파일명 처리 로직 (사용자 폴더까지 고려)
    private String getUniqueFileName(String username, String originalFileName) {
        String fileName = originalFileName;
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        String ext = fileName.substring(fileName.lastIndexOf("."));

        int count = 1;

        // username/파일명 경로에 파일이 있는지 확인
        while (doesFileExist(username + "/" + fileName)) {
            fileName = nameWithoutExt + "(" + count + ")" + ext;
            count++;
        }
        return fileName; // 경로를 뺀 순수 파일명만 리턴
    }

    // 파일 존재 여부 확인 (전체 경로인 Key를 받음)
    private boolean doesFileExist(String fullKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fullKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}