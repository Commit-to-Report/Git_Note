const API_BASE_URL = 'http://localhost:8080';

// 페이지 로드 시 사용자 정보 가져오기
window.addEventListener('load', async () => {
    const code = localStorage.getItem('github_code');

    if (!code) {
        window.location.href = 'index.html';
        return;
    }

    try {
        // 백엔드 API로 사용자 정보 요청
        const response = await fetch(`${API_BASE_URL}/api/github/user?code=${code}`);

        if (!response.ok) {
            throw new Error('Failed to fetch user info');
        }

        const user = await response.json();

        // 사용자 정보를 로컬 스토리지에 저장
        localStorage.setItem('user_info', JSON.stringify(user));
        localStorage.removeItem('github_code'); // code는 한 번만 사용

        // UI 업데이트
        displayUserInfo(user);
    } catch (error) {
        console.error('Error:', error);
        document.getElementById('loading').style.display = 'none';
        document.getElementById('error').style.display = 'block';
        document.getElementById('error').textContent = '사용자 정보를 가져오는데 실패했습니다.';
    }
});

function displayUserInfo(user) {
    // 로딩 숨기기
    document.getElementById('loading').style.display = 'none';
    document.getElementById('userProfile').style.display = 'block';

    // 프로필 이미지
    document.getElementById('avatar').src = user.avatarUrl;

    // 기본 정보
    document.getElementById('name').textContent = user.name || user.login;
    document.getElementById('username').textContent = `@${user.login}`;

    // 선택적 정보
    if (user.bio) {
        document.getElementById('bio').textContent = user.bio;
    } else {
        document.getElementById('bio').style.display = 'none';
    }

    if (user.location) {
        document.getElementById('location').textContent = user.location;
    } else {
        document.getElementById('location').style.display = 'none';
    }

    if (user.company) {
        document.getElementById('company').textContent = user.company;
    } else {
        document.getElementById('company').style.display = 'none';
    }

    // 상세 정보
    document.getElementById('userId').textContent = user.id;
    document.getElementById('login').textContent = user.login;

    if (user.email) {
        document.getElementById('emailRow').style.display = 'flex';
        document.getElementById('email').textContent = user.email;
    }

    document.getElementById('repos').textContent = user.publicRepos;
    document.getElementById('followers').textContent = user.followers;
    document.getElementById('following').textContent = user.following;
    document.getElementById('created').textContent = new Date(user.createdAt).toLocaleDateString('ko-KR');
}

// 로그아웃 버튼 핸들러
document.getElementById('logoutBtn').addEventListener('click', async () => {
    try {
        // 로컬 스토리지 클리어
        localStorage.removeItem('user_info');
        localStorage.removeItem('github_code');

        // 백엔드 로그아웃 엔드포인트 호출 (선택적)
        await fetch(`${API_BASE_URL}/api/logout`, {
            method: 'POST',
            credentials: 'include'
        });

        // 로그인 페이지로 이동
        window.location.href = 'index.html';
    } catch (error) {
        console.error('Logout error:', error);
        // 에러가 있어도 로그인 페이지로 이동
        window.location.href = 'index.html';
    }
});
