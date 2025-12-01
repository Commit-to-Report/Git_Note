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
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DDBReportService {

    private final DynamoDbClient dynamoDbClient;

    /**
     * 사용자 보고서 저장 (서버 로컬 시간대 사용)
     */
    public void saveUserReport(String userId, String reportId, String reportContent) {
        saveUserReport(userId, reportId, reportContent, null);
    }

    /**
     * 사용자 보고서 저장 (지정된 시간대 사용)
     * @param userId 사용자 ID
     * @param reportId 보고서 ID
     * @param reportContent 보고서 내용
     * @param zoneId 시간대 (null이면 서버 로컬 시간대 사용)
     */
    public void saveUserReport(String userId, String reportId, String reportContent, ZoneId zoneId) {
        String userName = reportId.split("/")[0];
        // zoneId가 제공되면 해당 시간대 사용, 없으면 서버 로컬 시간대 사용
        String now = (zoneId != null) 
            ? LocalDateTime.now(zoneId).toString()
            : LocalDateTime.now().toString();

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

