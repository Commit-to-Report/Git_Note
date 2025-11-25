const API_BASE_URL = 'http://localhost:8080';

// GitHub 로그인 버튼 클릭 핸들러
document.getElementById('loginBtn').addEventListener('click', async () => {
    try {
        // 백엔드에서 Client ID 가져오기
        const response = await fetch(`${API_BASE_URL}/api/github/client-id`);
        const data = await response.json();
        const clientId = data.clientId;

        // GitHub OAuth URL 생성
        const redirectUri = encodeURIComponent('http://localhost:5173/callback.html');
        const scope = 'user:email,read:user';
        const githubAuthUrl = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&scope=${scope}`;

        // GitHub 인증 페이지로 리다이렉트
        window.location.href = githubAuthUrl;
    } catch (error) {
        console.error('Login error:', error);
        alert('로그인 중 오류가 발생했습니다.');
    }
});
