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
    public String generateContent(String prompt, String style) {
        System.out.println("[GeminiApiService] generateContent 시작 - prompt 길이: " + (prompt != null ? prompt.length() : 0));
        System.out.println("[GeminiApiService] prompt 내용: " + (prompt != null ? prompt.substring(0, Math.min(100, prompt.length())) + "..." : "null"));

        String styleInstruction = switch (style) {
            case "summary" -> "**스타일:** 간결하게 요약된 보고서를 작성하세요. 핵심 포인트 위주로 표현합니다.\n";
            case "detailed" -> "**스타일:** 상세 분석 보고서를 작성하세요. 각 커밋의 기능/문제점과 작업 흐름을 자세히 설명합니다.\n";
            case "statistics" -> "**스타일:** 통계 중심 보고서를 작성하세요. 커밋 유형, 수정 빈도, 기능 추가 비율 등을 강조합니다.\n";
            default -> "";
        };

        try {
            // URI에 API 키를 쿼리 파라미터로 추가
            String uri = "/models/" + model + ":generateContent?key=" + apiKey;

            String fullPrompt =
                    "당신은 반드시 마크다운(Markdown) 형식으로만 출력해야 한다.\n" +
                            "마크다운을 사용하지 않거나 서식이 유지되지 않으면 잘못된 출력으로 간주된다.\n" +
                            "출력 시 제목, 본문, 구분선, 강조, 코드블록 등 마크다운 요소를 적극 활용한다.\n\n" +

                            (prompt != null ? prompt : "") + "\n\n" +

                            "## 보고서 작성 지침\n" +
                            styleInstruction + "\n\n" +
                            "**언어:** 한국어\n" +
                            "**형식:** 자연스러운 서술식 문장 중심, 단순 목록 나열 금지\n" +
                            "**리포지토리 이름과 조회 기간을 반드시 포함할 것**\n";

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