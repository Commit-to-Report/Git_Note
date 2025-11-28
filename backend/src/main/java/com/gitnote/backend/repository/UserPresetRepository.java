package com.gitnote.backend.repository;

import com.gitnote.backend.entity.UserPreset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

/**
 * UserPresetRepository
 * - 사용자별 프리셋 설정(DynamoDB) 조작을 책임지는 레포지토리 클래스
 */
@Repository
public class UserPresetRepository {

    // DynamoDB 테이블 객체 (UserPreset)
    private final DynamoDbTable<UserPreset> userPresetTable;

    /**
     * 생성자: DynamoDbEnhancedClient와 테이블명 입력 받아 테이블 객체 생성
     * @param dynamoDbEnhancedClient DynamoDB Enhanced 클라이언트
     * @param tableName 사용자 프리셋 테이블 이름 (application.yml에서 주입)
     */
    public UserPresetRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                @Value("${aws.dynamodb.table.user-preset}") String tableName) {
        this.userPresetTable = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(UserPreset.class));
    }

    /**
     * 사용자 프리셋 데이터 저장(업서트)
     * @param userPreset 저장할 사용자 프리셋
     * @return 저장된 사용자 프리셋
     */
    public UserPreset save(UserPreset userPreset) {
        userPresetTable.putItem(userPreset); // PK 충돌 시 덮어씀
        return userPreset;
    }

    /**
     * userId로 사용자 프리셋 조회 (없는 경우 Optional.empty 반환)
     * @param userId GitHub 사용자 ID (PartitionKey)
     * @return Optional<UserPreset> 프리셋
     */
    public Optional<UserPreset> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty(); // 불필요한 쿼리 방지
        }
        Key key = Key.builder()
                .partitionValue(userId)
                .build();
        return Optional.ofNullable(userPresetTable.getItem(key));
    }

    /**
     * userId로 사용자 프리셋 삭제
     * @param userId GitHub 사용자 ID (PartitionKey)
     */
    public void deleteByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return; // 입력값 검증
        }
        Key key = Key.builder()
                .partitionValue(userId)
                .build();
        userPresetTable.deleteItem(key);
    }
}
