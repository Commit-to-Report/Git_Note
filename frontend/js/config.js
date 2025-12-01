// config.js - API 설정 관리
// 이 파일에서 모든 API 엔드포인트 URL을 중앙 관리합니다.

(function () {
  // ============================================
  // 환경 설정
  // ============================================

  // 프로덕션 환경에서 백엔드 API 주소 설정
  // CloudFront를 통해 백엔드 API로 프록시하도록 설정
  // CloudFront Distribution에 /api/* behavior를 추가하여 백엔드 ALB로 프록시해야 함
  // 이렇게 하면 HTTPS 페이지에서 HTTP API 호출 시 Mixed Content 오류를 방지할 수 있음
  const PRODUCTION_API_URL = "https://d1l3a7dvc3xbrk.cloudfront.net"; // CloudFront 도메인 사용

  // 로컬 개발 환경 API 주소
  const LOCAL_API_URL = "http://localhost:8080";

  // ============================================
  // 자동 환경 감지
  // ============================================
  const hostname = window.location.hostname;

  // 로컬 개발 환경 감지
  if (hostname === "localhost" || hostname === "127.0.0.1" || hostname === "") {
    window.API_BASE_URL = LOCAL_API_URL;
    console.log("🔧 개발 모드: API_BASE_URL =", window.API_BASE_URL);
  } else {
    // 프로덕션 환경
    window.API_BASE_URL = PRODUCTION_API_URL;
    console.log("🚀 프로덕션 모드: API_BASE_URL =", window.API_BASE_URL);
  }
})();
