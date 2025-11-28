// header.js

// 헤더 HTML 구조를 문자열로 정의
const headerHTML = `
    <header class="app-header">
        <nav class="nav-menu">
            <a href="commits.html" class="nav-link">
                <span class="nav-icon">🔍</span> 커밋 조회하기
            </a>
            <a href="commit-list.html" class="nav-link">
                <img src="img/list.png" alt="목록 아이콘" class="nav-icon"> 커밋 목록
            </a>
            <a href="report-list.html" class="nav-link">
                <img src="img/week.png" alt="보고서 아이콘" class="nav-icon"> 보고서 목록
            </a>
        </nav>

        <div class="user-section">
            <a href="#" class="logout-btn" id="logoutBtn">
                <img src="img/person.png" alt="로그아웃 아이콘" class="logout-icon"> 로그아웃
            </a>
        </div>
    </header>
`;

// 페이지 로드 시 헤더 삽입
document.addEventListener('DOMContentLoaded', () => {
    const headerContainer = document.getElementById('header-container');
    if (headerContainer) {
        headerContainer.innerHTML = headerHTML;

        // 로그아웃 이벤트 리스너 추가 (기존과 동일)
        document.getElementById('logoutBtn').addEventListener('click', (e) => {
            e.preventDefault();
            alert('로그아웃 되었습니다.');
            window.location.href = 'index.html';
        });
    }
});