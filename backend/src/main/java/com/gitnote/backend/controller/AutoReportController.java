package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubCommit;
import com.gitnote.backend.entity.UserPreset;
import com.gitnote.backend.service.DDBReportService;
import com.gitnote.backend.service.EmailService;
import com.gitnote.backend.service.GeminiApiService;
import com.gitnote.backend.service.GitHubService;
import com.gitnote.backend.service.UserPresetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 자동 리포트 생성을 위한 Controller
 * Lambda에서 호출할 수 있는 API 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/auto-report")
@RequiredArgsConstructor
public class AutoReportController {

    private final GitHubService gitHubService;
    private final GeminiApiService geminiApiService;
    private final DDBReportService reportService;
    private final UserPresetService userPresetService;
    private final EmailService emailService;

    /**
     * 리포트 생성 API
     *
     * Request Body:
     * {
     *   "accessToken": "github-access-token",
     *   "repository": "owner/repo",
     *   "since": "2024-01-01",
     *   "until": "2024-01-31",
     *   "reportStyle": "summary|detailed|statistics",
     *   "userId": "github-username"
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "reportId": "owner/repo",
     *   "timestamp": "2024-01-31T12:00:00Z",
     *   "message": "Report generated successfully"
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateReport(@RequestBody Map<String, String> request) {
        try {
            // 요청 파라미터 추출
            String accessToken = request.get("accessToken");
            String repository = request.get("repository");
            String sinceStr = request.get("since");
            String untilStr = request.get("until");
            String reportStyle = request.getOrDefault("reportStyle", "summary");
            String userId = request.get("userId");

            // 필수 파라미터 검증
            if (accessToken == null || repository == null || sinceStr == null ||
                untilStr == null || userId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Missing required parameters: accessToken, repository, since, until, userId"
                ));
            }

            // repository 형식 검증 (owner/repo)
            String[] repoParts = repository.split("/");
            if (repoParts.length != 2) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Invalid repository format. Expected: owner/repo"
                ));
            }

            String owner = repoParts[0];
            String repo = repoParts[1];

            // 날짜 파싱
            LocalDate since = LocalDate.parse(sinceStr);
            LocalDate until = LocalDate.parse(untilStr);

            // 1. GitHub에서 커밋 조회
            List<GitHubCommit> commits = gitHubService.getCommitsByDateRange(
                accessToken, owner, repo, since, until
            );

            // 커밋이 없으면 에러 반환
            if (commits.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "No commits found for the specified period",
                    "repository", repository,
                    "period", Map.of("since", sinceStr, "until", untilStr)
                ));
            }

            // 2. 커밋 데이터를 텍스트로 변환
            String commitsText = gitHubService.exportCommitsAsText(
                commits, repository, since, until
            );

            // 3. Gemini API로 리포트 생성
            String reportContent = geminiApiService.generateContent(commitsText, reportStyle);

            // 4. DynamoDB에 리포트 저장 (자동 저장이므로 한국 시간대 사용)
            log.info("보고서 저장 시작: userId={}, repository={}", userId, repository);
            reportService.saveUserReport(userId, repository, reportContent, java.time.ZoneId.of("Asia/Seoul"));
            log.info("보고서 저장 완료: userId={}, repository={}", userId, repository);

            // 5. 이메일 알림 전송 (사용자 설정에 따라)
            log.info("이메일 알림 전송 프로세스 시작: userId={}, repository={}", userId, repository);
            sendEmailNotificationIfEnabled(userId, repository, sinceStr, untilStr);
            log.info("이메일 알림 전송 프로세스 완료: userId={}, repository={}", userId, repository);

            // 6. 성공 응답 반환
            return ResponseEntity.ok(Map.of(
                "success", true,
                "reportId", repository,
                "userId", userId,
                "commitsCount", commits.size(),
                "period", Map.of("since", sinceStr, "until", untilStr),
                "message", "Report generated and saved successfully"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Invalid parameters: " + e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Failed to generate report: " + e.getMessage()
            ));
        }
    }

    /**
     * 여러 리포트 일괄 생성 API (Lambda에서 사용)
     *
     * Request Body:
     * {
     *   "reports": [
     *     {
     *       "accessToken": "token1",
     *       "repository": "owner/repo1",
     *       "since": "2024-01-01",
     *       "until": "2024-01-31",
     *       "reportStyle": "summary",
     *       "userId": "user1"
     *     },
     *     ...
     *   ]
     * }
     */
    @PostMapping("/generate-batch")
    public ResponseEntity<?> generateBatchReports(@RequestBody Map<String, Object> request) {
        try {
            List<Map<String, String>> reports = (List<Map<String, String>>) request.get("reports");

            if (reports == null || reports.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "No reports to generate"
                ));
            }

            int successCount = 0;
            int failCount = 0;
            StringBuilder errors = new StringBuilder();

