package com.gitnote.backend.controller;

import com.gitnote.backend.dto.UserPresetRequest;
import com.gitnote.backend.dto.UserPresetRequests;
import com.gitnote.backend.dto.UserPresetResponse;
import com.gitnote.backend.entity.UserPreset;
import com.gitnote.backend.service.UserPresetService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 사용자 프리셋(설정) 관련 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/user/preset")
@RequiredArgsConstructor
public class UserPresetController {

    private final UserPresetService userPresetService;

    /**
     * 세션에서 username(로그인 유무) 체크 후 결과 반환  
     * @param session HttpSession
     * @return username, 없으면 ResponseEntity (401 Unauthorized)
     */
    private Object getUsernameOrUnauthorized(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                              .body(Map.of("error", "로그인이 필요합니다."));
        }
        return username;
    }

    /**
     * 사용자 프리셋 생성/수정  
     * - 이메일: 세션(email) 우선, 없으면 요청 값 사용  
     * - 자동 보고서 & 이메일 알림 & 보고서 스타일 / 주기 저장
     */
    @PostMapping
    public ResponseEntity<?> createOrUpdatePreset(
            @RequestBody UserPresetRequest request,
            HttpSession session
    ) {
        Object result = getUsernameOrUnauthorized(session);
        if (result instanceof ResponseEntity) return (ResponseEntity<?>) result;
        String username = (String) result;

        log.debug("[UserPresetController] 프리셋 저장 요청 - 사용자: {}, 세션ID: {}", username, session.getId());

        try {
            String sessionEmail = (String) session.getAttribute("email");
            String email = sessionEmail != null ? sessionEmail : request.getEmail();

            // DynamoDB는 빈 문자열을 허용하지 않으므로 null로 변환
            if (email != null && email.trim().isEmpty()) {
                email = null;
            }

            String reportStyle = request.getReportStyle();
            if (reportStyle != null && reportStyle.trim().isEmpty()) {
                reportStyle = null;
            }

            String reportFrequency = request.getReportFrequency();
            if (reportFrequency != null && reportFrequency.trim().isEmpty()) {
                reportFrequency = null;
            }

            String repository = request.getRepository();
            if (repository != null && repository.trim().isEmpty()) {
                repository = null;
            }

            // 세션에서 accessToken 가져오기 (Lambda에서 사용하기 위해 저장)
            String accessToken = (String) session.getAttribute("accessToken");

            // UserPreset 객체 생성 (Builder 패턴, null 아닌 값 처리)
            UserPreset preset = UserPreset.builder()
                    .userId(username)
                    .autoReportEnabled(Boolean.TRUE.equals(request.getAutoReportEnabled()))
                    .email(email)
                    .emailNotificationEnabled(request.getEmailNotificationEnabled())
                    .reportStyle(reportStyle)
                    .reportFrequency(reportFrequency)
                    .repository(repository)
                    .accessToken(accessToken)  // 세션의 accessToken 저장
                    .build();

            log.debug("[UserPresetController] 프리셋 저장 - 사용자: {}, 리포지토리: {}", username, repository);

            UserPreset savedPreset = userPresetService.createOrUpdatePreset(preset);
            return ResponseEntity.ok(UserPresetResponse.from(savedPreset));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "설정 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 사용자 프리셋 조회
     */
    @GetMapping
    public ResponseEntity<?> getPreset(HttpSession session) {
        Object result = getUsernameOrUnauthorized(session);
        if (result instanceof ResponseEntity) return (ResponseEntity<?>) result;
        String username = (String) result;

        try {
            Optional<UserPreset> presetOpt = userPresetService.getPreset(username);
            if (presetOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "사용자 설정을 찾을 수 없습니다."));
            }
            return ResponseEntity.ok(UserPresetResponse.from(presetOpt.get()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "설정 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 사용자 프리셋 삭제
     */
    @DeleteMapping
    public ResponseEntity<?> deletePreset(HttpSession session) {
        Object result = getUsernameOrUnauthorized(session);
        if (result instanceof ResponseEntity) return (ResponseEntity<?>) result;
        String username = (String) result;

        try {
            userPresetService.deletePreset(username);
            return ResponseEntity.ok(Map.of("message", "설정이 삭제되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "설정 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 이메일/이메일 알림 설정 수정
     */
    @PutMapping("/email")
    public ResponseEntity<?> updateEmailNotification(
            @RequestBody UserPresetRequests.EmailNotification request,
            HttpSession session
    ) {
        Object result = getUsernameOrUnauthorized(session);
        if (result instanceof ResponseEntity) return (ResponseEntity<?>) result;
        String username = (String) result;

        try {
            UserPreset updatedPreset = userPresetService.updateEmail(
                    username,
                    request.getEmail(),
                    request.getEnabled()
            );
            return ResponseEntity.ok(UserPresetResponse.from(updatedPreset));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "이메일 설정 업데이트 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 보고서 스타일 설정 수정
     */
    @PutMapping("/report-style")
    public ResponseEntity<?> updateReportStyle(
            @RequestBody UserPresetRequests.ReportStyle request,
            HttpSession session
    ) {
        Object result = getUsernameOrUnauthorized(session);
        if (result instanceof ResponseEntity) return (ResponseEntity<?>) result;
        String username = (String) result;

        try {
            UserPreset updatedPreset = userPresetService.updateReportStyle(username, request.getReportStyle());
            return ResponseEntity.ok(UserPresetResponse.from(updatedPreset));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "보고서 스타일 업데이트 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 보고서 생성 주기 설정 수정
     */
    @PutMapping("/report-frequency")
    public ResponseEntity<?> updateReportFrequency(
            @RequestBody UserPresetRequests.ReportFrequency request,
            HttpSession session
    ) {
        Object result = getUsernameOrUnauthorized(session);
        if (result instanceof ResponseEntity) return (ResponseEntity<?>) result;
        String username = (String) result;

        try {
            UserPreset updatedPreset = userPresetService.updateReportFrequency(username, request.getReportFrequency());
            return ResponseEntity.ok(UserPresetResponse.from(updatedPreset));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "보고서 생성 주기 업데이트 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

}
