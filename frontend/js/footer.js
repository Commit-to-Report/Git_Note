// footer.js

// 다크모드 초기화
(function () {
  const savedTheme = localStorage.getItem("theme") || "light";
  document.documentElement.setAttribute("data-theme", savedTheme);
})();

// 테마 토글 함수
function toggleTheme() {
  const currentTheme = document.documentElement.getAttribute("data-theme");
  const newTheme = currentTheme === "dark" ? "light" : "dark";

  document.documentElement.setAttribute("data-theme", newTheme);
  localStorage.setItem("theme", newTheme);
  updateThemeButton(newTheme);
}

// 테마 버튼 업데이트
function updateThemeButton(theme) {
  const themeIcon = document.getElementById("themeIcon");
  const themeText = document.getElementById("themeText");

  if (themeIcon && themeText) {
    if (theme === "dark") {
      themeIcon.textContent = "☀️";
      themeText.textContent = "라이트모드";
    } else {
      themeIcon.textContent = "🌙";
      themeText.textContent = "다크모드";
    }
  }
}

// 푸터 HTML 구조를 문자열로 정의
function getFooterHTML() {
  const currentTheme =
    document.documentElement.getAttribute("data-theme") || "light";
  const themeIcon = currentTheme === "dark" ? "☀️" : "🌙";
  const themeText = currentTheme === "dark" ? "라이트모드" : "다크모드";

  return `
    <footer class="app-footer">
        <div class="footer-content">
            <div class="footer-left">
                <span>© 2025 GitNote. All rights reserved.</span>
            </div>
            <div class="footer-right">
                <button class="theme-toggle-btn" id="themeToggleBtn" title="테마 전환">
                    <span id="themeIcon">${themeIcon}</span>
                    <span id="themeText">${themeText}</span>
                </button>
                <a href="https://github.com/Commit-to-Report/Git_Note.git" target="_blank" rel="noopener noreferrer" class="footer-link" title="GitHub 저장소">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/>
                    </svg>
                </a>
            </div>
        </div>
    </footer>
  `;
}

// 페이지 로드 시 푸터 삽입
document.addEventListener("DOMContentLoaded", () => {
  const footerContainer = document.getElementById("footer-container");
  if (footerContainer) {
    footerContainer.innerHTML = getFooterHTML();

    // 테마 토글 버튼 이벤트 리스너
    const themeToggleBtn = document.getElementById("themeToggleBtn");
    if (themeToggleBtn) {
      themeToggleBtn.addEventListener("click", toggleTheme);
    }

    // 초기 테마 버튼 상태 업데이트
    const currentTheme =
      document.documentElement.getAttribute("data-theme") || "light";
    updateThemeButton(currentTheme);
  }
});