            for (Map<String, String> reportRequest : reports) {
                try {
                    ResponseEntity<?> response = generateReport(reportRequest);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        Map<String, Object> body = (Map<String, Object>) response.getBody();
                        if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                            successCount++;
                        } else {
                            failCount++;
                            errors.append(String.format("Repository %s: %s\n",
                                reportRequest.get("repository"),
                                body != null ? body.get("message") : "Unknown error"));
                        }
                    } else {
                        failCount++;
                        errors.append(String.format("Repository %s: HTTP %s\n",
                            reportRequest.get("repository"),
                            response.getStatusCode()));
                    }
                } catch (Exception e) {
                    failCount++;
                    errors.append(String.format("Repository %s: %s\n",
                        reportRequest.get("repository"),
                        e.getMessage()));
                }
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "total", reports.size(),
                "successCount", successCount,
                "failCount", failCount,
                "errors", errors.toString()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Batch report generation failed: " + e.getMessage()
            ));
        }
    }

    /**
     * 사용자 설정에 따라 이메일 알림 전송
     * - UserPreset에서 emailNotificationEnabled가 true인 경우에만 전송
     */
    private void sendEmailNotificationIfEnabled(String userId, String repository, String since, String until) {
        log.info("========== 이메일 알림 전송 프로세스 시작 ==========");
        log.info("파라미터: userId={}, repository={}, since={}, until={}", userId, repository, since, until);
        
        try {
            log.info("[1단계] UserPreset 조회 시작: userId={}", userId);
            Optional<UserPreset> presetOpt = userPresetService.getPreset(userId);
            
            if (presetOpt.isEmpty()) {
                log.warn("[1단계] UserPreset을 찾을 수 없음: userId={} - 이메일 미전송", userId);
                log.info("========== 이메일 알림 전송 프로세스 종료 (설정 없음) ==========");
                return; // 설정 없으면 이메일 미전송
            }

            UserPreset preset = presetOpt.get();
            log.info("[1단계] UserPreset 조회 성공: userId={}", userId);
            log.info("  - emailNotificationEnabled: {}", preset.getEmailNotificationEnabled());
            log.info("  - email 설정 여부: {}", preset.getEmail() != null && !preset.getEmail().isEmpty());

            // 이메일 알림이 활성화되어 있고, 이메일 주소가 있는 경우에만 전송
            boolean emailEnabled = Boolean.TRUE.equals(preset.getEmailNotificationEnabled());
            boolean emailExists = preset.getEmail() != null && !preset.getEmail().isEmpty();
            
            log.info("[2단계] 이메일 전송 조건 확인:");
            log.info("  - 이메일 알림 활성화: {}", emailEnabled);
            log.info("  - 이메일 주소 존재: {}", emailExists);
            
            if (emailEnabled && emailExists) {
                String reportPeriod = String.format("%s ~ %s", since, until);
                String reportUrl = null; // TODO: 실제 보고서 URL이 있다면 설정

                log.info("[3단계] 이메일 전송 시작:");
                log.info("  - 리포지토리: {}", repository);
                log.info("  - 보고서 기간: {}", reportPeriod);

                emailService.sendReportCompletionEmail(
                    preset.getEmail(),
                    userId,
                    repository,
                    reportPeriod,
                    reportUrl
                );
                
                log.info("[3단계] 이메일 전송 완료: userId={}, repository={}", 
                    userId, repository);
                log.info("========== 이메일 알림 전송 프로세스 성공 완료 ==========");
            } else {
                log.info("[2단계] 이메일 전송 조건 미충족 - 전송하지 않음");
                if (!emailEnabled) {
                    log.info("  - 이유: 이메일 알림이 비활성화되어 있음");
                }
                if (!emailExists) {
                    log.info("  - 이유: 이메일 주소가 설정되지 않음");
                }
                log.info("========== 이메일 알림 전송 프로세스 종료 (조건 미충족) ==========");
            }
        } catch (Exception e) {
            // 이메일 전송 실패는 로그만 남기고 전체 프로세스에는 영향 없음
            log.error("========== 이메일 전송 중 오류 발생 ==========");
            log.error("오류 정보: userId={}, repository={}", userId, repository);
            log.error("오류 메시지: {}", e.getMessage());
            log.error("오류 클래스: {}", e.getClass().getName());
            log.error("스택 트레이스:", e);
            
            // DynamoDB 관련 오류인 경우 더 상세한 정보 제공
            if (e.getMessage() != null && e.getMessage().contains("DynamoDb")) {
                log.error("DynamoDB 오류 가능성 - 확인사항:");
                log.error("  1. DynamoDB 테이블 'UserPreset'이 존재하는지 확인");
                log.error("  2. AWS 자격 증명이 올바른지 확인 (AWS_ACCESS_KEY, AWS_SECRET_KEY)");
                log.error("  3. AWS 리전이 올바른지 확인 (ap-northeast-2)");
                log.error("  4. DynamoDB 테이블에 userId='{}'인 데이터가 있는지 확인", userId);
            }
            
            // SES 관련 오류인 경우
            if (e.getMessage() != null && (e.getMessage().contains("SES") || e.getMessage().contains("ses"))) {
                log.error("SES 오류 가능성 - 확인사항:");
                log.error("  1. 발신자 이메일이 AWS SES에서 인증되었는지 확인");
                log.error("  2. SES 샌드박스 모드인 경우 수신자 이메일도 인증 필요");
                log.error("  3. SES 권한이 올바른지 확인");
            }
            
            log.error("========== 이메일 알림 전송 프로세스 오류 종료 ==========");
        }
    }
}
