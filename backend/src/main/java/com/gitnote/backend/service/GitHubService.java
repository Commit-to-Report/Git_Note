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
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * GitHubService
 * - GitHub API 연동, 액세스 토큰 관리 · 사용자, 리포지토리, 커밋 데이터 수집 서비스
 */
@Service
public class GitHubService {

    // 깃허브 OAuth Client ID 및 Secret은 애플리케이션 환경설정(@Value 주입)
    @Value("${github.client.id}")
    private String clientId;

    @Value("${github.client.secret}")
    private String clientSecret;

    // WebClient 인스턴스 (재활용)
    private final WebClient webClient;

    /**
     * 생성자 - 웹클라이언트 + HTTP 타임아웃 설정
     */
    public GitHubService() {
        // 네트워크 타임아웃(연결/응답, Read/Write) 설정
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

    /**
     * GitHub OAuth 인증 코드를 사용해 액세스 토큰을 발급받음
     * @param code 인증 코드
     * @param redirectUri 리다이렉트 URI (인증 요청 시 사용한 것과 동일해야 함)
     * @return 액세스 토큰(문자열). 실패 시 null
     */
    public String getAccessToken(String code, String redirectUri) {
        final String tokenUrl = "https://github.com/login/oauth/access_token";
        // 요청 데이터 셋업 (GitHub OAuth API 요구사항: redirect_uri 필수)
        Map<String, Object> params = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "redirect_uri", redirectUri
        );

        Map<String, Object> response = null;
        try {
            response = webClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            System.err.println("[getAccessToken] Exception: " + e.getMessage());
            // 실패 시 null 반환
        }
        return response != null ? (String) response.get("access_token") : null;
    }

