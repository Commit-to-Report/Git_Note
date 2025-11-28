package com.gitnote.backend.dto;

import lombok.Data;

@Data
public class UserPresetRequest {
    private Boolean autoReportEnabled;         // 보고서 자동 생성 활성화 여부
    private String email;                      // 알림받을 이메일
    private Boolean emailNotificationEnabled;  // 이메일 알림 활성화 여부
    private String reportStyle;                // 보고서 스타일/프롬프트
    private String reportFrequency;            // 보고서 생성 주기 (DAILY, WEEKLY, MONTHLY)
    private String repository;                 // 자동 보고서 생성할 리포지토리 (fullName 형식: owner/repo)
}
