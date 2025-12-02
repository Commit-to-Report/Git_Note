# GitNote Backend API 문서

## Spring REST Docs

이 프로젝트는 **Spring REST Docs**를 사용하여 API 문서를 자동으로 생성합니다.

### Spring REST Docs란?

Spring REST Docs는 테스트 코드를 기반으로 API 문서를 자동 생성하는 도구입니다.

- 테스트가 통과해야만 문서가 생성되므로 **문서의 정확성 보장**
- 코드 변경 시 자동으로 문서 업데이트
- AsciiDoc 형식으로 아름답고 읽기 쉬운 HTML 문서 생성

## API 문서 생성 방법

### 1. 테스트 실행 및 문서 생성

```bash
cd backend

# 방법 1: 테스트 실행 후 문서 생성
./gradlew clean test asciidoctor

# 방법 2: 테스트 건너뛰고 문서만 생성 (이미 snippets가 있는 경우)
./gradlew asciidoctor -x test
```

### 2. Controller 테스트만 실행

```bash
# 모든 Controller 테스트 실행
./gradlew test --tests "*ControllerTest"

# 특정 Controller 테스트만 실행
./gradlew test --tests "CommitControllerTest"
./gradlew test --tests "GitHubOAuthControllerTest"
./gradlew test --tests "S3ControllerTest"
```

---

## 생성된 API 문서 보기

### 문서 위치

```
backend/build/docs/asciidoc/index.html
```

### 브라우저에서 열기

```bash
# macOS
open backend/build/docs/asciidoc/index.html

# Linux
xdg-open backend/build/docs/asciidoc/index.html

# Windows
start backend/build/docs/asciidoc/index.html

# 또는 파일 탐색기에서 직접 열기
```

---

## 프로젝트 구조

```
backend/
├── src/
│   ├── docs/
│   │   └── asciidoc/
│   │       └── index.adoc           # API 문서 템플릿 (수동 작성)
│   └── test/
│       └── java/.../controller/
│           ├── CommitControllerTest.java         # GitHub API 테스트
│           ├── GitHubOAuthControllerTest.java    # OAuth 인증 테스트
│           ├── S3ControllerTest.java             # S3 API 테스트
│           ├── DDBReportControllerTest.java      # DynamoDB 보고서 API 테스트
│           └── UserPresetControllerTest.java     # 사용자 설정 API 테스트
└── build/
    ├── generated-snippets/          # 테스트에서 자동 생성된 문서 조각
    │   ├── commit-controller-test/
    │   ├── git-hub-o-auth-controller-test/
    │   ├── s3-controller-test/
    │   └── ...
    └── docs/
        └── asciidoc/
            └── index.html           # 최종 생성된 API 문서
```

## 새로운 API 문서 추가 방법

### 1. Controller 테스트 작성

`src/test/java/.../controller/` 경로에 테스트 파일 생성:

```java
@WebMvcTest(YourController.class)
@AutoConfigureRestDocs
@Import(RestDocsConfiguration.class)
public class YourControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestDocumentationResultHandler restDocs;

    @MockBean
    private YourService yourService;

    @Test
    public void yourApiTest() throws Exception {
        // given
        // 테스트 데이터 준비

        // when & then
        mockMvc.perform(get("/api/your-endpoint")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(restDocs.document(
                        responseFields(
                                fieldWithPath("field1").description("필드1 설명"),
                                fieldWithPath("field2").description("필드2 설명")
                        )
                ));
    }
}
```

### 2. index.adoc에 섹션 추가

`src/docs/asciidoc/index.adoc` 파일에 새로운 API 섹션 추가:

```asciidoc
[[resources-your-api]]
=== Your API

API 설명을 작성합니다.

[[resources-your-api-endpoint]]
==== 엔드포인트 이름

===== 요청

include::{snippets}/your-controller-test/your-api-test/http-request.adoc[]

===== 응답

include::{snippets}/your-controller-test/your-api-test/http-response.adoc[]

===== 응답 필드

include::{snippets}/your-controller-test/your-api-test/response-fields.adoc[]
```

### 3. 테스트 실행 및 문서 생성

```bash
./gradlew clean test asciidoctor
```

---

## 문서화된 API 목록

현재 문서화된 API는 **총 20개 엔드포인트**입니다:

### 1. OAuth & 인증 API (4개)

- `GET /api/github/client-id` - GitHub OAuth 클라이언트 ID 조회
- `GET /api/github/user` - 사용자 정보 조회 및 세션 생성
- `GET /api/user/session` - 세션 확인
- `POST /api/logout` - 로그아웃

### 2. GitHub API (3개)

- `GET /api/github/repositories` - 저장소 목록 조회
- `GET /api/github/commits` - 커밋 목록 조회
- `GET /api/github/rate-limit` - API 호출 제한 조회

### 3. S3 스토리지 API (2개)

- `POST /api/s3/upload` - 커밋 로그 업로드
- `GET /api/s3/list` - 커밋 로그 목록 조회

### 4. S3 보고서 생성 API (1개)

- `GET /api/s3/report` - AI 보고서 생성

### 5. DynamoDB 보고서 API (3개)

- `POST /api/user/report` - 보고서 저장
- `GET /api/user/report/list` - 저장된 보고서 목록 조회
- `GET /api/user/report/view` - 특정 보고서 상세 조회 (PK, SK)

### 6. 사용자 설정 API (6개)

- `POST /api/user/preset` - 설정 생성/수정 (전체)
- `GET /api/user/preset` - 설정 조회
- `DELETE /api/user/preset` - 설정 삭제
- `PUT /api/user/preset/email` - 이메일 설정 수정
- `PUT /api/user/preset/report-style` - 보고서 스타일 수정
- `PUT /api/user/preset/report-frequency` - 보고서 생성 주기 수정

### 7. 헬스 체크 API (1개)

- `GET /api/health` - 애플리케이션 상태 확인

---

### 내부 API

다음 API는 Lambda 함수나 내부 시스템에서만 사용되며, 외부에서 직접 호출하지 않습니다:

- `POST /api/auto-report/generate` - 자동 보고서 생성 (Lambda 호출용)
- `POST /api/auto-report/generate-batch` - 배치 보고서 생성 (Lambda 호출용)

## 문서 커스터마이징

### 스타일 수정

`src/docs/asciidoc/index.adoc` 파일의 `<style>` 태그 내에서 CSS를 수정할 수 있습니다.

### 섹션 추가/제거

`index.adoc` 파일에서 원하는 섹션을 추가하거나 제거할 수 있습니다.

## 문제 해결

### 문서가 생성되지 않는 경우

```bash
# 1. 빌드 디렉토리 완전 삭제
./gradlew clean

# 2. 테스트만 실행하여 snippets 생성
./gradlew test --tests "*ControllerTest"

# 3. snippets 확인
ls -la build/generated-snippets/

# 4. 문서 생성
./gradlew asciidoctor -x test
```

### 테스트 실패 시

```bash
# 실패한 테스트 확인
./gradlew test --info

# 특정 테스트만 실행
./gradlew test --tests "YourControllerTest" --info
```

## 참고 자료

- [Spring REST Docs 공식 문서](https://docs.spring.io/spring-restdocs/docs/current/reference/html5/)
- [AsciiDoc 문법](https://docs.asciidoctor.org/asciidoc/latest/)
- [MockMvc 가이드](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html)
