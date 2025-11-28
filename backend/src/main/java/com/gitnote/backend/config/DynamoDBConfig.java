package com.gitnote.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * DynamoDB와의 연동을 위한 설정 클래스입니다.
 * AWS 크레덴셜 및 리전을 주입받아 DynamoDB 관련 빈을 등록합니다.
 */
@Configuration
public class DynamoDBConfig {

    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    /**
     * DynamoDbClient Bean 등록
     * - AWS 크레덴셜과 리전을 바탕으로 DynamoDbClient 인스턴스를 생성합니다.
     * - DynamoDB 서버와 통신할 클라이언트를 생성
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    }

    /**
     * DynamoDbEnhancedClient Bean 등록
     * - 위에서 생성한 DynamoDbClient를 바탕으로 EnhancedClient를 제공합니다.
     * - 일반 DynamoDbClient보다 객체 매핑(POJO ↔ DynamoDB Item) 기능, 편리한 CRUD API를 제공
     */
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
