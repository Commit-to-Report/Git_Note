package com.gitnote.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.s3.InMemoryBufferingS3OutputStreamProvider;
import io.awspring.cloud.s3.Jackson2JsonS3ObjectConverter;
import io.awspring.cloud.s3.S3Template;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3와의 연동을 위한 설정 클래스입니다.
 * AWS 크레덴셜 및 리전을 주입받아 S3 관련 빈을 등록합니다.
 */
@Configuration
public class S3Config {

    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    /**
     * S3Client Bean 등록
     * - AWS 크레덴셜과 리전을 바탕으로 S3Client 인스턴스를 생성합니다.
     * - S3 서버와 통신할 클라이언트를 생성
     */
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    }

    /**
     * S3Presigner Bean 등록
     * - Presigned URL 생성을 위한 S3Presigner 인스턴스를 생성합니다.
     */
    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    }

    /**
     * S3Template Bean 등록
     * - Spring Cloud AWS에서 제공하는 S3Template을 생성합니다.
     * - S3 업로드/다운로드 등의 작업을 간편하게 수행할 수 있습니다.
     */
    @Bean
    public S3Template s3Template(S3Client s3Client, S3Presigner s3Presigner) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new S3Template(
            s3Client,
            new InMemoryBufferingS3OutputStreamProvider(s3Client, null),
            new Jackson2JsonS3ObjectConverter(objectMapper),
            s3Presigner
        );
    }
}
