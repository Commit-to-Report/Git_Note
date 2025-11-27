package com.gitnote.backend.controller;

import com.gitnote.backend.RestDocsConfiguration;
import com.gitnote.backend.dto.GitHubCommit;
import com.gitnote.backend.dto.GitHubRepository;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;

@WebMvcTest(CommitController.class)
@AutoConfigureRestDocs
@Import(RestDocsConfiguration.class)
public class CommitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestDocumentationResultHandler restDocs;

    @MockBean
    private GitHubService gitHubService;

    @Test
    public void getRepositories() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("accessToken", "test-token");
        session.setAttribute("username", "testuser");

        GitHubRepository repo1 = new GitHubRepository();
        repo1.setName("repo1");
        repo1.setDescription("테스트 저장소 1");
        repo1.setHtmlUrl("https://github.com/testuser/repo1");

        GitHubRepository repo2 = new GitHubRepository();
        repo2.setName("repo2");
        repo2.setDescription("테스트 저장소 2");
        repo2.setHtmlUrl("https://github.com/testuser/repo2");

        List<GitHubRepository> repositories = Arrays.asList(repo1, repo2);

        given(gitHubService.getRepositories(anyString(), anyString()))
                .willReturn(repositories);

        // when & then
        mockMvc.perform(get("/api/github/repositories")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories").isArray())
                .andExpect(jsonPath("$.count").value(2))
                .andDo(restDocs.document(
                        relaxedResponseFields(
                                fieldWithPath("repositories").type(JsonFieldType.ARRAY).description("저장소 목록"),
                                fieldWithPath("repositories[].name").type(JsonFieldType.STRING).description("저장소 이름"),
                                fieldWithPath("repositories[].description").type(JsonFieldType.STRING).description("저장소 설명"),
                                fieldWithPath("repositories[].html_url").type(JsonFieldType.STRING).description("저장소 URL"),
                                fieldWithPath("count").type(JsonFieldType.NUMBER).description("저장소 개수")
                        )
                ));
    }

    @Test
    public void getCommits() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("accessToken", "test-token");

        List<GitHubCommit> commits = Arrays.asList(
                createTestCommit("abc123", "첫 번째 커밋", "testuser", "2024-01-01T10:00:00Z"),
                createTestCommit("def456", "두 번째 커밋", "testuser", "2024-01-02T15:30:00Z")
        );

        given(gitHubService.getCommitsByDateRange(anyString(), anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(commits);

        // when & then
        mockMvc.perform(get("/api/github/commits")
                        .param("owner", "testuser")
                        .param("repo", "testrepo")
                        .param("since", "2024-01-01")
                        .param("until", "2024-01-31")
                        .param("includeDetails", "false")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commits").isArray())
                .andExpect(jsonPath("$.count").value(2))
                .andDo(restDocs.document(
                        queryParameters(
                                parameterWithName("owner").description("저장소 소유자"),
                                parameterWithName("repo").description("저장소 이름"),
                                parameterWithName("since").description("시작 날짜 (YYYY-MM-DD)"),
                                parameterWithName("until").description("종료 날짜 (YYYY-MM-DD)"),
                                parameterWithName("includeDetails").description("상세 정보 포함 여부 (기본값: false)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("commits").type(JsonFieldType.ARRAY).description("커밋 목록"),
                                fieldWithPath("commits[].sha").type(JsonFieldType.STRING).description("커밋 SHA"),
                                fieldWithPath("commits[].commit.message").type(JsonFieldType.STRING).description("커밋 메시지"),
                                fieldWithPath("commits[].commit.author.name").type(JsonFieldType.STRING).description("작성자 이름"),
                                fieldWithPath("commits[].commit.author.email").type(JsonFieldType.STRING).description("작성자 이메일"),
                                fieldWithPath("commits[].commit.author.date").type(JsonFieldType.STRING).description("커밋 날짜"),
                                fieldWithPath("commits[].author.login").type(JsonFieldType.STRING).description("GitHub 로그인"),
                                fieldWithPath("commits[].author.name").type(JsonFieldType.STRING).description("GitHub 이름"),
                                fieldWithPath("count").type(JsonFieldType.NUMBER).description("커밋 개수"),
                                fieldWithPath("repository").type(JsonFieldType.STRING).description("저장소 경로"),
                                fieldWithPath("period.since").type(JsonFieldType.STRING).description("시작 날짜"),
                                fieldWithPath("period.until").type(JsonFieldType.STRING).description("종료 날짜")
                        )
                ));
    }

    @Test
    public void getRateLimit() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("accessToken", "test-token");

        Map<String, Object> rateLimit = Map.of(
                "limit", 5000,
                "remaining", 4999,
                "reset", 1234567890
        );

        given(gitHubService.getRateLimit(anyString()))
                .willReturn(rateLimit);

        // when & then
        mockMvc.perform(get("/api/github/rate-limit")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(5000))
                .andDo(restDocs.document(
                        relaxedResponseFields(
                                fieldWithPath("limit").type(JsonFieldType.NUMBER).description("시간당 최대 요청 수"),
                                fieldWithPath("remaining").type(JsonFieldType.NUMBER).description("남은 요청 수"),
                                fieldWithPath("reset").type(JsonFieldType.NUMBER).description("리셋 시간 (Unix timestamp)")
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
