package com.gitnote.backend.repository;

import com.gitnote.backend.entity.UserPreset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
public class UserPresetRepository {

    private final DynamoDbTable<UserPreset> userPresetTable;

    public UserPresetRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                @Value("${aws.dynamodb.table.user-preset}") String tableName) {
        this.userPresetTable = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(UserPreset.class));
    }

    public UserPreset save(UserPreset userPreset) {
        userPresetTable.putItem(userPreset);
        return userPreset;
    }

    public Optional<UserPreset> findByUserId(String userId) {
        Key key = Key.builder()
                .partitionValue(userId)
                .build();
        UserPreset result = userPresetTable.getItem(key);
        return Optional.ofNullable(result);
    }

    public void deleteByUserId(String userId) {
        Key key = Key.builder()
                .partitionValue(userId)
                .build();
        userPresetTable.deleteItem(key);
    }
}
