package com.gitnote.backend.service;

import com.gitnote.backend.entity.UserPreset;
import com.gitnote.backend.repository.UserPresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * UserPresetService
 * - 사용자 프리셋(설정) 관리 서비스
 * - 사용자 프리셋 생성, 수정, 조회, 삭제 등의 비즈니스 로직 담당
 */
@Service
@RequiredArgsConstructor
public class UserPresetService {

    private final UserPresetRepository userPresetRepository;

    /**
     * 사용자 프리셋을 생성 또는 업데이트 (upsert: 있으면 수정, 없으면 생성)
     * @param preset 저장할 설정(사용자ID 필수)
     * @return 저장/수정된 UserPreset 객체
     */
    public UserPreset createOrUpdatePreset(UserPreset preset) {
        // 기존 프리셋 있는지 먼저 조회
        return userPresetRepository.findByUserId(preset.getUserId())
                .map(existing -> {
                    // 기존 값 업데이트 (필요 필드만)
                    existing.setAutoReportEnabled(preset.getAutoReportEnabled());
                    existing.setEmail(preset.getEmail());
                    existing.setEmailNotificationEnabled(preset.getEmailNotificationEnabled());
                    existing.setReportStyle(preset.getReportStyle());
                    existing.setReportFrequency(preset.getReportFrequency());
                    existing.setRepository(preset.getRepository());
                    existing.setUpdatedAt(Instant.now());
                    return userPresetRepository.save(existing);
                })
                .orElseGet(() -> {
                    // 신규 생성: timestamp 기본값 설정
                    Instant now = Instant.now();
                    preset.setCreatedAt(now);
                    preset.setUpdatedAt(now);
                    return userPresetRepository.save(preset);
                });
    }

    /**
     * 사용자 프리셋 단건 조회
     * @param userId 사용자 ID
     * @return Optional<UserPreset>
     */
    public Optional<UserPreset> getPreset(String userId) {
        return userPresetRepository.findByUserId(userId);
    }

    /**
     * 사용자 프리셋 삭제
     * @param userId 사용자 ID
     */
    public void deletePreset(String userId) {
        userPresetRepository.deleteByUserId(userId);
    }

    /**
     * 사용자 프리셋의 이메일 설정/알림 활성화 수정
     * @param userId 사용자 ID
     * @param email 이메일 주소
     * @param enabled 알림 활성화 여부
     * @return 수정된 UserPreset
     */
    public UserPreset updateEmail(String userId, String email, Boolean enabled) {
        // 프리셋 미존재 시 예외 처리
        UserPreset preset = userPresetRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 설정을 찾을 수 없습니다."));
        // 변경
        preset.setEmail(email);
        preset.setEmailNotificationEnabled(enabled);
        preset.setUpdatedAt(Instant.now());
        return userPresetRepository.save(preset);
    }

    /**
     * 사용자 프리셋의 보고서 스타일 변경
     * @param userId 사용자 ID
     * @param reportStyle 보고서 스타일 문자열
     * @return 수정된 UserPreset
     */
    public UserPreset updateReportStyle(String userId, String reportStyle) {
        UserPreset preset = userPresetRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 설정을 찾을 수 없습니다."));
        preset.setReportStyle(reportStyle);
        preset.setUpdatedAt(Instant.now());
        return userPresetRepository.save(preset);
    }

    /**
     * 사용자 프리셋의 보고서 빈도 변경
     * @param userId 사용자 ID
     * @param frequency 빈도 문자열 (예: DAILY, WEEKLY 등)
     * @return 수정된 UserPreset
     */
    public UserPreset updateReportFrequency(String userId, String frequency) {
        UserPreset preset = userPresetRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 설정을 찾을 수 없습니다."));
        preset.setReportFrequency(frequency);
        preset.setUpdatedAt(Instant.now());
        return userPresetRepository.save(preset);
    }
}
