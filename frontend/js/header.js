// header.js

// 헤더 HTML 구조를 문자열로 정의
const headerHTML = `
    <header class="app-header">
        <nav class="nav-menu">
            <a href="commits.html" class="nav-link">
                <span>🔍</span> 커밋 조회하기
            </a>
            <a href="commit-list.html" class="nav-link">커밋 목록</a>
            <a href="report-list.html" class="nav-link">보고서 목록</a>
        </nav>

        <div class="user-section">
            <div class="user-profile">
                <div class="user-icon">👤</div> </div>
            <a href="#" class="logout-btn" id="logoutBtn">로그아웃</a>
        </div>
    </header>
`;

// 페이지 로드 시 헤더 삽입
document.addEventListener('DOMContentLoaded', () => {
    const headerContainer = document.getElementById('header-container');
    if (headerContainer) {
        headerContainer.innerHTML = headerHTML;

        // 로그아웃 이벤트 리스너 추가 (필요시 기능 구현)
        document.getElementById('logoutBtn').addEventListener('click', (e) => {
            e.preventDefault();
            alert('로그아웃 되었습니다.'); // 추후 실제 로그아웃 로직으로 교체
            window.location.href = 'index.html'; // 로그인 페이지로 이동
        });
    }
});