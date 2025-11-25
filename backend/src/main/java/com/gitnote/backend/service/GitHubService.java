package com.gitnote.backend.service;

import com.gitnote.backend.dto.GitHubUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class GitHubService {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    private final WebClient webClient;

    public GitHubService() {
        this.webClient = WebClient.builder().build();
    }

    public String getAccessToken(String code) {
        String tokenUrl = "https://github.com/login/oauth/access_token";

        Map<String, Object> response = webClient.post()
                .uri(tokenUrl)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(Map.of(
                        "client_id", clientId,
                        "client_secret", clientSecret,
                        "code", code
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return response != null ? (String) response.get("access_token") : null;
    }

    public GitHubUserInfo getUserInfo(String accessToken) {
        String userUrl = "https://api.github.com/user";

        Map<String, Object> response = webClient.get()
                .uri(userUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            return null;
        }

        GitHubUserInfo userInfo = new GitHubUserInfo();
        userInfo.setLogin((String) response.get("login"));
        userInfo.setId(((Number) response.get("id")).longValue());
        userInfo.setName((String) response.get("name"));
        userInfo.setEmail((String) response.get("email"));
        userInfo.setAvatarUrl((String) response.get("avatar_url"));
        userInfo.setBio((String) response.get("bio"));
        userInfo.setLocation((String) response.get("location"));
        userInfo.setCompany((String) response.get("company"));
        userInfo.setPublicRepos((Integer) response.get("public_repos"));
        userInfo.setFollowers((Integer) response.get("followers"));
        userInfo.setFollowing((Integer) response.get("following"));
        userInfo.setCreatedAt((String) response.get("created_at"));

        return userInfo;
    }

    public boolean revokeToken(String accessToken) {
        try {
            String revokeUrl = String.format("https://api.github.com/applications/%s/token", clientId);

            // Basic Auth: base64(client_id:client_secret)
            String credentials = clientId + ":" + clientSecret;
            String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());

            webClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(revokeUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Credentials)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(Map.of("access_token", accessToken))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            return true;
        } catch (Exception e) {
            System.err.println("Failed to revoke token: " + e.getMessage());
            return false;
        }
    }
}
