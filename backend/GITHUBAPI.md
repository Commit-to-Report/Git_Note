# GitNote - Github API 연동 가이드

## 목차

1. [시작하기](#시작하기)
2. [API 엔드포인트](#api-엔드포인트)
3. [화면별 기능 설명](#화면별-기능-설명)
4. [문제 해결](#문제-해결)

---

## 시작하기

### 사전 요구사항

- Java 17 이상
- Git
- Python 3 (프론트엔드 서버용)
- GitHub 계정

### 초기 설정

1. **GitHub OAuth App 설정**

   - GitHub Settings → Developer settings → OAuth Apps
   - New OAuth App 클릭
   - Application name: `GitNote`
   - Homepage URL: `http://localhost:5173`
   - Authorization callback URL: `http://localhost:5173/callback.html`
   - Client ID와 Client Secret을 복사

2. **Backend 설정 파일 수정**
   ```properties
   # backend/src/main/resources/application.properties
   github.client.id=YOUR_CLIENT_ID
   github.client.secret=YOUR_CLIENT_SECRET
   github.redirect.uri=http://localhost:5173/callback.html
   spring.profiles.active=dev
   ```

---

## API 엔드포인트

### 인증 관련

#### `GET /api/github/client-id`

GitHub OAuth Client ID를 반환합니다.

**응답 예시:**

```json
{
  "clientId": "Iv23li9z4Gvt8aMMuB5w"
}
```

#### `GET /api/github/user`

GitHub OAuth 코드를 사용하여 사용자 정보를 가져옵니다.

**파라미터:**

- `code` (required): GitHub OAuth authorization code

**응답 예시:**

```json
{
  "login": "username",
  "id": 12345,
  "name": "User Name",
  "email": "user@example.com",
  "avatarUrl": "https://avatars.githubusercontent.com/u/12345",
  "bio": "Developer",
  "location": "Seoul, Korea",
  "company": "Company Name",
  "publicRepos": 50,
  "followers": 100,
  "following": 80,
  "createdAt": "2015-01-01T00:00:00Z"
}
```

#### `POST /api/logout`

세션을 무효화하고 GitHub 토큰을 취소합니다.

**응답 예시:**

```json
{
  "message": "Logged out successfully"
}
```

---

### 리포지토리 관련

#### `GET /api/github/repositories`

로그인한 사용자의 모든 리포지토리를 가져옵니다.

**인증:** 세션 필요

**응답 예시:**

```json
{
  "repositories": [
    {
      "name": "Git_Note",
      "fullName": "username/Git_Note",
      "description": "GitHub commit viewer",
      "htmlUrl": "https://github.com/username/Git_Note",
      "private": false,
      "language": "Java",
      "stars": 5,
      "updatedAt": "2025-11-25T00:00:00Z"
    }
  ],
  "count": 1
}
```

---

### 커밋 관련

#### `GET /api/github/commits`

특정 리포지토리의 특정 날짜 범위 커밋을 가져옵니다.

**인증:** 세션 필요

**파라미터:**

- `owner` (required): 리포지토리 소유자
- `repo` (required): 리포지토리 이름
- `since` (required): 시작 날짜 (YYYY-MM-DD)
- `until` (required): 종료 날짜 (YYYY-MM-DD)
- `includeDetails` (optional): 상세 정보 포함 여부 (기본값: false)

**요청 예시:**

```
GET /api/github/commits?owner=username&repo=Git_Note&since=2025-11-18&until=2025-11-25&includeDetails=false
```

**응답 예시:**

```json
{
  "commits": [
    {
      "sha": "abc123def456",
      "commit": {
        "message": "Add commit search feature",
        "author": {
          "name": "User Name",
          "email": "user@example.com",
          "date": "2025-11-25T10:30:00Z"
        }
      },
      "htmlUrl": "https://github.com/username/Git_Note/commit/abc123def456"
    }
  ],
  "count": 1,
  "repository": "username/Git_Note",
  "period": {
    "since": "2025-11-18",
    "until": "2025-11-25"
  }
}
```

#### `GET /api/github/commits/export`

커밋 내용을 텍스트 파일로 내보냅니다.

**인증:** 세션 필요

**파라미터:**

- `owner` (required): 리포지토리 소유자
- `repo` (required): 리포지토리 이름
- `since` (required): 시작 날짜 (YYYY-MM-DD)
- `until` (required): 종료 날짜 (YYYY-MM-DD)

**요청 예시:**

```
GET /api/github/commits/export?owner=username&repo=Git_Note&since=2025-11-18&until=2025-11-25
```

**응답:** 텍스트 파일 다운로드

---

## 화면별 기능 설명

### 1. 메인 페이지 (`index.html`)

- GitHub 로그인 버튼
- 로그인 시 GitHub OAuth 페이지로 리다이렉트

### 2. 콜백 페이지 (`callback.html`)

- GitHub에서 인증 코드를 받아 처리
- 자동으로 대시보드로 이동

### 3. 대시보드 (`dashboard.html`)

**기능:**

- 사용자 프로필 정보 표시
- 계정 통계 (리포지토리 수, 팔로워 등)
- 커밋 조회 페이지로 이동
- 로그아웃

### 4. 커밋 조회 페이지 (`commits.html`)

**기능:**

#### a. 리포지토리 선택

- 드롭다운에서 사용자의 모든 리포지토리 조회

#### b. 날짜 범위 선택

- 시작 날짜와 종료 날짜 지정
- 기본값: 최근 7일

#### c. 커밋 조회

- 선택한 조건으로 커밋 검색
- 커밋 목록 표시:
  - 커밋 메시지
  - 작성자 이름
  - 커밋 SHA (짧은 버전)
  - 커밋 날짜
  - GitHub 링크

---

## 문제 해결

### 1. "Not authenticated" 에러

**원인:** 세션이 만료되었거나 로그인하지 않음

**해결 방법:**

- 로그아웃 후 다시 로그인
- 브라우저 쿠키 삭제 후 재시도

### 2. 리포지토리 목록이 비어있음

**원인:**

- GitHub 토큰 권한 부족
- API 호출 실패

**해결 방법:**

- 로그아웃 후 다시 로그인하여 권한 재승인
- 네트워크 연결 확인

### 3. 커밋을 찾을 수 없음

**원인:**

- 선택한 날짜 범위에 커밋이 없음
- 리포지토리 접근 권한 없음

**해결 방법:**

- 날짜 범위 조정
- 다른 리포지토리 선택
- 리포지토리가 비공개인 경우 권한 확인

### 5. CORS 에러

**원인:**

- Backend와 Frontend의 포트가 다름

**해결 방법:**

- Backend 서버가 8080 포트에서 실행 중인지 확인
- Frontend 서버가 5173 포트에서 실행 중인지 확인
- `WebConfig.java`에서 CORS 설정 확인

### 6. GitHub API Rate Limit

**원인:**

- GitHub API 호출 제한 초과

**해결 방법:**

- 잠시 대기 후 재시도
- 로그인한 상태에서는 시간당 5,000번까지 호출 가능

---

## 참고 링크

- [GitHub OAuth 문서](https://docs.github.com/en/developers/apps/building-oauth-apps)
- [GitHub REST API 문서](https://docs.github.com/en/rest)
- [Spring Boot 문서](https://spring.io/projects/spring-boot)
