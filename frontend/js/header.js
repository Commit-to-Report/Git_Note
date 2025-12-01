// header.js

// 헤더 HTML 구조를 문자열로 정의 (dashboard 스타일 적용)
function getHeaderHTML() {
    const username = sessionStorage.getItem("username");
    const avatar = sessionStorage.getItem("avatar");
    
    let userProfileHTML = '';
    if (username) {
        userProfileHTML = `
            <div class="user-profile">
                <div class="user-icon">
                    ${avatar ? `<img src="${avatar}" alt="User Avatar" />` : '<span>👤</span>'}
                </div>
                <span class="username">${username}</span>
            </div>
        `;
    }
    
    return `
        <header class="app-header">
            <div class="logo-section">
                <a href="dashboard.html">
                    <img src="img/logo_text.png" alt="GitNote" class="header-logo" />
                </a>
            </div>
            
            <nav class="nav-menu">
                <a href="dashboard.html" class="nav-link">
                    <span class="nav-icon">📊</span> 대시보드
                </a>
                <a href="commits.html" class="nav-link">
                    <span class="nav-icon">🔍</span> 커밋 조회하기
                </a>
                <a href="commit-list.html" class="nav-link">
                    <img src="img/list.png" alt="목록 아이콘" class="nav-icon"> 커밋 목록
                </a>
                <a href="report-list.html" class="nav-link">
                    <span class="nav-icon">📑</span> 보고서 목록
                </a>
            </nav>

            <div class="user-section">
                ${userProfileHTML}
                <a href="#" class="logout-btn" id="logoutBtn">
                    <img src="img/person.png" alt="로그아웃 아이콘" class="logout-icon"> 로그아웃
                </a>
            </div>
        </header>
    `;
}

// 페이지 로드 시 헤더 삽입
document.addEventListener('DOMContentLoaded', () => {
    const headerContainer = document.getElementById('header-container');
    if (headerContainer) {
        headerContainer.innerHTML = getHeaderHTML();

        // 로그아웃 이벤트 리스너 추가
        const logoutBtn = document.getElementById('logoutBtn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', (e) => {
                e.preventDefault();
                sessionStorage.clear();
                alert('로그아웃 되었습니다.');
                window.location.href = 'index.html';
            });
        }
    }
});