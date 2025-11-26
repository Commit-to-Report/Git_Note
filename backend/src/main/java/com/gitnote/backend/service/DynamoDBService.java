package com.gitnote.backend.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DynamoDBService {

    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${aws.dynamodb.table}")
    private String tableName;

    public void saveReport(String repoName, String reportContent) {
        // 1. 테이블 객체 생성 (이름은 yml에서 가져옴)
        DynamoDbTable<ReportEntity> reportTable = enhancedClient.table(tableName, TableSchema.fromBean(ReportEntity.class));

        // 2. 데이터 생성
        ReportEntity report = new ReportEntity();
        report.setId(UUID.randomUUID().toString());
        report.setRepoName(repoName);
        report.setContent(reportContent);
        report.setCreatedDate(LocalDate.now().toString());

        // 3. 저장
        reportTable.putItem(report);
        System.out.println("✅ DynamoDB 저장 성공: " + report.getId());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class ReportEntity {
        private String id;
        private String repoName;
        private String content;
        private String createdDate;

        @DynamoDbPartitionKey
        public String getId() { return id; }
    }
}