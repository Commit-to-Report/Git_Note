package com.gitnote.backend.dto;

import com.gitnote.backend.entity.UserPreset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresetResponse {
    private String userId;
    private Boolean autoReportEnabled;
    private String email;
    private Boolean emailNotificationEnabled;
    private String reportStyle;
    private String reportFrequency;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserPresetResponse from(UserPreset preset) {
        return UserPresetResponse.builder()
                .userId(preset.getUserId())
                .autoReportEnabled(preset.getAutoReportEnabled())
                .email(preset.getEmail())
                .emailNotificationEnabled(preset.getEmailNotificationEnabled())
                .reportStyle(preset.getReportStyle())
                .reportFrequency(preset.getReportFrequency())
                .createdAt(preset.getCreatedAt())
                .updatedAt(preset.getUpdatedAt())
                .build();
    }
}
