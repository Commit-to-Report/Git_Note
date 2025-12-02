package com.gitnote.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

        log.info("[GeminiApiService] 초기화 완료 - 모델: {}, 프로젝트 ID: {}", model, projectId);
    }

    /**
     * Gemini API를 호출하여 텍스트를 생성합니다.
     * @param prompt 모델에게 전달할 요청 프롬프트
     * @param style 보고서 스타일
     * @return 생성된 텍스트
     */
    public String generateContent(String prompt, String style) {
        log.info("[GeminiApiService] 콘텐츠 생성 시작 - prompt 길이: {}", prompt != null ? prompt.length() : 0);

        String styleInstruction = switch (style) {
            case "summary" -> "**스타일:** 간결하게 요약된 보고서를 작성하세요. 핵심 포인트 위주로 표현합니다.\n";
            case "detailed" -> "**스타일:** 상세 분석 보고서를 작성하세요. 각 커밋의 기능/문제점과 작업 흐름을 자세히 설명합니다.\n";
            case "statistics" -> "**스타일:** 통계 중심 보고서를 작성하세요. 커밋 유형, 수정 빈도, 기능 추가 비율 등을 강조합니다.\n";
            default -> "";
        };

        try {
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

            String jsonBody = String.format("{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}]}", fullPrompt.replace("\"", "\\\""));

            log.debug("[GeminiApiService] API 호출 URI: {}", uri);

            Mono<String> responseMono = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> log.error("[GeminiApiService] Mono 에러 발생: {}", e.getMessage()));

            String rawJsonResult = responseMono.block();
            log.info("[GeminiApiService] Gemini API 호출 완료 - 응답 길이: {}", rawJsonResult != null ? rawJsonResult.length() : 0);

            String extractedText = extractTextFromJson(rawJsonResult);
            log.info("[GeminiApiService] 텍스트 추출 완료 - 길이: {}", extractedText.length());
            return extractedText;

        } catch (WebClientResponseException e) {
            log.error("[GeminiApiService] Gemini API 호출 실패 - 상태코드: {}, 응답: {}", 
                    e.getStatusCode(), 
                    e.getResponseBodyAsString().length() > 200 
                        ? e.getResponseBodyAsString().substring(0, 200) + "..." 
                        : e.getResponseBodyAsString());
            throw new RuntimeException("Gemini API 호출 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[GeminiApiService] 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("콘텐츠 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private String extractTextFromJson(String rawJson) {
        if (rawJson == null || rawJson.isEmpty()) return "";

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(rawJson);

            JsonNode textNode = findFirstTextNode(root);
            if (textNode != null) {
                return textNode.asText();
            }
        } catch (Exception e) {
            log.error("[GeminiApiService] JSON 파싱 실패: {}", e.getMessage());
        }
        return "";
    }

    private JsonNode findFirstTextNode(JsonNode node) {
        if (node.has("text")) return node.get("text");

        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                JsonNode result = findFirstTextNode(child);
                if (result != null) return result;
            }
        }
        return null;
    }
}
