# GitNote
[![Deploy to Amazon ECR](https://github.com/Commit-to-Report/Git_Note/actions/workflows/deploy.yml/badge.svg)](https://github.com/Commit-to-Report/Git_Note/actions/workflows/deploy.yml)
Failing 뜨면 즉시 김지윤에게 말씀해주세요.

GitNote는 GitHub OAuth를 통해 로그인하여 리포지토리의 커밋 내역을 조회하고 텍스트 파일로 내보낼 수 있는 웹 애플리케이션입니다.

## ✨ 주요 기능

- 🔐 GitHub OAuth 로그인
- 📦 사용자의 모든 리포지토리 조회
- 📅 날짜 범위를 지정한 커밋 검색
- 📝 커밋 내역을 상세하게 조회

## 🚀 실행 방법

### 1. Backend 서버 실행

```sh
cd [프로젝트 경로]/Git_Note/backend
./gradlew bootRun
```

### 2. Frontend 서버 실행 (새 터미널에서)

```sh
cd [프로젝트 경로]/Git_Note/frontend
python3 -m http.server 5173
```

### 3. 브라우저에서 접속

```
http://localhost:5173
```

---

## 📖 사용 방법

1. **GitHub 로그인**

   - 메인 페이지에서 "GitHub으로 로그인" 클릭
   - GitHub 권한 승인

2. **커밋 조회**

   - 대시보드에서 "📝 커밋 조회하기" 클릭
   - 리포지토리 선택
   - 날짜 범위 지정 (시작일 ~ 종료일)
   - "🔍 커밋 조회" 버튼 클릭

---

## 🛠️ 기술 스택

### Backend

- Java 17+
- Spring Boot 3.x
- Spring WebFlux (WebClient)
- GitHub REST API v3

### Frontend

- HTML5, CSS3, JavaScript (Vanilla)
- GitHub OAuth 2.0

---

## 서버 중지 방법

- **Backend 중지**

  ```sh
  pkill -f "spring-boot"
  ```

- **Frontend 중지**

  ```sh
  pkill -f "http.server 5173"
  ```

- **특정 포트(5173 등) 사용 중인 프로세스 확인 후 종료**
  ```sh
  lsof -i :5173  # 실행 중인 프로세스 PID 확인
  kill [PID]     # 해당 PID 종료
  ```

---

## 현재 서버 실행 상태 확인

- **Backend(8080 포트) 상태 확인**

  ```sh
  lsof -i :8080
  ```

- **Frontend(5173 포트) 상태 확인**
  ```sh
  lsof -i :5173
  ```

---

## ⚠️ 문제 해결 (Troubleshooting)

### 리포지토리 로딩이 느리거나 실패하는 경우

**증상:** "리포지토리를 불러오는 중..." 메시지가 오래 표시되거나 타임아웃 발생

**원인:**

1. GitHub API Rate Limit 초과
2. 네트워크 연결 문제
3. 서버 타임아웃
4. 많은 수의 리포지토리를 가진 계정

**해결 방법:**

1. **Rate Limit 확인**

   - 브라우저 콘솔(F12)에서 Rate Limit 정보 확인
   - GitHub API 제한: 인증된 요청은 시간당 5,000회
   - Reset 시간까지 대기 후 재시도

2. **다시 불러오기**

   - 페이지 새로고침 (F5 또는 Cmd/Ctrl+R)
   - "🔄 다시 불러오기" 버튼 클릭 (자동으로 표시됨)

3. **백엔드 로그 확인**

   ```sh
   # 백엔드 터미널에서 에러 로그 확인
   # "Failed to fetch repositories" 메시지 확인
   ```

4. **서버 재시작**

   ```sh
   # Backend 재시작
   cd backend
   pkill -f "spring-boot"
   ./gradlew bootRun
   ```

5. **네트워크 확인**
   ```sh
   # GitHub API 연결 테스트
   curl -H "Authorization: Bearer YOUR_TOKEN" https://api.github.com/rate_limit
   ```

### GitHub API Rate Limit 관련

**Rate Limit 정보:**

- 인증되지 않은 요청: 시간당 60회
- 인증된 요청: 시간당 5,000회
- Rate Limit이 초과되면 reset 시간까지 대기 필요

**확인 방법:**

```sh
curl -H "Authorization: Bearer YOUR_TOKEN" https://api.github.com/rate_limit
```

**대처 방법:**

- Rate Limit reset 시간까지 대기
- 다른 GitHub 계정으로 로그인
- 불필요한 API 호출 최소화

### 타임아웃 설정

현재 설정:

- 연결 타임아웃: 10초
- 응답 타임아웃: 30초
- 리포지토리 조회 타임아웃: 30초

더 긴 타임아웃이 필요한 경우 `backend/src/main/java/com/gitnote/backend/service/GitHubService.java`에서 설정 변경 가능

---
