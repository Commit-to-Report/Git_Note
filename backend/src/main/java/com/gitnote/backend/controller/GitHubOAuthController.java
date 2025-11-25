package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubUserInfo;
import com.gitnote.backend.service.GitHubService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

@RestController
public class GitHubOAuthController {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.redirect.uri}")
    private String redirectUri;

    private final GitHubService gitHubService;

    public GitHubOAuthController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @GetMapping("/api/github/client-id")
    public ResponseEntity<Map<String, String>> getClientId() {
        Map<String, String> response = new HashMap<>();
        response.put("clientId", clientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/github/user")
    public ResponseEntity<?> getUserInfo(@RequestParam("code") String code) {
        try {
            String accessToken = gitHubService.getAccessToken(code);

            if (accessToken == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to get access token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            GitHubUserInfo userInfo = gitHubService.getUserInfo(accessToken);

            if (userInfo == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to get user information");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/api/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, String> response = new HashMap<>();

        if (session != null) {
            String accessToken = (String) session.getAttribute("accessToken");
            if (accessToken != null) {
                gitHubService.revokeToken(accessToken);
            }
            session.invalidate();
        }

        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }
}
