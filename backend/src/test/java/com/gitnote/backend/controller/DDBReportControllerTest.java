package com.gitnote.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitnote.backend.RestDocsConfiguration;
import com.gitnote.backend.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DDBReportController.class)
@AutoConfigureRestDocs
@Import(RestDocsConfiguration.class)
public class DDBReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestDocumentationResultHandler restDocs;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReportService reportService;

    @Test
    public void saveReport() throws Exception {
        // given
        Map<String, String> request = new HashMap<>();
        request.put("userId", "testuser");
        request.put("reportId", "report_2024-11-27");
        request.put("content", "보고서 내용입니다...");

        doNothing().when(reportService).saveUserReport(anyString(), anyString(), anyString());

        // when & then
        mockMvc.perform(post("/api/user/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("보고서 저장 성공"))
                .andDo(restDocs.document(
                        requestFields(
                                fieldWithPath("userId").type(JsonFieldType.STRING).description("사용자 ID"),
                                fieldWithPath("reportId").type(JsonFieldType.STRING).description("보고서 ID"),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("보고서 내용")
                        ),
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("저장 성공 메시지")
                        )
                ));
    }
}

