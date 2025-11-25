package com.gitnote.backend.dto;

import lombok.Data;

@Data
public class GitHubUserInfo {
    private String login;
    private Long id;
    private String name;
    private String email;
    private String avatarUrl;
    private String bio;
    private String location;
    private String company;
    private Integer publicRepos;
    private Integer followers;
    private Integer following;
    private String createdAt;
}
