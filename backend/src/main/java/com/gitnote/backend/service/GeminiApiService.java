package com.gitnote.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class GeminiApiService {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String projectId;
    private final String location;

    public GeminiApiService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.model:gemini-2.5-flash}") String model,
            @Value("${gemini.project.id}") String projectId,
            @Value("${gemini.location:us-central1}") String location
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.projectId = projectId;
        this.location = location;

        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1")
                .build();

        System.out.println("[GeminiApiService] 초기화 완료. 모델: " + model + ", 프로젝트 ID: " + projectId);
    }

    /**
     * Gemini API를 호출하여 텍스트를 생성합니다.
     * @param prompt 모델에게 전달할 요청 프롬프트
     * @return 생성된 텍스트만 추출한 결과 문자열
     */
    public String generateContent(String prompt) {
        System.out.println("[GeminiApiService] generateContent 시작 - prompt 길이: " + (prompt != null ? prompt.length() : 0));
        System.out.println("[GeminiApiService] prompt 내용: " + (prompt != null ? prompt.substring(0, Math.min(100, prompt.length())) + "..." : "null"));

        try {
            // URI에 API 키를 쿼리 파라미터로 추가
            String uri = "/models/" + model + ":generateContent?key=" + apiKey;

            String fullPrompt = (prompt != null ? prompt : "") +
                    "**보고서 작성 지침:**\n" +
                    "\n" +
                    "1.  **언어:** 보고서의 모든 내용은 **한국어**로 작성되어야 합니다.\n" +
                    "2.  **형식:** 전체 보고서는 **서술식(narrative)** 문장으로 구성되어야 하며, 단순히 커밋 목록을 나열하는 형식은 피해야 합니다.\n" +
                    "3.  **내용:** 커밋 메시지(특히 'fix', 'feat', 'refactor' 등의 접두사)를 기반으로 하여, **어떤 기능이 추가/개선되었는지** 또는 **어떤 문제(버그/오류)가 해결되었는지**에 초점을 맞추어 작업의 흐름과 중요도를 자연스럽게 설명해 주세요.\n" +
                    "4.  **정보 포함:** 보고서의 시작 부분에는 [--- 커밋 내역 ---]에 명시된 **리포지토리 이름**과 **조회 기간**을 명확하게 포함해 주세요.";

            String escapedPrompt = escapeJson(fullPrompt);
            String jsonBody = String.format(
                    "{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}]}",
                    escapedPrompt
            );


            System.out.println("[GeminiApiService] 호출 URI: " + uri);
            System.out.println("[GeminiApiService] 요청 바디 (부분): " + (jsonBody.length() > 200 ? jsonBody.substring(0, 200) + "..." : jsonBody));

            Mono<String> responseMono = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> System.err.println("[GeminiApiService] Mono 에러 발생: " + e.getMessage()));

            String rawJsonResult = responseMono.block();
            System.out.println("[GeminiApiService] Gemini API 호출 완료, 응답 길이: " + (rawJsonResult != null ? rawJsonResult.length() : 0));

            String extractedText = extractTextFromJson(rawJsonResult);

            System.out.println("[GeminiApiService] 추출된 텍스트 길이: " + extractedText.length());
            return extractedText;

        } catch (WebClientResponseException e) {
            System.err.println("[GeminiApiService] Gemini API 호출 실패 - 상태 코드: " + e.getStatusCode());
            System.err.println("[GeminiApiService] 응답 내용: " + e.getResponseBodyAsString().substring(0, Math.min(200, e.getResponseBodyAsString().length())) + "...");
            e.printStackTrace();
            return "API Error: " + e.getMessage();
        } catch (Exception e) {
            System.err.println("[GeminiApiService] 예외 발생: " + e.getMessage());
            e.printStackTrace();
            return "Internal Error: " + e.getMessage();
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String extractTextFromJson(String rawJson) {
        if (rawJson == null || rawJson.isEmpty()) {
            return "";
        }

        String searchKey = "\"text\": \"";
        int startIndex = rawJson.indexOf(searchKey);

        if (startIndex == -1) {
            System.err.println("[GeminiApiService] 텍스트 필드를 찾을 수 없습니다.");
            return rawJson;
        }

        startIndex += searchKey.length();

        int endIndex = rawJson.indexOf("\"", startIndex);

        if (endIndex == -1) {
            return rawJson.substring(startIndex);
        }

        try {
            String extracted = rawJson.substring(startIndex, endIndex);
            return extracted.replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\r", "\r")
                    .replace("\\\\", "\\");
        } catch (IndexOutOfBoundsException e) {
            System.err.println("[GeminiApiService] JSON 파싱 중 인덱스 오류 발생.");
            return rawJson;
        }
    }
}