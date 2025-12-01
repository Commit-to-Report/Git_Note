package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubUserInfo;
import com.gitnote.backend.service.GitHubService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class GitHubOAuthController {

    // GitHub OAuth Client ID (application.properties에서 주입)
    @Value("${github.client.id}")
    private String clientId;

    // GitHub OAuth Redirect URI
    @Value("${github.redirect.uri}")
    private String redirectUri;

    private final GitHubService gitHubService;

    // 생성자 주입 방식으로 서비스 연결
    public GitHubOAuthController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    /**
     * 프론트엔드에서 OAuth 인증을 위해 필요한 GitHub Client ID 반환
     * @return clientId
     */
    @GetMapping("/api/github/client-id")
    public ResponseEntity<Map<String, String>> getClientId() {
        Map<String, String> response = new HashMap<>();
        response.put("clientId", clientId);
        return ResponseEntity.ok(response);
    }

    /**
     * 코드로 accessToken을 받고 사용자 정보를 세션에 저장하여 반환
     * @param code GitHub OAuth 인가 코드
     * @param session HttpSession
     */
    @GetMapping("/api/github/user")
    public ResponseEntity<?> getUserInfo(
            @RequestParam("code") String code,
            HttpSession session
    ) {
        try {
            // 1. 액세스 토큰 발급 (redirect_uri는 application.properties의 값 사용)
            String accessToken = gitHubService.getAccessToken(code, redirectUri);
            if (accessToken == null) {
                // 인증 실패: accessToken 획득 불가
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Failed to get access token"));
            }

            // 2. 사용자 정보 요청
            GitHubUserInfo userInfo = gitHubService.getUserInfo(accessToken);
            if (userInfo == null) {
                // 인증 실패: 사용자 정보 획득 불가
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Failed to get user information"));
            }

            // 3. 세션에 사용자 정보 저장
            session.setAttribute("accessToken", accessToken);
            session.setAttribute("username", userInfo.getLogin());
            session.setAttribute("email", userInfo.getEmail());

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            // 예외 처리: 내부 서버 오류
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error: " + e.getMessage()));
        }
    }

    /**
     * 현재 로그인된 세션 정보를 반환
     * (프론트엔드에서 로그인 유지 및 사용자 정보 확인 용도)
     */
    @GetMapping("/api/user/session")
    public ResponseEntity<?> checkSession(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            // 로그인 세션 없음
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No active session"));
        }
        String email = (String) session.getAttribute("email");
        Map<String, String> response = new HashMap<>();
        response.put("username", username);
        response.put("email", email != null ? email : "");
        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃 처리 (accessToken revoke 및 세션 무효화)
     */
    @PostMapping("/api/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // 기존 세션만 조회
        if (session != null) {
            // 액세스 토큰이 남아있다면 GitHub에 토큰 revoke 요청
            String accessToken = (String) session.getAttribute("accessToken");
            if (accessToken != null) {
                gitHubService.revokeToken(accessToken);
            }
            session.invalidate(); // 세션 무효화
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
