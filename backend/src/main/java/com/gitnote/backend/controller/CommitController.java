package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubCommit;
import com.gitnote.backend.dto.GitHubRepository;
import com.gitnote.backend.service.GitHubService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
public class CommitController {

    private final GitHubService gitHubService;

    public CommitController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @GetMapping("/api/github/rate-limit")
    public ResponseEntity<?> getRateLimit(HttpSession session) {
        try {
            String accessToken = (String) session.getAttribute("accessToken");
            if (accessToken == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated. Please login first"));

            return ResponseEntity.ok(gitHubService.getRateLimit(accessToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to fetch rate limit: " + e.getMessage()));
        }
    }

    @GetMapping("/api/github/repositories")
    public ResponseEntity<?> getRepositories(HttpSession session) {
        try {
            String accessToken = (String) session.getAttribute("accessToken");
            String username = (String) session.getAttribute("username");
            if (accessToken == null || username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다.", "needLogin", "true"));

            List<GitHubRepository> repositories = gitHubService.getRepositories(accessToken, username);
            return ResponseEntity.ok(Map.of("repositories", repositories, "count", repositories.size()));
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            if (e.getMessage().contains("rate limit")) return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
            if (e.getMessage().contains("인증") || e.getMessage().contains("로그인")) {
                error.put("needLogin", "true");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "서버 오류: " + e.getMessage()));
        }
    }

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
            if (accessToken == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated. Please login first"));

            LocalDate since = LocalDate.parse(sinceStr);
            LocalDate until = LocalDate.parse(untilStr);
            List<GitHubCommit> commits = gitHubService.getCommitsByDateRange(accessToken, owner, repo, since, until);

            if (includeDetails) {
                List<GitHubCommit> detailedCommits = new ArrayList<>();
                for (GitHubCommit commit : commits) {
                    GitHubCommit detailed = gitHubService.getCommitDetails(accessToken, owner, repo, commit.getSha());
                    if (detailed != null) detailedCommits.add(detailed);
                }
                commits = detailedCommits;
            }

            return ResponseEntity.ok(Map.of(
                    "commits", commits,
                    "count", commits.size(),
                    "repository", owner + "/" + repo,
                    "period", Map.of("since", sinceStr, "until", untilStr)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to fetch commits: " + e.getMessage()));
        }
    }
}
