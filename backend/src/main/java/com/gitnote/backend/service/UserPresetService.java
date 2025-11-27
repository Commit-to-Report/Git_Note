package com.gitnote.backend.service;

import com.gitnote.backend.entity.UserPreset;
import com.gitnote.backend.repository.UserPresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPresetService {

    private final UserPresetRepository userPresetRepository;

    public UserPreset createOrUpdatePreset(UserPreset preset) {
        Optional<UserPreset> existingPreset = userPresetRepository.findByUserId(preset.getUserId());

        if (existingPreset.isPresent()) {
            // 기존 설정 업데이트
            UserPreset existing = existingPreset.get();
            existing.setAutoReportEnabled(preset.getAutoReportEnabled());
            existing.setEmail(preset.getEmail());
            existing.setEmailNotificationEnabled(preset.getEmailNotificationEnabled());
            existing.setReportStyle(preset.getReportStyle());
            existing.setReportFrequency(preset.getReportFrequency());
            existing.setUpdatedAt(Instant.now());
            return userPresetRepository.save(existing);
        } else {
            // 새 설정 생성
            preset.setCreatedAt(Instant.now());
            preset.setUpdatedAt(Instant.now());
            return userPresetRepository.save(preset);
        }
    }

    public Optional<UserPreset> getPreset(String userId) {
        return userPresetRepository.findByUserId(userId);
    }

    public void deletePreset(String userId) {
        userPresetRepository.deleteByUserId(userId);
    }

    public UserPreset updateEmail(String userId, String email, Boolean enabled) {
        Optional<UserPreset> existingPreset = userPresetRepository.findByUserId(userId);
        if (existingPreset.isEmpty()) {
            throw new IllegalArgumentException("사용자 설정을 찾을 수 없습니다.");
        }

        UserPreset preset = existingPreset.get();
        preset.setEmail(email);
        preset.setEmailNotificationEnabled(enabled);
        preset.setUpdatedAt(Instant.now());
        return userPresetRepository.save(preset);
    }

    public UserPreset updateReportStyle(String userId, String reportStyle) {
        Optional<UserPreset> existingPreset = userPresetRepository.findByUserId(userId);
        if (existingPreset.isEmpty()) {
            throw new IllegalArgumentException("사용자 설정을 찾을 수 없습니다.");
        }

        UserPreset preset = existingPreset.get();
        preset.setReportStyle(reportStyle);
        preset.setUpdatedAt(Instant.now());
        return userPresetRepository.save(preset);
    }

    public UserPreset updateReportFrequency(String userId, String frequency) {
        Optional<UserPreset> existingPreset = userPresetRepository.findByUserId(userId);
        if (existingPreset.isEmpty()) {
            throw new IllegalArgumentException("사용자 설정을 찾을 수 없습니다.");
        }

        UserPreset preset = existingPreset.get();
        preset.setReportFrequency(frequency);
        preset.setUpdatedAt(Instant.now());
        return userPresetRepository.save(preset);
    }
}
