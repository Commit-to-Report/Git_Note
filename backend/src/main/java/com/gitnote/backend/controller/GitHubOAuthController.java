package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubUserInfo;
import com.gitnote.backend.service.GitHubService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(Map.of("clientId", clientId));
    }

    @GetMapping("/api/github/user")
    public ResponseEntity<?> getUserInfo(@RequestParam("code") String code, HttpSession session) {
        try {
            String accessToken = gitHubService.getAccessToken(code);
            if (accessToken == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Failed to get access token"));

            GitHubUserInfo userInfo = gitHubService.getUserInfo(accessToken);
            if (userInfo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Failed to get user information"));

            session.setAttribute("accessToken", accessToken);
            session.setAttribute("username", userInfo.getLogin());
            session.setAttribute("email", userInfo.getEmail());
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/user/session")
    public ResponseEntity<?> checkSession(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No active session"));
        }
        return ResponseEntity.ok(Map.of(
            "username", username,
            "email", session.getAttribute("email") != null ? session.getAttribute("email") : ""
        ));
    }

    @PostMapping("/api/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String accessToken = (String) session.getAttribute("accessToken");
            if (accessToken != null) gitHubService.revokeToken(accessToken);
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
