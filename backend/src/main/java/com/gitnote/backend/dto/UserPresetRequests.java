package com.gitnote.backend.dto;

import lombok.Data;

public class UserPresetRequests {

    @Data
    public static class EmailNotification {
        private String email;
        private Boolean enabled;
    }

    @Data
    public static class ReportStyle {
        private String reportStyle;
    }

    @Data
    public static class ReportFrequency {
        private String reportFrequency;
    }
}
