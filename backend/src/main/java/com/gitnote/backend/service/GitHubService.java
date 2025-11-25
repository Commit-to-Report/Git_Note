package com.gitnote.backend.service;

import com.gitnote.backend.dto.GitHubCommit;
import com.gitnote.backend.dto.GitHubRepository;
import com.gitnote.backend.dto.GitHubUserInfo;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class GitHubService {

    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    private final WebClient webClient;

    public GitHubService() {
        // HTTP 클라이언트에 타임아웃 설정
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 연결 타임아웃: 10초
                .responseTimeout(Duration.ofSeconds(30)) // 응답 타임아웃: 30초
                .doOnConnected(conn -> 
                    conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
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

    /**
     * GitHub API Rate Limit 정보를 가져옵니다.
     */
    public Map<String, Object> getRateLimit(String accessToken) {
        String rateLimitUrl = "https://api.github.com/rate_limit";

        try {
            Map<String, Object> response = webClient.get()
                    .uri(rateLimitUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return response;
        } catch (Exception e) {
            System.err.println("Failed to fetch rate limit: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * 사용자의 모든 리포지토리 목록을 가져옵니다.
     */
    public List<GitHubRepository> getRepositories(String accessToken, String username) {
        String reposUrl = String.format("https://api.github.com/users/%s/repos?per_page=100&sort=updated", username);

        System.out.println("=".repeat(60));
        System.out.println("Fetching repositories for user: " + username);
        System.out.println("URL: " + reposUrl);
        long startTime = System.currentTimeMillis();

        try {
            List<GitHubRepository> repositories = webClient.get()
                    .uri(reposUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToFlux(GitHubRepository.class)
                    .collectList()
                    .block();

            long endTime = System.currentTimeMillis();
            int count = repositories != null ? repositories.size() : 0;
            
            System.out.println("✓ Successfully fetched " + count + " repositories in " + (endTime - startTime) + " ms");
            System.out.println("=".repeat(60));

            return repositories != null ? repositories : new ArrayList<>();
        } catch (WebClientResponseException e) {
            System.err.println("✗ GitHub API error: " + e.getStatusCode());
            System.err.println("Response: " + e.getResponseBodyAsString());
            System.err.println("=".repeat(60));
            
            if (e.getStatusCode().value() == 403) {
                throw new RuntimeException("GitHub API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
            } else if (e.getStatusCode().value() == 401) {
                throw new RuntimeException("인증이 만료되었습니다. 다시 로그인해주세요.");
            }
            
            throw new RuntimeException("리포지토리 목록을 불러올 수 없습니다: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("✗ Unexpected error: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
            System.err.println("=".repeat(60));
            throw new RuntimeException("서버 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 특정 리포지토리의 특정 날짜 범위 커밋을 가져옵니다.
     */
    public List<GitHubCommit> getCommitsByDateRange(String accessToken, String owner, String repo, 
                                                      LocalDate since, LocalDate until) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        String sinceParam = since.atStartOfDay().format(formatter);
        String untilParam = until.atTime(23, 59, 59).format(formatter);
        
        String commitsUrl = String.format(
            "https://api.github.com/repos/%s/%s/commits?since=%s&until=%s&per_page=100",
            owner, repo, sinceParam, untilParam
        );

        try {
            List<GitHubCommit> commits = webClient.get()
                    .uri(commitsUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToFlux(GitHubCommit.class)
                    .collectList()
                    .block();

            return commits != null ? commits : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Failed to fetch commits: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 특정 커밋의 상세 정보를 가져옵니다 (변경된 파일 포함).
     */
    public GitHubCommit getCommitDetails(String accessToken, String owner, String repo, String sha) {
        String commitUrl = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, sha);

        try {
            return webClient.get()
                    .uri(commitUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(GitHubCommit.class)
                    .block();
        } catch (Exception e) {
            System.err.println("Failed to fetch commit details: " + e.getMessage());
            return null;
        }
    }

    /**
     * 커밋 목록을 텍스트 형식으로 변환합니다.
     */
    public String exportCommitsAsText(List<GitHubCommit> commits, String repositoryName, 
                                      LocalDate since, LocalDate until) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=".repeat(80)).append("\n");
        sb.append(String.format("커밋 리포트: %s\n", repositoryName));
        sb.append(String.format("기간: %s ~ %s\n", since, until));
        sb.append(String.format("총 커밋 수: %d\n", commits.size()));
        sb.append("=".repeat(80)).append("\n\n");

        for (int i = 0; i < commits.size(); i++) {
            GitHubCommit commit = commits.get(i);
            
            sb.append(String.format("[%d] 커밋 #%d\n", i + 1, i + 1));
            sb.append("-".repeat(80)).append("\n");
            sb.append(String.format("SHA: %s\n", commit.getSha()));
            
            if (commit.getCommit() != null) {
                sb.append(String.format("메시지: %s\n", commit.getCommit().getMessage()));
                
                if (commit.getCommit().getAuthor() != null) {
                    sb.append(String.format("작성자: %s <%s>\n", 
                        commit.getCommit().getAuthor().getName(),
                        commit.getCommit().getAuthor().getEmail()));
                    sb.append(String.format("작성일: %s\n", commit.getCommit().getAuthor().getDate()));
                }
            }
            
            sb.append(String.format("URL: %s\n", commit.getHtmlUrl()));
            
            // 변경된 파일 정보
            if (commit.getFiles() != null && !commit.getFiles().isEmpty()) {
                sb.append(String.format("\n변경된 파일 (%d개):\n", commit.getFiles().size()));
                for (GitHubCommit.FileChange file : commit.getFiles()) {
                    sb.append(String.format("  - %s (%s) [+%d/-%d]\n",
                        file.getFilename(),
                        file.getStatus(),
                        file.getAdditions() != null ? file.getAdditions() : 0,
                        file.getDeletions() != null ? file.getDeletions() : 0
                    ));
                }
            }
            
            // 통계 정보
            if (commit.getStats() != null) {
                sb.append(String.format("\n통계: +%d / -%d (총 %d 변경)\n",
                    commit.getStats().getAdditions() != null ? commit.getStats().getAdditions() : 0,
                    commit.getStats().getDeletions() != null ? commit.getStats().getDeletions() : 0,
                    commit.getStats().getTotal() != null ? commit.getStats().getTotal() : 0
                ));
            }
            
            sb.append("\n");
        }
        
        sb.append("=".repeat(80)).append("\n");
        sb.append("리포트 종료\n");
        sb.append("=".repeat(80)).append("\n");
        
        return sb.toString();
    }
}
