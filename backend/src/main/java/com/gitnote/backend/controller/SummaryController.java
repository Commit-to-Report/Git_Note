package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubCommit;
import com.gitnote.backend.service.GeminiApiService;
import com.gitnote.backend.service.GitHubService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final GitHubService gitHubService;
    private final GeminiApiService geminiApiService;

    public SummaryController(GitHubService gitHubService, GeminiApiService geminiApiService) {
        this.gitHubService = gitHubService;
        this.geminiApiService = geminiApiService;
    }

    @PostMapping("/commits")
    public ResponseEntity<?> summarizeCommits(
            @RequestBody Map<String, String> requestBody,
            HttpSession session
    ) {
        try {
            System.out.println("==== [CommitSummary] 요청 도착 ====");

            String owner = requestBody.get("owner");
            String repo = requestBody.get("repo");
            String sinceStr = requestBody.get("since");
            String untilStr = requestBody.get("until");

            System.out.println("[CommitSummary] owner=" + owner + ", repo=" + repo);
            System.out.println("[CommitSummary] since=" + sinceStr + ", until=" + untilStr);

            String accessToken = getAccessToken(session);

            List<GitHubCommit> commits = fetchCommits(accessToken, owner, repo, sinceStr, untilStr);

            if (commits.isEmpty()) {
                System.out.println("[CommitSummary] ⚠ 커밋 없음");
                return ResponseEntity.ok(Map.of(
                        "summary", "선택된 기간에 커밋이 없습니다.",
                        "commits", List.of()
                ));
            }

            String commitText = buildCommitText(commits);

            System.out.println("==== [CommitSummary] GitHub Commit Raw Text ====");
            System.out.println(commitText);

            // Gemini 요약 요청
            System.out.println("[CommitSummary] Gemini 요약 요청 중...");
            String summary = geminiApiService.generateContent(commitText);
            System.out.println("[CommitSummary] Gemini 요약 완료!");

            return ResponseEntity.ok(Map.of(
                    "summary", summary,
                    "commitCount", commits.size()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to summarize commits: " + e.getMessage()));
        }
    }

    private String getAccessToken(HttpSession session) {
        String accessToken = (String) session.getAttribute("accessToken");
        if (accessToken == null) {
            System.out.println("[CommitSummary] ❌ GitHub 로그인 안 됨");
            throw new RuntimeException("Not authenticated. Please login first.");
        }
        return accessToken;
    }

    private List<GitHubCommit> fetchCommits(String accessToken, String owner, String repo, String sinceStr, String untilStr) {
        LocalDate since = LocalDate.parse(sinceStr);
        LocalDate until = LocalDate.parse(untilStr);
        System.out.println("[CommitSummary] GitHub 커밋 불러오는 중...");
        return gitHubService.getCommitsByDateRange(accessToken, owner, repo, since, until);
    }

    private String buildCommitText(List<GitHubCommit> commits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commits.size(); i++) {
            GitHubCommit c = commits.get(i);
            String message = c.getCommit() != null ? c.getCommit().getMessage() : "No message";
            String author = c.getCommit() != null && c.getCommit().getAuthor() != null ? c.getCommit().getAuthor().getName() : "Unknown";
            String date = c.getCommit() != null && c.getCommit().getAuthor() != null ? c.getCommit().getAuthor().getDate() : "Unknown";
            sb.append("[").append(i + 1).append("] ").append(c.getSha().substring(0, 7)).append("\n")
                    .append("Message: ").append(message).append("\n")
                    .append("Author: ").append(author).append("\n")
                    .append("Date: ").append(date).append("\n")
                    .append("----------------------\n");
        }
        return sb.toString();
    }
}
