package com.gitnote.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class UserPreset {

    private String userId;              // GitHub 사용자 ID (Partition Key)
    private Boolean autoReportEnabled;  // 보고서 자동 생성 활성화 여부
    private String email;               // 알림받을 이메일
    private Boolean emailNotificationEnabled;  // 이메일 알림 활성화 여부
    private String reportStyle;         // 보고서 스타일/프롬프트
    private String reportFrequency;     // 보고서 생성 주기 (DAILY, WEEKLY, MONTHLY)
    private String repository;          // 자동 보고서 생성할 리포지토리 (fullName 형식: owner/repo)
    private Instant createdAt;          // 생성 시간
    private Instant updatedAt;          // 수정 시간

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")  // DynamoDB 테이블의 파티션 키 이름과 매핑
    public String getUserId() {
        return userId;
    }
}
