# GitNote
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
> 뱃지가 Failing(빨간색)으로 바뀌면 김지윤에게 알려주세요.

GitNote는 GitHub OAuth를 통해 로그인하여 리포지토리의 커밋 내역을 조회하고, **AWS S3에 백업**하거나 **AI 주간 보고서**를 생성할 수 있는 웹 애플리케이션입니다.

## ✨ 주요 기능
- 🔐 **GitHub OAuth 로그인**
- 📦 **사용자의 모든 리포지토리 조회**
- 📅 **날짜 범위를 지정한 커밋 검색**
- 📝 **커밋 내역 상세 조회 및 텍스트 복사**
- ☁️ **AWS S3 업로드: 커밋 내역을 텍스트 파일로 클라우드에 영구 저장 (자동 중복 방지)**

## 🛠️ 기술 스택
### Backend
- **Java 17+**, **Spring Boot 3.x**
- **Spring WebFlux (WebClient)**
- **AWS SDK 3.1.1/3.1.1** (S3, DynamoDB)
- **Google Gemini API**

### Frontend
- HTML5, CSS3, JavaScript (Vanilla)
- GitHub OAuth 2.0

---
## 🚀 실행 방법

### 1. Backend 서버 실행 (IntelliJ IDEA)
터미널이 아닌 **IntelliJ IDEA**를 사용하여 실행합니다.

1.  **프로젝트 열기**: IntelliJ에서 `Git_Note/backend` 폴더를 엽니다.
2.  **Gradle 로딩**: 오른쪽 `Gradle` 탭에서 새로고침(🔄)을 눌러 의존성을 다운로드합니다.
3.  **환경 변수 설정 (필수 ⭐)**:
    - 상단 메뉴 `Run` > `Edit Configurations...` > `BackendApplication` 선택
    - **Environment variables**에 아래 키들을 추가합니다.
        - `AWS_ACCESS_KEY`: AWS 액세스 키
        - `AWS_SECRET_KEY`: AWS 시크릿 키
        - `AWS_S3_BUCKET`: S3 버킷 이름 (예: gitnote-bucket)
        - `GEMINI_API_KEY`: Google Gemini API 키
        - `GITHUB_CLIENT_SECRET`: GitHub OAuth Client Secret
4.  **서버 실행**:
    - `BackendApplication.java` 파일을 엽니다.
    - 코드 옆의 **초록색 재생 버튼(▶)**을 클릭하여 서버를 시작합니다.
    - 로그에 `Tomcat started on port(s): 8080`이 뜨면 성공입니다.

### 2. Frontend 서버 실행 (터미널)
프론트엔드는 간단한 파이썬 웹 서버를 사용합니다.

```bash
cd [프로젝트 경로]/Git_Note/frontend
python3 -m http.server:5173
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
   - 
3. **기능 활용**
   -텍스트 복사: 조회된 내역을 클립보드에 복사하여 붙여넣기 가능
   -☁️ S3 업로드: 버튼을 클릭하여 커밋 내역을 AWS S3 클라우드에 텍스트 파일로 영구 저장 (자동으로 중복 파일명 처리됨)

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
