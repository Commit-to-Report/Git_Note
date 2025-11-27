package com.gitnote.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final DynamoDbClient dynamoDbClient;

    public void saveUserReport(String userId, String reportId, String reportContent) {
        String userName = reportId.contains("_") ? reportId.split("_")[0] : reportId;

        PutItemRequest request = PutItemRequest.builder()
                .tableName("UserReports")
                .item(Map.of(
                        //TODO: PK 숫자로 바꿔서 수정
                        "PK", AttributeValue.builder().s(userId).build(),
                        "SK", AttributeValue.builder().s(reportId).build(),
                        "User", AttributeValue.builder().s(userName).build(),
                        "Content", AttributeValue.builder().s(reportContent).build(),
                        "UpdatedAt", AttributeValue.builder().s(LocalDateTime.now().toString()).build()
                ))
                .build();

        dynamoDbClient.putItem(request);
    }
}
