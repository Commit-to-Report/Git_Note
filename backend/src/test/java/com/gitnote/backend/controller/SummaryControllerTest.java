package com.gitnote.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitnote.backend.RestDocsConfiguration;
import com.gitnote.backend.dto.GitHubCommit;
import com.gitnote.backend.service.GeminiApiService;
import com.gitnote.backend.service.GitHubService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SummaryController.class)
@AutoConfigureRestDocs
@Import(RestDocsConfiguration.class)
public class SummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestDocumentationResultHandler restDocs;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GitHubService gitHubService;

    @MockBean
    private GeminiApiService geminiApiService;

    @Test
    public void summarizeCommits() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("accessToken", "test-token");

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("owner", "testuser");
        requestBody.put("repo", "testrepo");
        requestBody.put("since", "2024-01-01");
        requestBody.put("until", "2024-01-31");

        List<GitHubCommit> commits = Arrays.asList(
                createTestCommit("abc1234567890", "첫 번째 커밋", "testuser", "2024-01-01T10:00:00Z"),
                createTestCommit("def4567890123", "두 번째 커밋", "testuser", "2024-01-02T15:30:00Z")
        );

        String summary = "2024년 1월 동안 총 2개의 커밋이 있었습니다. " +
                "주요 작업 내용은 새로운 기능 추가와 버그 수정입니다.";

        given(gitHubService.getCommitsByDateRange(anyString(), anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(commits);
        given(geminiApiService.generateContent(anyString()))
                .willReturn(summary);

        // when & then
        mockMvc.perform(post("/api/summary/commits")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(summary))
                .andExpect(jsonPath("$.commitCount").value(2))
                .andDo(restDocs.document(
                        requestFields(
                                fieldWithPath("owner").type(JsonFieldType.STRING).description("저장소 소유자"),
                                fieldWithPath("repo").type(JsonFieldType.STRING).description("저장소 이름"),
                                fieldWithPath("since").type(JsonFieldType.STRING).description("시작 날짜 (YYYY-MM-DD)"),
                                fieldWithPath("until").type(JsonFieldType.STRING).description("종료 날짜 (YYYY-MM-DD)")
                        ),
                        responseFields(
                                fieldWithPath("summary").type(JsonFieldType.STRING).description("AI가 생성한 커밋 요약 내용"),
                                fieldWithPath("commitCount").type(JsonFieldType.NUMBER).description("분석된 커밋 개수")
                        )
                ));
    }

    private GitHubCommit createTestCommit(String sha, String message, String authorName, String date) {
        GitHubCommit commit = new GitHubCommit();
        commit.setSha(sha);

        GitHubCommit.CommitInfo commitInfo = new GitHubCommit.CommitInfo();
        commitInfo.setMessage(message);

        GitHubCommit.Author author = new GitHubCommit.Author();
        author.setName(authorName);
        author.setEmail(authorName + "@example.com");
        author.setDate(date);

        commitInfo.setAuthor(author);
        commit.setCommit(commitInfo);

        GitHubCommit.Author topLevelAuthor = new GitHubCommit.Author();
        topLevelAuthor.setLogin(authorName);
        topLevelAuthor.setName(authorName);
        commit.setAuthor(topLevelAuthor);

        return commit;
    }
}

