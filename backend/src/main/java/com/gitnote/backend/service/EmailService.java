package com.gitnote.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * 이메일 전송 서비스
 * AWS SES를 사용하여 이메일을 전송합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SesClient sesClient;

    @Value("${aws.ses.sender-email}")
    private String senderEmail;

    @Value("${aws.ses.sender-name}")
    private String senderName;

    /**
     * 보고서 생성 완료 알림 이메일 전송
     *
     * @param recipientEmail 수신자 이메일
     * @param userId GitHub 사용자 ID
     * @param repository 리포지토리명
     * @param reportPeriod 보고서 기간
     * @param reportUrl 보고서 URL (선택사항)
     */
    public void sendReportCompletionEmail(
            String recipientEmail,
            String userId,
            String repository,
            String reportPeriod,
            String reportUrl
    ) {
        log.info("========== EmailService: 보고서 완료 이메일 전송 시작 ==========");
        log.info("파라미터: userId={}, repository={}, reportPeriod={}", 
            userId, repository, reportPeriod);
        
        try {
            log.info("[EmailService 1단계] 이메일 제목 및 본문 생성 시작");
            String subject = String.format("[GitNote] %s 보고서가 생성되었습니다", repository);
            log.info("  - 제목: {}", subject);
            
            String htmlBody = buildReportEmailHtml(userId, repository, reportPeriod, reportUrl);
            String textBody = buildReportEmailText(userId, repository, reportPeriod, reportUrl);
            log.info("  - HTML 본문 길이: {} bytes", htmlBody.length());
            log.info("  - 텍스트 본문 길이: {} bytes", textBody.length());
            log.info("[EmailService 1단계] 이메일 제목 및 본문 생성 완료");

            log.info("[EmailService 2단계] SES를 통한 이메일 전송 시작");
            sendEmail(recipientEmail, subject, htmlBody, textBody);
            log.info("[EmailService 2단계] SES를 통한 이메일 전송 완료");
            
            log.info("보고서 완료 이메일 전송 성공: repository={}", repository);
            log.info("========== EmailService: 보고서 완료 이메일 전송 성공 완료 ==========");
        } catch (Exception e) {
            log.error("========== EmailService: 보고서 완료 이메일 전송 실패 ==========");
            log.error("오류 정보: repository={}", repository);
            log.error("오류 메시지: {}", e.getMessage());
            log.error("오류 클래스: {}", e.getClass().getName());
            log.error("스택 트레이스:", e);
            log.error("========== EmailService: 보고서 완료 이메일 전송 실패 종료 ==========");
            // 이메일 전송 실패는 전체 프로세스를 중단하지 않음
            throw e; // 상위로 예외 전파하여 로깅
        }
    }

    /**
     * AWS SES를 통해 이메일 전송
     */
    private void sendEmail(String recipientEmail, String subject, String htmlBody, String textBody) {
        long startTime = System.currentTimeMillis();
        log.info("========== SES 이메일 전송 시작 ==========");
        log.info("제목: {}", subject);
        
        try {
            log.info("[SES 1단계] SendEmailRequest 생성 시작");
            
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(sourceEmail)
                    .destination(Destination.builder()
                            .toAddresses(recipientEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject)
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .data(htmlBody)
                                            .charset("UTF-8")
                                            .build())
                                    .text(Content.builder()
                                            .data(textBody)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();
            log.info("[SES 1단계] SendEmailRequest 생성 완료");

            log.info("[SES 2단계] SES API 호출 시작 (sesClient.sendEmail)");
            log.info("  - 리전: {}", sesClient.serviceClientConfiguration().region());
            SendEmailResponse response = sesClient.sendEmail(request);
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("[SES 2단계] SES API 호출 성공");
            log.info("  - 소요시간: {}ms", duration);
            log.info("========== SES 이메일 전송 성공 완료 ==========");
        } catch (SesException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("========== SES 이메일 전송 실패 ==========");
            log.error("소요시간: {}ms", duration);
            log.error("오류 코드: {}", e.awsErrorDetails().errorCode());
            log.error("오류 메시지: {}", e.awsErrorDetails().errorMessage());
            log.error("HTTP 상태 코드: {}", e.statusCode());
            log.error("스택 트레이스:", e);
            log.error("========== SES 이메일 전송 실패 종료 ==========");
            throw new RuntimeException("이메일 전송 실패: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("========== SES 이메일 전송 중 예상치 못한 오류 발생 ==========");
            log.error("소요시간: {}ms", duration);
            log.error("오류 메시지: {}", e.getMessage());
            log.error("오류 클래스: {}", e.getClass().getName());
            log.error("스택 트레이스:", e);
            log.error("========== SES 이메일 전송 예상치 못한 오류 종료 ==========");
            throw new RuntimeException("이메일 전송 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 보고서 이메일 HTML 본문 생성
     */
    private String buildReportEmailHtml(String userId, String repository, String reportPeriod, String reportUrl) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head><meta charset=\"UTF-8\"></head>");
        html.append("<body style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #333;\">");
        html.append("<div style=\"max-width: 600px; margin: 0 auto; padding: 20px;\">");

        // 헤더
        html.append("<div style=\"background-color: #4A90E2; color: white; padding: 20px; text-align: center;\">");
        html.append("<h1 style=\"margin: 0;\">GitNote</h1>");
        html.append("</div>");

        // 본문
        html.append("<div style=\"background-color: #f9f9f9; padding: 30px; border: 1px solid #ddd;\">");
        html.append("<h2 style=\"color: #4A90E2;\">보고서 생성 완료</h2>");
        html.append("<p>안녕하세요, <strong>").append(userId).append("</strong>님!</p>");
        html.append("<p>요청하신 보고서가 성공적으로 생성되었습니다.</p>");

        html.append("<div style=\"background-color: white; padding: 15px; margin: 20px 0; border-left: 4px solid #4A90E2;\">");
        html.append("<p style=\"margin: 5px 0;\"><strong>리포지토리:</strong> ").append(repository).append("</p>");
        html.append("<p style=\"margin: 5px 0;\"><strong>기간:</strong> ").append(reportPeriod).append("</p>");
        html.append("</div>");

        if (reportUrl != null && !reportUrl.isEmpty()) {
            html.append("<p style=\"text-align: center; margin: 30px 0;\">");
            html.append("<a href=\"").append(reportUrl).append("\" ");
            html.append("style=\"background-color: #4A90E2; color: white; padding: 12px 30px; ");
            html.append("text-decoration: none; border-radius: 5px; display: inline-block;\">");
            html.append("보고서 확인하기");
            html.append("</a>");
            html.append("</p>");
        }

        html.append("<p style=\"color: #666; font-size: 14px; margin-top: 30px;\">");
        html.append("GitNote 서비스를 이용해 주셔서 감사합니다.");
        html.append("</p>");
        html.append("</div>");

        // 푸터
        html.append("<div style=\"text-align: center; padding: 20px; color: #999; font-size: 12px;\">");
        html.append("<p>이 이메일은 자동으로 발송되었습니다.</p>");
        html.append("<p>&copy; 2024 GitNote. All rights reserved.</p>");
        html.append("</div>");

        html.append("</div>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    /**
     * 보고서 이메일 텍스트 본문 생성 (HTML을 지원하지 않는 이메일 클라이언트용)
     */
    private String buildReportEmailText(String userId, String repository, String reportPeriod, String reportUrl) {
        StringBuilder text = new StringBuilder();
        text.append("GitNote 보고서 생성 완료\n\n");
        text.append("안녕하세요, ").append(userId).append("님!\n\n");
        text.append("요청하신 보고서가 성공적으로 생성되었습니다.\n\n");
        text.append("리포지토리: ").append(repository).append("\n");
        text.append("기간: ").append(reportPeriod).append("\n\n");

        if (reportUrl != null && !reportUrl.isEmpty()) {
            text.append("보고서 확인: ").append(reportUrl).append("\n\n");
        }

        text.append("GitNote 서비스를 이용해 주셔서 감사합니다.\n\n");
        text.append("---\n");
        text.append("이 이메일은 자동으로 발송되었습니다.\n");
        text.append("© 2024 GitNote. All rights reserved.");

        return text.toString();
    }
}
