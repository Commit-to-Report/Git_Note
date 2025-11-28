package com.gitnote.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import java.util.List;
import java.util.stream.Collectors;
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

    public List<Map<String, String>> getAllReports() {
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("UserReports")
                .build();

        ScanResponse response = dynamoDbClient.scan(scanRequest);

        return response.items().stream()
                .map(item -> item.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().s()
                        ))
                ).toList();
    }

    public Map<String, Object> getReportByPKAndSK(String pk, String sk) {
        Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder().s(pk).build(),
                "SK", AttributeValue.builder().s(sk).build()
        );

        var request = software.amazon.awssdk.services.dynamodb.model.GetItemRequest.builder()
                .tableName("UserReports")
                .key(key)
                .build();

        var response = dynamoDbClient.getItem(request);

        if (response.hasItem()) {
            return response.item().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> (Object) e.getValue().s()
                    ));
        }
        return null;
    }
}

