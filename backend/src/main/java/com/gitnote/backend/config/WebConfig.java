package com.gitnote.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 관련 설정 클래스
 * CORS(Cross-Origin Resource Sharing) 정책을 설정하여
 * 프론트엔드와의 원활한 통신을 지원합니다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 프론트엔드 Origin URL(application.properties에서 주입)
    @Value("${frontend.url}")
    private String frontendUrl;

    /**
     * CORS 매핑 설정
     * - /api/** 경로에 대해서만 허용
     * - 지정된 프론트엔드 Origin만 허용
     * - 주요 HTTP 메소드 허용(GET, POST, PUT, DELETE, OPTIONS)
     * - 모든 헤더 허용
     * - 자격 증명(쿠키 등) 허용
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendUrl)      // 허용할 Origin (프론트 URL)
                .allowedMethods(
                        "GET",                   // 조회
                        "POST",                  // 생성
                        "PUT",                   // 수정
                        "DELETE",                // 삭제
                        "OPTIONS"                // Pre-flight
                )
                .allowedHeaders("*")             // 모든 헤더 허용
                .allowCredentials(true);         // 쿠키 등 인증정보 허용
    }
}
