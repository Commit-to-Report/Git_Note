package com.gitnote.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitnote.backend.RestDocsConfiguration;
import com.gitnote.backend.dto.S3UploadRequest;
import com.gitnote.backend.service.S3Service;
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

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(S3Controller.class)
@AutoConfigureRestDocs
@Import(RestDocsConfiguration.class)
public class S3ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestDocumentationResultHandler restDocs;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private S3Service s3Service;

    @Test
    public void uploadCommitLog() throws Exception {
        // given
        S3UploadRequest request = new S3UploadRequest();
        request.setUsername("testuser");
        request.setRepositoryName("testuser/testrepo");
        request.setStartDate("2024-01-01");
        request.setEndDate("2024-01-31");
        request.setContent("Commit logs content...");

        String fileUrl = "https://s3.amazonaws.com/bucket/testuser/testrepo_2024-11-27.txt";

        given(s3Service.uploadLog(anyString(), anyString(), anyString()))
                .willReturn(fileUrl);

        // when & then
        mockMvc.perform(post("/api/s3/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(fileUrl))
                .andExpect(jsonPath("$.message").value("Upload success"))
                .andDo(restDocs.document(
                        requestFields(
                                fieldWithPath("username").type(JsonFieldType.STRING).description("사용자명"),
                                fieldWithPath("repositoryName").type(JsonFieldType.STRING).description("저장소 이름 (owner/repo 형식)"),
                                fieldWithPath("startDate").type(JsonFieldType.STRING).description("커밋 시작 날짜"),
                                fieldWithPath("endDate").type(JsonFieldType.STRING).description("커밋 종료 날짜"),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("업로드할 커밋 로그 내용")
                        ),
                        responseFields(
                                fieldWithPath("url").type(JsonFieldType.STRING).description("업로드된 파일의 S3 URL"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("업로드 성공 메시지")
                        )
                ));
    }

    @Test
    public void getMyCommitLogs() throws Exception {
        // given
        String username = "testuser";
        List<String> fileList = Arrays.asList(
                "testrepo_2024-11-27.txt",
                "testrepo_2024-11-26.txt",
                "anotherepo_2024-11-25.txt"
        );

        given(s3Service.getUserFileList(anyString()))
                .willReturn(fileList);

        // when & then
        mockMvc.perform(get("/api/s3/list")
                        .param("username", username)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.count").value(3))
                .andDo(restDocs.document(
                        queryParameters(
                                parameterWithName("username").description("파일 목록을 조회할 사용자명")
                        ),
                        responseFields(
                                fieldWithPath("files").type(JsonFieldType.ARRAY).description("사용자의 커밋 로그 파일 목록"),
                                fieldWithPath("count").type(JsonFieldType.NUMBER).description("파일 개수")
                        )
                ));
    }
}

