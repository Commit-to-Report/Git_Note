package com.gitnote.backend.controller;

import com.gitnote.backend.dto.UserPresetRequest;
import com.gitnote.backend.dto.UserPresetResponse;
import com.gitnote.backend.dto.UserPresetRequests;
import com.gitnote.backend.entity.UserPreset;
import com.gitnote.backend.service.UserPresetService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user/preset")
@RequiredArgsConstructor
public class UserPresetController {

    private final UserPresetService userPresetService;

    @PostMapping
    public ResponseEntity<?> createOrUpdatePreset(@RequestBody UserPresetRequest request, HttpSession session) {
        try {
            String username = (String) session.getAttribute("username");
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
            }

            // 세션에서 GitHub 이메일 가져오기 (없으면 request에서 가져온 이메일 사용)
            String sessionEmail = (String) session.getAttribute("email");
            String email = sessionEmail != null ? sessionEmail : request.getEmail();

            // GitHub 사용자 정보를 세션에서 가져와서 userId로 사용
            // 현재는 username을 userId로 사용합니다.
            UserPreset preset = UserPreset.builder()
                    .userId(username)
                    .autoReportEnabled(request.getAutoReportEnabled() != null ? request.getAutoReportEnabled() : false)
                    .email(email)
                    .emailNotificationEnabled(request.getEmailNotificationEnabled())
                    .reportStyle(request.getReportStyle())
                    .reportFrequency(request.getReportFrequency())
                    .build();

            UserPreset savedPreset = userPresetService.createOrUpdatePreset(preset);
            return ResponseEntity.ok(UserPresetResponse.from(savedPreset));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "설정 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getPreset(HttpSession session) {
        try {
            String username = (String) session.getAttribute("username");
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
            }

            Optional<UserPreset> preset = userPresetService.getPreset(username);
            if (preset.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "사용자 설정을 찾을 수 없습니다."));
            }

            return ResponseEntity.ok(UserPresetResponse.from(preset.get()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "설정 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deletePreset(HttpSession session) {
        try {
            String username = (String) session.getAttribute("username");
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
            }

            userPresetService.deletePreset(username);
            return ResponseEntity.ok(Map.of("message", "설정이 삭제되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "설정 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PutMapping("/email")
    public ResponseEntity<?> updateEmailNotification(@RequestBody UserPresetRequests.EmailNotification request, HttpSession session) {
        try {
            String username = (String) session.getAttribute("username");
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
            }

            UserPreset updatedPreset = userPresetService.updateEmail(username, request.getEmail(), request.getEnabled());
            return ResponseEntity.ok(UserPresetResponse.from(updatedPreset));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "이메일 설정 업데이트 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PutMapping("/report-style")
    public ResponseEntity<?> updateReportStyle(@RequestBody UserPresetRequests.ReportStyle request, HttpSession session) {
        try {
            String username = (String) session.getAttribute("username");
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
            }

            UserPreset updatedPreset = userPresetService.updateReportStyle(username, request.getReportStyle());
            return ResponseEntity.ok(UserPresetResponse.from(updatedPreset));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "보고서 스타일 업데이트 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PutMapping("/report-frequency")
    public ResponseEntity<?> updateReportFrequency(@RequestBody UserPresetRequests.ReportFrequency request, HttpSession session) {
        try {
            String username = (String) session.getAttribute("username");
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다."));
            }

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
