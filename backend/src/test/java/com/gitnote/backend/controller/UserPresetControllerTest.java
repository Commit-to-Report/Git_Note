package com.gitnote.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitnote.backend.RestDocsConfiguration;
import com.gitnote.backend.dto.UserPresetRequest;
import com.gitnote.backend.dto.UserPresetRequests;
import com.gitnote.backend.entity.UserPreset;
import com.gitnote.backend.service.UserPresetService;
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

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserPresetController.class)
@AutoConfigureRestDocs
@Import(RestDocsConfiguration.class)
public class UserPresetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestDocumentationResultHandler restDocs;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserPresetService userPresetService;

    @Test
    public void createOrUpdatePreset() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "testuser");
        session.setAttribute("email", "testuser@example.com");

        UserPresetRequest request = new UserPresetRequest();
        request.setAutoReportEnabled(true);
        request.setEmail("testuser@example.com");
        request.setEmailNotificationEnabled(true);
        request.setReportStyle("상세 보고서");
        request.setReportFrequency("WEEKLY");

        UserPreset savedPreset = UserPreset.builder()
                .userId("testuser")
                .autoReportEnabled(true)
                .email("testuser@example.com")
                .emailNotificationEnabled(true)
                .reportStyle("상세 보고서")
                .reportFrequency("WEEKLY")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(userPresetService.createOrUpdatePreset(any(UserPreset.class)))
                .willReturn(savedPreset);

        // when & then
        mockMvc.perform(post("/api/user/preset")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("testuser"))
                .andDo(restDocs.document(
                        requestFields(
                                fieldWithPath("autoReportEnabled").type(JsonFieldType.BOOLEAN).description("보고서 자동 생성 활성화 여부"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("알림받을 이메일 주소"),
                                fieldWithPath("emailNotificationEnabled").type(JsonFieldType.BOOLEAN).description("이메일 알림 활성화 여부"),
                                fieldWithPath("reportStyle").type(JsonFieldType.STRING).description("보고서 스타일/프롬프트"),
                                fieldWithPath("reportFrequency").type(JsonFieldType.STRING).description("보고서 생성 주기 (DAILY, WEEKLY, MONTHLY)")
                        ),
                        responseFields(
                                fieldWithPath("userId").type(JsonFieldType.STRING).description("사용자 ID"),
                                fieldWithPath("autoReportEnabled").type(JsonFieldType.BOOLEAN).description("보고서 자동 생성 활성화 여부"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("알림받을 이메일 주소"),
                                fieldWithPath("emailNotificationEnabled").type(JsonFieldType.BOOLEAN).description("이메일 알림 활성화 여부"),
                                fieldWithPath("reportStyle").type(JsonFieldType.STRING).description("보고서 스타일/프롬프트"),
                                fieldWithPath("reportFrequency").type(JsonFieldType.STRING).description("보고서 생성 주기"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 일시"),
                                fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("수정 일시")
                        )
                ));
    }

    @Test
    public void getPreset() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "testuser");

        UserPreset preset = UserPreset.builder()
                .userId("testuser")
                .autoReportEnabled(true)
                .email("testuser@example.com")
                .emailNotificationEnabled(true)
                .reportStyle("상세 보고서")
                .reportFrequency("WEEKLY")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(userPresetService.getPreset(anyString()))
                .willReturn(Optional.of(preset));

        // when & then
        mockMvc.perform(get("/api/user/preset")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("testuser"))
                .andDo(restDocs.document(
                        responseFields(
                                fieldWithPath("userId").type(JsonFieldType.STRING).description("사용자 ID"),
                                fieldWithPath("autoReportEnabled").type(JsonFieldType.BOOLEAN).description("보고서 자동 생성 활성화 여부"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("알림받을 이메일 주소"),
                                fieldWithPath("emailNotificationEnabled").type(JsonFieldType.BOOLEAN).description("이메일 알림 활성화 여부"),
                                fieldWithPath("reportStyle").type(JsonFieldType.STRING).description("보고서 스타일/프롬프트"),
                                fieldWithPath("reportFrequency").type(JsonFieldType.STRING).description("보고서 생성 주기"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 일시"),
                                fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("수정 일시")
                        )
                ));
    }

    @Test
    public void deletePreset() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "testuser");

        doNothing().when(userPresetService).deletePreset(anyString());

        // when & then
        mockMvc.perform(delete("/api/user/preset")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("설정이 삭제되었습니다."))
                .andDo(restDocs.document(
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("설정 삭제 성공 메시지")
                        )
                ));
    }

    @Test
    public void updateEmailNotification() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "testuser");

        UserPresetRequests.EmailNotification request = new UserPresetRequests.EmailNotification();
        request.setEmail("newemail@example.com");
        request.setEnabled(true);

        UserPreset updatedPreset = UserPreset.builder()
                .userId("testuser")
                .autoReportEnabled(true)
                .email("newemail@example.com")
                .emailNotificationEnabled(true)
                .reportStyle("상세 보고서")
                .reportFrequency("WEEKLY")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(userPresetService.updateEmail(anyString(), anyString(), any(Boolean.class)))
                .willReturn(updatedPreset);

        // when & then
        mockMvc.perform(put("/api/user/preset/email")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newemail@example.com"))
                .andDo(restDocs.document(
                        requestFields(
                                fieldWithPath("email").type(JsonFieldType.STRING).description("새로운 이메일 주소"),
                                fieldWithPath("enabled").type(JsonFieldType.BOOLEAN).description("이메일 알림 활성화 여부")
                        ),
                        responseFields(
                                fieldWithPath("userId").type(JsonFieldType.STRING).description("사용자 ID"),
                                fieldWithPath("autoReportEnabled").type(JsonFieldType.BOOLEAN).description("보고서 자동 생성 활성화 여부"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("알림받을 이메일 주소"),
                                fieldWithPath("emailNotificationEnabled").type(JsonFieldType.BOOLEAN).description("이메일 알림 활성화 여부"),
                                fieldWithPath("reportStyle").type(JsonFieldType.STRING).description("보고서 스타일/프롬프트"),
                                fieldWithPath("reportFrequency").type(JsonFieldType.STRING).description("보고서 생성 주기"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 일시"),
                                fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("수정 일시")
                        )
                ));
    }

    @Test
    public void updateReportStyle() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "testuser");

        UserPresetRequests.ReportStyle request = new UserPresetRequests.ReportStyle();
        request.setReportStyle("간단한 요약");

        UserPreset updatedPreset = UserPreset.builder()
                .userId("testuser")
                .autoReportEnabled(true)
                .email("testuser@example.com")
                .emailNotificationEnabled(true)
                .reportStyle("간단한 요약")
                .reportFrequency("WEEKLY")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(userPresetService.updateReportStyle(anyString(), anyString()))
                .willReturn(updatedPreset);

        // when & then
        mockMvc.perform(put("/api/user/preset/report-style")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportStyle").value("간단한 요약"))
                .andDo(restDocs.document(
                        requestFields(
                                fieldWithPath("reportStyle").type(JsonFieldType.STRING).description("새로운 보고서 스타일/프롬프트")
                        ),
                        responseFields(
                                fieldWithPath("userId").type(JsonFieldType.STRING).description("사용자 ID"),
                                fieldWithPath("autoReportEnabled").type(JsonFieldType.BOOLEAN).description("보고서 자동 생성 활성화 여부"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("알림받을 이메일 주소"),
                                fieldWithPath("emailNotificationEnabled").type(JsonFieldType.BOOLEAN).description("이메일 알림 활성화 여부"),
                                fieldWithPath("reportStyle").type(JsonFieldType.STRING).description("보고서 스타일/프롬프트"),
                                fieldWithPath("reportFrequency").type(JsonFieldType.STRING).description("보고서 생성 주기"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 일시"),
                                fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("수정 일시")
                        )
                ));
    }

    @Test
    public void updateReportFrequency() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("username", "testuser");

        UserPresetRequests.ReportFrequency request = new UserPresetRequests.ReportFrequency();
        request.setReportFrequency("DAILY");

        UserPreset updatedPreset = UserPreset.builder()
                .userId("testuser")
                .autoReportEnabled(true)
                .email("testuser@example.com")
                .emailNotificationEnabled(true)
                .reportStyle("상세 보고서")
                .reportFrequency("DAILY")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(userPresetService.updateReportFrequency(anyString(), anyString()))
                .willReturn(updatedPreset);

        // when & then
        mockMvc.perform(put("/api/user/preset/report-frequency")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportFrequency").value("DAILY"))
                .andDo(restDocs.document(
                        requestFields(
                                fieldWithPath("reportFrequency").type(JsonFieldType.STRING).description("새로운 보고서 생성 주기 (DAILY, WEEKLY, MONTHLY)")
                        ),
                        responseFields(
                                fieldWithPath("userId").type(JsonFieldType.STRING).description("사용자 ID"),
                                fieldWithPath("autoReportEnabled").type(JsonFieldType.BOOLEAN).description("보고서 자동 생성 활성화 여부"),
                                fieldWithPath("email").type(JsonFieldType.STRING).description("알림받을 이메일 주소"),
                                fieldWithPath("emailNotificationEnabled").type(JsonFieldType.BOOLEAN).description("이메일 알림 활성화 여부"),
                                fieldWithPath("reportStyle").type(JsonFieldType.STRING).description("보고서 스타일/프롬프트"),
                                fieldWithPath("reportFrequency").type(JsonFieldType.STRING).description("보고서 생성 주기"),
                                fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 일시"),
                                fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("수정 일시")
                        )
                ));
    }
}

