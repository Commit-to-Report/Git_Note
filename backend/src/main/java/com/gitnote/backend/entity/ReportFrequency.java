package com.gitnote.backend.entity;

public enum ReportFrequency {
    DAILY("일일보고"),
    WEEKLY("주간보고"),
    MONTHLY("월간보고");

    private final String description;

    ReportFrequency(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
