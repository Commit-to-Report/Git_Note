package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubCommit;
import com.gitnote.backend.dto.GitHubRepository;
import com.gitnote.backend.dto.GitHubUserInfo;
import com.gitnote.backend.service.GitHubService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

            if (userInfo == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to get user information");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            // 세션에 저장
            session.setAttribute("accessToken", accessToken);
            session.setAttribute("username", userInfo.getLogin());

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error: " + e.getMessage()));
        }
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

    /**
     * GitHub API Rate Limit 상태를 확인합니다.
     */
    @GetMapping("/api/github/rate-limit")
    public ResponseEntity<?> getRateLimit(HttpSession session) {
        try {
            String accessToken = (String) session.getAttribute("accessToken");

            if (accessToken == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Not authenticated. Please login first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            Map<String, Object> rateLimit = gitHubService.getRateLimit(accessToken);
            return ResponseEntity.ok(rateLimit);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch rate limit: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 사용자의 리포지토리 목록을 가져옵니다.
     */
    @GetMapping("/api/github/repositories")
    public ResponseEntity<?> getRepositories(HttpSession session) {
        System.out.println("\n>>> API Request: GET /api/github/repositories");
        
        try {
            String accessToken = (String) session.getAttribute("accessToken");
            String username = (String) session.getAttribute("username");

            System.out.println("Session - AccessToken: " + (accessToken != null ? "존재" : "없음"));
            System.out.println("Session - Username: " + (username != null ? username : "없음"));

            if (accessToken == null || username == null) {
                System.out.println("✗ Authentication failed - redirecting to login");
                Map<String, String> error = new HashMap<>();
                error.put("error", "로그인이 필요합니다.");
                error.put("needLogin", "true");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            List<GitHubRepository> repositories = gitHubService.getRepositories(accessToken, username);

            // 리포지토리 데이터 검증
            if (repositories != null && !repositories.isEmpty()) {
                System.out.println("✓ Fetched " + repositories.size() + " repositories");
                
                // 첫 번째 리포지토리 정보 출력 (디버깅용)
                if (repositories.size() > 0) {
                    GitHubRepository firstRepo = repositories.get(0);
                    System.out.println("  Sample: name=" + firstRepo.getName() + ", fullName=" + firstRepo.getFullName());
                }
            } else {
                System.out.println("⚠️ No repositories found");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("repositories", repositories);
            response.put("count", repositories.size());

            System.out.println("✓ Response sent with " + repositories.size() + " repositories\n");
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            System.err.println("✗ RuntimeException: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            
            if (e.getMessage().contains("rate limit") || e.getMessage().contains("요청 한도")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
            } else if (e.getMessage().contains("인증") || e.getMessage().contains("로그인")) {
                error.put("needLogin", "true");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
            
        } catch (Exception e) {
            System.err.println("✗ Unexpected Exception: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, String> error = new HashMap<>();
            error.put("error", "서버 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 특정 리포지토리의 특정 날짜 범위 커밋을 가져옵니다.
     * @param owner 리포지토리 소유자
     * @param repo 리포지토리 이름
     * @param sinceStr 시작 날짜
     * @param untilStr 종료 날짜
     * @param includeDetails 상세 정보 포함 여부
     * @param session 세션
     * @return 커밋 목록
     */
    @GetMapping("/api/github/commits")
    public ResponseEntity<?> getCommits(
            @RequestParam("owner") String owner,
            @RequestParam("repo") String repo,
            @RequestParam("since") String sinceStr,
            @RequestParam("until") String untilStr,
            @RequestParam(value = "includeDetails", defaultValue = "false") boolean includeDetails,
            HttpSession session) {
        try {
            String accessToken = (String) session.getAttribute("accessToken");

            if (accessToken == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Not authenticated. Please login first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            LocalDate since = LocalDate.parse(sinceStr);
            LocalDate until = LocalDate.parse(untilStr);

            List<GitHubCommit> commits = gitHubService.getCommitsByDateRange(
                accessToken, owner, repo, since, until
            );

            // 상세 정보가 필요한 경우 각 커밋의 상세 정보를 가져옵니다
            if (includeDetails) {
                List<GitHubCommit> detailedCommits = new ArrayList<>();
                for (GitHubCommit commit : commits) {
                    GitHubCommit detailed = gitHubService.getCommitDetails(
                        accessToken, owner, repo, commit.getSha()
                    );
                    if (detailed != null) {
                        detailedCommits.add(detailed);
                    }
                }
                commits = detailedCommits;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("commits", commits);
            response.put("count", commits.size());
            response.put("repository", owner + "/" + repo);
            response.put("period", Map.of("since", sinceStr, "until", untilStr));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch commits: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 커밋 내용을 텍스트 파일로 내보냅니다.
     */
    @GetMapping("/api/github/commits/export")
    public ResponseEntity<?> exportCommits(
            @RequestParam("owner") String owner,
            @RequestParam("repo") String repo,
            @RequestParam("since") String sinceStr,
            @RequestParam("until") String untilStr,
            HttpSession session) {
        try {
            String accessToken = (String) session.getAttribute("accessToken");

            if (accessToken == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Not authenticated. Please login first.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            LocalDate since = LocalDate.parse(sinceStr);
            LocalDate until = LocalDate.parse(untilStr);

            // 상세 정보를 포함한 커밋 목록 가져오기
            List<GitHubCommit> commits = gitHubService.getCommitsByDateRange(
                accessToken, owner, repo, since, until
            );

            // 각 커밋의 상세 정보 가져오기
            List<GitHubCommit> detailedCommits = new ArrayList<>();
            for (GitHubCommit commit : commits) {
                GitHubCommit detailed = gitHubService.getCommitDetails(
                    accessToken, owner, repo, commit.getSha()
                );
                if (detailed != null) {
                    detailedCommits.add(detailed);
                }
            }

            // 텍스트로 변환
            String textContent = gitHubService.exportCommitsAsText(
                detailedCommits, owner + "/" + repo, since, until
            );

            // 파일명 생성
            String filename = String.format("commits_%s_%s_to_%s.txt", 
                repo, sinceStr, untilStr);

            // 텍스트 파일로 응답
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", filename);

            return ResponseEntity.ok()
                .headers(headers)
                .body(textContent);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to export commits: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
