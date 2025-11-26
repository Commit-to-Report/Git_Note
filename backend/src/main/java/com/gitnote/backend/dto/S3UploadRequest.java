package com.gitnote.backend.dto;

public class S3UploadRequest {
    private String repositoryName;
    private String startDate;
    private String endDate;
    private String content;

    // Getter & Setter
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}