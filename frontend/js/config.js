// config.js - API 설정 관리
// 이 파일에서 모든 API 엔드포인트 URL을 중앙 관리합니다.

(function () {
  // ============================================
  // 환경 설정
  // ============================================

  // 프로덕션 환경에서 백엔드 API 주소 설정
  // HTTPS를 사용하여 Mixed Content 오류 방지
  // CloudFront를 통해 배포되는 경우 HTTPS를 사용해야 합니다
  const PRODUCTION_API_URL =
    "https://gitnot-Gitno-CMVc8qxDvMZ1-202218994.ap-northeast-2.elb.amazonaws.com"; // HTTPS 사용

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
