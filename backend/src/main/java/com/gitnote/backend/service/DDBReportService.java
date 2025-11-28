package com.gitnote.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DDBReportService {

    private final DynamoDbClient dynamoDbClient;

    public void saveUserReport(String userId, String reportId, String reportContent) {
        String userName = reportId.split("/")[0];
        String now = LocalDateTime.now().toString();

        PutItemRequest request = PutItemRequest.builder()
                .tableName("UserReports")
                .item(Map.of(
                        "PK", AttributeValue.builder().s(reportId).build(),
                        "SK", AttributeValue.builder().s(now).build(),
                        "User", AttributeValue.builder().s(userName).build(),
                        "Content", AttributeValue.builder().s(reportContent).build()
                ))
                .build();

        dynamoDbClient.putItem(request);
    }
}