    /**
     * 액세스 토큰으로 사용자 정보를 조회
     * @param accessToken 사용자 액세스 토큰
     * @return GitHubUserInfo(사용자 정보), 실패 시 null
     */
    public GitHubUserInfo getUserInfo(String accessToken) {
        final String userUrl = "https://api.github.com/user";

        try {
            Map<String, Object> response = webClient.get()
                    .uri(userUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return null;

            // 타입 캐스팅 안전성 보장 (Null 처리)
            GitHubUserInfo userInfo = new GitHubUserInfo();
            userInfo.setLogin((String) response.get("login"));
            userInfo.setId(response.get("id") instanceof Number ? ((Number) response.get("id")).longValue() : null);
            userInfo.setName((String) response.get("name"));
            userInfo.setEmail((String) response.get("email"));
            userInfo.setAvatarUrl((String) response.get("avatar_url"));
            userInfo.setBio((String) response.get("bio"));
            userInfo.setLocation((String) response.get("location"));
            userInfo.setCompany((String) response.get("company"));
            userInfo.setPublicRepos(response.get("public_repos") instanceof Number ? ((Number) response.get("public_repos")).intValue() : null);
            userInfo.setFollowers(response.get("followers") instanceof Number ? ((Number) response.get("followers")).intValue() : null);
            userInfo.setFollowing(response.get("following") instanceof Number ? ((Number) response.get("following")).intValue() : null);
            userInfo.setCreatedAt((String) response.get("created_at"));

            return userInfo;
        } catch (Exception e) {
            System.err.println("[getUserInfo] Exception: " + e.getMessage());
            return null;
        }
    }

    /**
     * 주어진 액세스 토큰을 만료(폐기) 처리함 (GitHub logout 등)
     * @param accessToken 폐기할 액세스 토큰
     * @return 성공 여부(true/false)
     */
    public boolean revokeToken(String accessToken) {
        try {
            String revokeUrl = String.format("https://api.github.com/applications/%s/token", clientId);
            // Basic Auth 필요 (clientId:clientSecret Base64 인코드)
            String credentials = clientId + ":" + clientSecret;
            String base64Credentials = Base64.getEncoder().encodeToString(credentials.getBytes());

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
            System.err.println("[revokeToken] Failed to revoke token: " + e.getMessage());
            return false;
        }
    }

    /**
     * 깃허브 API RateLimit 상태 조회
     * @param accessToken 사용자 액세스 토큰
     * @return Map(레이트 리밋 정보 or 에러)
     */
    public Map<String, Object> getRateLimit(String accessToken) {
        final String rateLimitUrl = "https://api.github.com/rate_limit";
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
            System.err.println("[getRateLimit] Failed: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * 사용자 리포지토리 목록 조회
     * @param accessToken 사용자 토큰
     * @param username GitHub 닉네임
     * @return GitHubRepository 목록 (없을 때 빈 리스트)
     */
    public List<GitHubRepository> getRepositories(String accessToken, String username) {
        // 한 번에 최대 100개(per_page) - 페이지네이션 필요 시 개선!
        final String reposUrl = String.format("https://api.github.com/users/%s/repos?per_page=100&sort=updated", username);

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

            long elapsed = System.currentTimeMillis() - startTime;
            int count = repositories != null ? repositories.size() : 0;
            System.out.printf("[getRepositories] %s - %d repositories fetched in %d ms%n", username, count, elapsed);

            return repositories != null ? repositories : Collections.emptyList();
        } catch (WebClientResponseException e) {
            System.err.println("[getRepositories] GitHub API error: " + e.getStatusCode() + " Body: " + e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 403) {
                throw new RuntimeException("GitHub API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
            } else if (e.getStatusCode().value() == 401) {
                throw new RuntimeException("인증이 만료되었습니다. 다시 로그인해주세요.");
            }
            throw new RuntimeException("리포지토리 목록을 불러올 수 없습니다: " + e.getMessage());
        } catch (Exception e) {
            System.err.printf("[getRepositories] Unexpected error: %s - %s%n", e.getClass().getName(), e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("서버 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 특정 리포지토리, 날짜 범위 별 커밋 목록 조회
     * @param accessToken 사용자 인증 토큰
     * @param owner 리포지토리 소유자
     * @param repo 리포지토리 명
     * @param since 조회 시작일
     * @param until 조회 종료일(포함)
     * @return 커밋 목록 (없으면 빈 리스트)
     */
    public List<GitHubCommit> getCommitsByDateRange(String accessToken, String owner, String repo,
                                                    LocalDate since, LocalDate until) {
        // ISO_DATE_TIME 포맷 예: 2024-06-09T00:00:00
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        String sinceParam = since.atStartOfDay().format(formatter);
        String untilParam = until.atTime(23, 59, 59).format(formatter);

        final String commitsUrl = String.format(
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

            return commits != null ? commits : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[getCommitsByDateRange] Failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 커밋 상세 정보(변경파일 포함) 단일 건 조회
     * @param accessToken 인증 토큰
     * @param owner 리포지토리 소유자
     * @param repo 리포지토리 명
     * @param sha 커밋 SHA
     * @return GitHubCommit 객체 또는 null
     */
    public GitHubCommit getCommitDetails(String accessToken, String owner, String repo, String sha) {
        final String commitUrl = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, sha);

        try {
            return webClient.get()
                    .uri(commitUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(GitHubCommit.class)
                    .block();
        } catch (Exception e) {
            System.err.println("[getCommitDetails] Failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 커밋 목록을 리포트용 텍스트로 변환 (변경파일, 통계 포함)
     * @param commits 커밋 목록
     * @param repositoryName 리포지토리 명
     * @param since 시작일
     * @param until 종료일
     * @return 문자열 리포트(테이블 형식)
     */
    public String exportCommitsAsText(List<GitHubCommit> commits, String repositoryName,
                                      LocalDate since, LocalDate until) {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(80)).append("\n")
            .append(String.format("커밋 리포트: %s\n", repositoryName))
            .append(String.format("기간: %s ~ %s\n", since, until))
            .append(String.format("총 커밋 수: %d\n", commits.size()))
            .append("=".repeat(80)).append("\n\n");

        int i = 1;
        for (GitHubCommit commit : commits) {
            sb.append(String.format("[%d] 커밋 #%d\n", i, i))
              .append("-".repeat(80)).append("\n")
              .append(String.format("SHA: %s\n", commit.getSha()));

            // 커밋 메타 및 작성자
            if (commit.getCommit() != null) {
                sb.append(String.format("메시지: %s\n", commit.getCommit().getMessage()));
                if (commit.getCommit().getAuthor() != null) {
                    sb.append(String.format("작성자: %s <%s>\n",
                            commit.getCommit().getAuthor().getName(),
                            commit.getCommit().getAuthor().getEmail()))
                      .append(String.format("작성일: %s\n", commit.getCommit().getAuthor().getDate()));
                }
            }

            sb.append(String.format("URL: %s\n", commit.getHtmlUrl()));

            // 변경 파일 정보
            List<GitHubCommit.FileChange> files = commit.getFiles();
            if (files != null && !files.isEmpty()) {
                sb.append(String.format("\n변경된 파일 (%d개):\n", files.size()));
                files.forEach(file -> sb.append(String.format("  - %s (%s) [+%d/-%d]\n",
                        file.getFilename(),
                        file.getStatus(),
                        Optional.ofNullable(file.getAdditions()).orElse(0),
                        Optional.ofNullable(file.getDeletions()).orElse(0)
                )));
            }

            // 변경 통계 정보
            if (commit.getStats() != null) {
                sb.append(String.format("\n통계: +%d / -%d (총 %d 변경)\n",
                        Optional.ofNullable(commit.getStats().getAdditions()).orElse(0),
                        Optional.ofNullable(commit.getStats().getDeletions()).orElse(0),
                        Optional.ofNullable(commit.getStats().getTotal()).orElse(0)
                ));
            }

            sb.append("\n");
            i++;
        }
        sb.append("=".repeat(80)).append("\n")
            .append("리포트 종료\n")
            .append("=".repeat(80)).append("\n");

        return sb.toString();
    }
}
