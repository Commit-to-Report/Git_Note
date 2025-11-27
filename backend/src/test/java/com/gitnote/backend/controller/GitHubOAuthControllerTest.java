package com.gitnote.backend.controller;

import com.gitnote.backend.RestDocsConfiguration;
import com.gitnote.backend.dto.GitHubUserInfo;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GitHubOAuthController.class)
@AutoConfigureRestDocs
@Import(RestDocsConfiguration.class)
public class GitHubOAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestDocumentationResultHandler restDocs;

    @MockBean
    private GitHubService gitHubService;

    @Test
    public void getClientId() throws Exception {
        // when & then
        mockMvc.perform(get("/api/github/client-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").exists())
                .andDo(restDocs.document(
                        responseFields(
                                fieldWithPath("clientId").type(JsonFieldType.STRING).description("GitHub OAuth 클라이언트 ID")
                        )
                ));
    }

    @Test
    public void getUserInfo() throws Exception {
        // given
        String code = "test-code";
        String accessToken = "test-access-token";

        GitHubUserInfo userInfo = new GitHubUserInfo();
        userInfo.setLogin("testuser");
        userInfo.setId(12345L);
        userInfo.setName("Test User");
        userInfo.setEmail("testuser@example.com");
        userInfo.setAvatarUrl("https://avatars.githubusercontent.com/u/12345");
        userInfo.setBio("Test bio");
        userInfo.setLocation("Seoul, Korea");
        userInfo.setCompany("Test Company");
        userInfo.setPublicRepos(10);
        userInfo.setFollowers(100);
        userInfo.setFollowing(50);
        userInfo.setCreatedAt("2020-01-01T00:00:00Z");

        given(gitHubService.getAccessToken(anyString())).willReturn(accessToken);
        given(gitHubService.getUserInfo(anyString())).willReturn(userInfo);

        // when & then
        mockMvc.perform(get("/api/github/user")
                        .param("code", code)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("testuser"))
                .andDo(restDocs.document(
                        queryParameters(
                                parameterWithName("code").description("GitHub OAuth 인증 코드")
                        ),
                        responseFields(
                                fieldWithPath("login").type(JsonFieldType.STRING).description("GitHub 로그인 ID"),
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("GitHub 사용자 고유 ID"),
                                fieldWithPath("name").type(JsonFieldType.STRING).description("사용자 이름"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("이메일 주소"),
                                fieldWithPath("avatarUrl").type(JsonFieldType.STRING).description("프로필 이미지 URL"),
                                fieldWithPath("bio").type(JsonFieldType.STRING).description("자기소개"),
                                fieldWithPath("location").type(JsonFieldType.STRING).description("위치"),
                                fieldWithPath("company").type(JsonFieldType.STRING).description("회사"),
                                fieldWithPath("publicRepos").type(JsonFieldType.NUMBER).description("공개 저장소 수"),
                                fieldWithPath("followers").type(JsonFieldType.NUMBER).description("팔로워 수"),
                                fieldWithPath("following").type(JsonFieldType.NUMBER).description("팔로잉 수"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("계정 생성 날짜")
                        )
                ));
    }

    @Test
    public void checkSession() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "testuser");
        session.setAttribute("email", "testuser@example.com");

        // when & then
        mockMvc.perform(get("/api/user/session")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andDo(restDocs.document(
                        responseFields(
                                fieldWithPath("username").type(JsonFieldType.STRING).description("GitHub 사용자명"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("사용자 이메일")
                        )
                ));
    }

    @Test
    public void logout() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("accessToken", "test-token");
        session.setAttribute("username", "testuser");

        given(gitHubService.revokeToken(anyString())).willReturn(true);

        // when & then
        mockMvc.perform(post("/api/logout")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"))
                .andDo(restDocs.document(
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("로그아웃 성공 메시지")
                        )
                ));
    }
}

