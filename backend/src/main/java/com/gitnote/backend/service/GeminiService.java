package com.gitnote.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitnote.backend.dto.GeminiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    public String generateReport(String commitLogs) {
        // [수정] 요청하신 Gemini 2.0 모델 (gemini-2.0-flash-exp)
        // 만약 404 에러가 나면 "gemini-1.5-flash" 또는 "gemini-pro"로 변경하세요.
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=" + apiKey;

        // [수정] 요청하신 상세 프롬프트 적용
        String instruction = """
                # 보고서 작성 지침
                당신은 시니어 개발자입니다. 아래 커밋 로그를 분석하여 '주간 개발 보고서'를 작성해주세요.
                
                ## 보고서 구조:
                1. **개발 활동 개요**: 전체 커밋 수 및 주요 활동(FIX/FEAT 등) 경향 분석.
                2. **핵심 성과 분석**: FEAT, FIX, BUILD 등 유형별로 분류하여 그 중요성 및 구체적인 내용을 서술. 각 유형별로 최소 3개 이상의 핵심 커밋을 인용하여 설명하세요.
                3. **결론 및 향후 제언**: 기간 동안의 성과를 요약하고, 개선 필요 사항(예: 미분류 커밋) 및 다음 개발 주기에 대한 전략을 제언.
                4. **별첨: 상세 커밋 목록**: 모든 커밋을 포함한 목록.
                
                ## 주의사항:
                - 절대 커밋 메시지를 나열하면서 보고서를 시작하지 마세요.
                - 반드시 '서술적인 문장'으로 내용을 채우세요.
                - 마크다운(Markdown) 형식을 사용하여 깔끔하게 작성하세요.
                """;

        // 실제 데이터(커밋 로그)와 지침을 합침
        String finalPrompt = instruction + "\n\n# 분석 대상 커밋 로그:\n" + commitLogs;

        RestClient restClient = RestClient.create();

        try {
            // API 호출
            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiRequest.create(finalPrompt))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);

            // 응답 구조 파싱
            JsonNode candidates = root.path("candidates");
            if (candidates.isMissingNode() || candidates.isEmpty()) {
                return "AI 응답 오류: 생성된 내용이 없습니다. (Safety filters 등의 이유일 수 있음)";
            }

            return candidates.get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

        } catch (HttpClientErrorException e) {
            return "AI 요청 실패 (" + e.getStatusCode() + "): " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "AI 처리 중 오류 발생: " + e.getMessage();
        }
    }
}