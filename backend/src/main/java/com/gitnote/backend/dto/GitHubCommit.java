package com.gitnote.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GitHubCommit {
    private String sha;
    private CommitInfo commit;
    
    @JsonProperty("html_url")
    private String htmlUrl;
    
    private Author author;
    
    // 커밋 상세 정보를 위한 추가 필드
    private List<FileChange> files;
    private CommitStats stats;

    public static class CommitInfo {
        private String message;
        private Author author;
        private Committer committer;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Author getAuthor() {
            return author;
        }

        public void setAuthor(Author author) {
            this.author = author;
        }

        public Committer getCommitter() {
            return committer;
        }

        public void setCommitter(Committer committer) {
            this.committer = committer;
        }
    }

    public static class Author {
        private String name;
        private String email;
        private String date;
        
        @JsonProperty("login")
        private String login;
        
        @JsonProperty("avatar_url")
        private String avatarUrl;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }

    public static class Committer {
        private String name;
        private String email;
        private String date;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    public static class FileChange {
        private String filename;
        private String status;
        private Integer additions;
        private Integer deletions;
        private Integer changes;
        private String patch;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getAdditions() {
            return additions;
        }

        public void setAdditions(Integer additions) {
            this.additions = additions;
        }

        public Integer getDeletions() {
            return deletions;
        }

        public void setDeletions(Integer deletions) {
            this.deletions = deletions;
        }

        public Integer getChanges() {
            return changes;
        }

        public void setChanges(Integer changes) {
            this.changes = changes;
        }

        public String getPatch() {
            return patch;
        }

        public void setPatch(String patch) {
            this.patch = patch;
        }
    }

    public static class CommitStats {
        private Integer additions;
        private Integer deletions;
        private Integer total;

        public Integer getAdditions() {
            return additions;
        }

        public void setAdditions(Integer additions) {
            this.additions = additions;
        }

        public Integer getDeletions() {
            return deletions;
        }

        public void setDeletions(Integer deletions) {
            this.deletions = deletions;
        }

        public Integer getTotal() {
            return total;
        }

        public void setTotal(Integer total) {
            this.total = total;
        }
    }

    // Main class Getters and Setters
    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public CommitInfo getCommit() {
        return commit;
    }

    public void setCommit(CommitInfo commit) {
        this.commit = commit;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public List<FileChange> getFiles() {
        return files;
    }

    public void setFiles(List<FileChange> files) {
        this.files = files;
    }

    public CommitStats getStats() {
        return stats;
    }

    public void setStats(CommitStats stats) {
        this.stats = stats;
    }
}

