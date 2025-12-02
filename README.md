# GitNote

GitNote는 GitHub OAuth를 통해 로그인하여 리포지토리의 커밋 내역을 조회하고, **AI 기반 자동 보고서 생성** 및 **AWS S3 백업** 기능을 제공하는 웹 애플리케이션입니다.

<br/>

## 목차

1. [프로젝트 소개](#1-프로젝트-소개)
2. [시스템 아키텍처](#2-시스템-아키텍처)
3. [사용 기술 스택](#3-사용-기술-스택)
4. [AWS 인프라 구성](#4-aws-인프라-구성)
5. [애플리케이션 구조](#5-애플리케이션-구조)
6. [배포 방법](#6-배포-방법)
7. [운영/모니터링 방법](#7-운영모니터링-방법)
8. [환경변수/설정 값](#8-환경변수설정-값)
9. [팀원 및 역할, 진행 과정](#9-팀원-및-역할-진행-과정)

<br/>

## 1. 프로젝트 소개

### 1.1 개요

GitNote는 개발자들이 GitHub 커밋 내역을 체계적으로 관리하고 분석할 수 있도록 도와주는 웹 애플리케이션입니다. GitHub OAuth 인증을 통해 사용자의 리포지토리에 접근하고, 커밋 내역을 조회하여 AI 기반 보고서를 자동으로 생성합니다.

### 1.2 주요 기능

- **GitHub OAuth 로그인**: GitHub 계정으로 간편 로그인
- **리포지토리 조회**: 사용자의 모든 GitHub 리포지토리 목록 조회
- **커밋 검색**: 날짜 범위를 지정한 커밋 내역 검색
- **커밋 상세 조회**: 커밋의 변경 파일 및 상세 정보 조회
- **AI 보고서 생성**: Google Gemini API를 활용한 스타일별 커밋 보고서 생성
- **자동 이메일 알림**: 설정한 주기(일/주/월)에 따라 보고서 생성 여부를 이메일로 자동 전송
- **AWS S3 백업**: 커밋 내역을 텍스트 파일로 S3에 저장
- **DynamoDB 저장**: 생성된 보고서를 DynamoDB에 저장하여 조회 및 수정 가능

### 1.3 사용 시나리오

1. 개발자가 GitHub에 로그인하여 자신의 리포지토리 커밋 내역을 확인
2. 특정 기간의 커밋을 조회하여 작업 내용을 정리
3. AI가 생성한 보고서를 통해 프로젝트 진행 상황 파악
4. 주기적으로 자동 생성되는 보고서를 이메일로 받아 확인
5. 중요한 커밋 내역을 S3에 백업하여 보관

<br/>

## 2. 시스템 아키텍처

### 2.1 전체 아키텍처

[첨부예정]

### 2.2 데이터 흐름

#### 2.2.1 사용자 인증 흐름

```
사용자 → CloudFront → Backend (ALB) → GitHub OAuth API
           ↓
        세션 생성
```

#### 2.2.2 커밋 조회 흐름

```
사용자 → CloudFront → Backend → GitHub API
           ↓
     커밋 데이터 반환
```

#### 2.2.3 자동 보고서 생성 흐름

```
EventBridge (스케줄러)
    ↓
Lambda Function
    ↓
DynamoDB (UserPreset 조회)
    ↓
Backend API 호출
    ↓
GitHub API (커밋 조회)
    ↓
Gemini API (보고서 생성)
    ↓
DynamoDB (보고서 저장)
    ↓
SES (이메일 전송)
```

### 2.3 컴포넌트 설명

- **CloudFront**: 정적 파일(프론트엔드) 및 API 요청 프록시
- **S3**: 프론트엔드 정적 파일 저장 및 커밋 로그 백업
- **ALB**: 백엔드 애플리케이션 로드 밸런서
- **ECS**: 백엔드 컨테이너 실행 환경
- **DynamoDB**: 사용자 설정 및 보고서 저장
- **Lambda**: 자동 보고서 생성 스케줄러
- **EventBridge**: Lambda 함수 스케줄링
- **SES**: 이메일 전송 서비스

<br/>

## 3. 사용 기술 스택

### 3.1 Backend

| 기술        | 버전  | 용도                                |
| ----------- | ----- | ----------------------------------- |
| Java        | 17+   | 백엔드 개발 언어                    |
| Spring Boot | 3.x   | 웹 애플리케이션 프레임워크          |
| AWS SDK     | 3.1.1 | AWS 서비스 연동 (S3, DynamoDB, SES) |
| Lombok      | -     | 보일러플레이트 코드 제거            |
| Gradle      | 8.x   | 빌드 도구                           |

### 3.2 Frontend

| 기술                 | 버전 | 용도            |
| -------------------- | ---- | --------------- |
| HTML5                | -    | 마크업          |
| CSS3                 | -    | 스타일링        |
| JavaScript (Vanilla) | ES6+ | 클라이언트 로직 |
| GitHub OAuth 2.0     | -    | 인증            |

### 3.3 Infrastructure

| 기술            | 용도                     |
| --------------- | ------------------------ |
| AWS CloudFront  | CDN 및 API 프록시        |
| AWS S3          | 정적 파일 호스팅 및 백업 |
| AWS ECS         | 컨테이너 오케스트레이션  |
| AWS ALB         | 로드 밸런싱              |
| AWS DynamoDB    | NoSQL 데이터베이스       |
| AWS Lambda      | 서버리스 함수 실행       |
| AWS EventBridge | 스케줄링                 |
| AWS SES         | 이메일 전송              |

### 3.4 External APIs

| API                | 용도                         |
| ------------------ | ---------------------------- |
| GitHub REST API v3 | 리포지토리 및 커밋 정보 조회 |
| Google Gemini API  | AI 기반 보고서 생성          |

<br/>

## 4. AWS 인프라 구성

### 4.1 인프라 구성도

[첨부예정]

### 4.2 주요 리소스

#### 4.2.1 CloudFront

- **Distribution ID**: `d1l3a7dvc3xbrk`
- **Origin**: S3 버킷 (프론트엔드)
- **Behavior**: `/api/*` → ALB (백엔드)
- **Viewer Protocol**: HTTPS Only

#### 4.2.2 S3

- **버킷**: 프론트엔드 정적 파일 저장
- **용도**: HTML, CSS, JavaScript 파일 호스팅

#### 4.2.3 ECS

- **클러스터**: 백엔드 애플리케이션 실행
- **서비스**: Spring Boot 애플리케이션
- **Task Definition**: Docker 컨테이너 실행

#### 4.2.4 ALB

- **타입**: Application Load Balancer
- **리스너**: HTTP (80)
- **타겟 그룹**: ECS 서비스

#### 4.2.5 DynamoDB

- **테이블 1**: `UserPreset` - 사용자 설정 저장
- **테이블 2**: `UserReports` - 생성된 보고서 저장

#### 4.2.6 Lambda

- **함수명**: `auto-report`
- **런타임**: Node.js 20
- **트리거**: EventBridge (스케줄)

#### 4.2.7 EventBridge

- **규칙**: 일/주/월별 보고서 생성 스케줄
- **타겟**: Lambda 함수

#### 4.2.8 SES

- **리전**: `ap-northeast-2`
- **용도**: 보고서 완료 이메일 전송

<br/>

## 5. 애플리케이션 구조

### 5.1 패키지 구조

```
com.gitnote.backend/
├── BackendApplication.java          # 메인 애플리케이션
├── config/                          # 설정 클래스
│   ├── DynamoDBConfig.java          # DynamoDB 설정
│   ├── S3Config.java                # S3 설정
│   ├── SESConfig.java               # SES 설정
│   └── WebConfig.java               # CORS 설정
├── controller/                      # REST API 컨트롤러
│   ├── AutoReportController.java    # 자동 보고서 생성 API
│   ├── CommitController.java        # 커밋 조회 API
│   ├── DDBReportController.java     # DynamoDB 보고서 API
│   ├── GitHubOAuthController.java  # OAuth 인증 API
│   ├── HealthCheckController.java   # 헬스 체크 API
│   ├── S3Controller.java            # S3 업로드 API
│   ├── S3ReportController.java      # S3 보고서 API
│   ├── UserPresetController.java    # 사용자 설정 API
│   └── WebController.java           # 웹 페이지 컨트롤러
├── service/                         # 비즈니스 로직
│   ├── DDBReportService.java        # DynamoDB 보고서 서비스
│   ├── EmailService.java            # 이메일 전송 서비스
│   ├── GeminiApiService.java        # Gemini API 서비스
│   ├── GitHubService.java           # GitHub API 서비스
│   ├── S3Service.java               # S3 서비스
│   └── UserPresetService.java       # 사용자 설정 서비스
├── repository/                      # 데이터 접근 계층
│   └── UserPresetRepository.java    # UserPreset 리포지토리
├── entity/                          # 도메인 엔티티
│   ├── ReportFrequency.java         # 보고서 주기 Enum
│   └── UserPreset.java              # 사용자 설정 엔티티
├── dto/                             # 데이터 전송 객체
│   ├── GitHubCommit.java            # GitHub 커밋 DTO
│   ├── GitHubRepository.java        # GitHub 리포지토리 DTO
│   ├── GitHubUserInfo.java          # GitHub 사용자 정보 DTO
│   ├── S3UploadRequest.java         # S3 업로드 요청 DTO
│   ├── UserPresetRequest.java       # 사용자 설정 요청 DTO
│   └── UserPresetResponse.java      # 사용자 설정 응답 DTO
└── exception/                       # 예외 처리
    └── GlobalExceptionHandler.java  # 전역 예외 핸들러
```

### 5.2 주요 기능 모듈

#### 5.2.1 인증 모듈

- **GitHubOAuthController**: OAuth 인증 처리
- **GitHubService**: GitHub API 연동
- **세션 관리**: HttpSession을 통한 사용자 세션 관리

#### 5.2.2 커밋 조회 모듈

- **CommitController**: 커밋 조회 API
- **GitHubService**: GitHub API를 통한 커밋 데이터 조회

#### 5.2.3 보고서 생성 모듈

- **AutoReportController**: 자동 보고서 생성 API
- **GeminiApiService**: AI 기반 보고서 생성
- **DDBReportService**: 보고서 저장

#### 5.2.4 이메일 모듈

- **EmailService**: SES를 통한 이메일 전송
- **AutoReportController**: 보고서 완료 시 이메일 알림

#### 5.2.5 저장소 모듈

- **S3Service**: S3 파일 업로드 및 Presigned URL 생성
- **DDBReportService**: DynamoDB 보고서 저장/조회

#### 5.2.6 사용자 설정 모듈

- **UserPresetController**: 사용자 설정 API
- **UserPresetService**: 사용자 설정 비즈니스 로직
- **UserPresetRepository**: DynamoDB 데이터 접근

### 5.3 레이어 아키텍처

```
┌─────────────────────────────────┐
│      Controller Layer           │  ← REST API 엔드포인트
├─────────────────────────────────┤
│      Service Layer              │  ← 비즈니스 로직
├─────────────────────────────────┤
│      Repository Layer           │  ← 데이터 접근
├─────────────────────────────────┤
│      Entity/DTO Layer           │  ← 도메인 모델
└─────────────────────────────────┘
```

<br/>

## 6. 배포 방법

### 6.1 로컬 개발 환경 설정

#### 6.1.1 필수 요구사항

- Java 17+
- Gradle 8.x
- Python 3.x (프론트엔드 서버용)
- AWS 계정 및 자격 증명

#### 6.1.2 환경 변수 설정

`.env` 파일 생성 (로컬 개발용):

```bash
# AWS 설정
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
AWS_S3_BUCKET=your-bucket-name
AWS_REGION=ap-northeast-2

# GitHub OAuth
GITHUB_CLIENT_SECRET=your-github-client-secret

# Gemini API
GEMINI_PROJECT_ID=your-project-id
GEMINI_API_KEY=your-api-key

# Frontend URL (로컬)
FRONTEND_URL=http://localhost:5173
```

#### 6.1.3 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

또는 IntelliJ IDEA에서 `BackendApplication.java` 실행

#### 6.1.4 프론트엔드 실행

```bash
cd frontend
python3 -m http.server 5173
```

### 6.2 Docker 빌드 및 배포

#### 6.2.1 Docker 이미지 빌드

```bash
cd backend
./gradlew bootJar
docker build -t gitnote-backend:latest .
```

#### 6.2.2 ECR에 이미지 푸시

```bash
# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com

# 태그 지정
docker tag gitnote-backend:latest <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com/gitnote-backend:latest

# 푸시
docker push <account-id>.dkr.ecr.ap-northeast-2.amazonaws.com/gitnote-backend:latest
```

#### 6.2.3 ECS Task Definition 업데이트

1. AWS 콘솔 > ECS > Task Definitions
2. 기존 Task Definition 선택
3. "Create new revision" 클릭
4. Container image URL 업데이트
5. 환경 변수 확인/수정
6. "Create" 클릭

#### 6.2.4 ECS 서비스 업데이트

1. AWS 콘솔 > ECS > Clusters
2. 클러스터 선택
3. 서비스 선택
4. "Update" 클릭
5. 새 Task Definition revision 선택
6. "Force new deployment" 체크
7. "Update" 클릭

### 6.3 Lambda 함수 배포

#### 6.3.1 패키지 준비

```bash
cd lambda/auto-report
npm install
zip -r function.zip index.js package.json node_modules/
```

#### 6.3.2 Lambda 함수 업데이트

```bash
aws lambda update-function-code \
  --function-name auto-report \
  --zip-file fileb://function.zip \
  --region ap-northeast-2
```

#### 6.3.3 환경 변수 설정

```bash
aws lambda update-function-configuration \
  --function-name auto-report \
  --environment Variables="{
    USER_PRESET_TABLE=UserPreset,
    BACKEND_API_URL=http://your-alb-url
  }" \
  --region ap-northeast-2
```

### 6.4 프론트엔드 배포

#### 6.4.1 S3에 업로드

```bash
aws s3 sync frontend/ s3://your-bucket-name/ --delete
```

#### 6.4.2 CloudFront 캐시 무효화

```bash
aws cloudfront create-invalidation \
  --distribution-id d1l3a7dvc3xbrk \
  --paths "/*"
```

<br/>

## 7. 운영/모니터링 방법

### 7.1 로그 확인

#### 7.1.1 ECS 로그 (CloudWatch Logs)

```bash
# 로그 그룹 확인
aws logs describe-log-groups --log-group-name-prefix /ecs/gitnote

# 최근 로그 확인
aws logs tail /ecs/gitnote-backend --follow
```

#### 7.1.2 Lambda 로그

```bash
# CloudWatch Logs에서 확인
aws logs tail /aws/lambda/auto-report --follow
```

### 7.2 모니터링 지표

#### 7.2.1 ECS 모니터링

- CPU 사용률
- 메모리 사용률
- Task 실행 상태
- 서비스 상태

#### 7.2.2 Lambda 모니터링

- 함수 실행 횟수
- 실행 시간
- 에러율
- 타임아웃 발생 횟수

#### 7.2.3 DynamoDB 모니터링

- 읽기/쓰기 용량 사용률
- 에러율
- 지연 시간

### 7.3 알람 설정

#### 7.3.1 CloudWatch Alarms

```bash
# ECS 서비스 에러 알람
aws cloudwatch put-metric-alarm \
  --alarm-name ecs-service-errors \
  --alarm-description "ECS 서비스 에러 발생" \
  --metric-name Errors \
  --namespace AWS/ECS \
  --statistic Sum \
  --period 300 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold
```

### 7.4 헬스 체크

#### 7.4.1 백엔드 헬스 체크

```bash
curl https://d1l3a7dvc3xbrk.cloudfront.net/api/health
```

#### 7.4.2 Lambda 함수 테스트

```bash
aws lambda invoke \
  --function-name auto-report \
  --payload '{"frequency":"DAILY"}' \
  response.json
```

## 8. 환경변수/설정 값

### 8.1 필수 환경 변수

#### 8.1.1 Backend (ECS Task Definition)

| 변수명                 | 설명                       | 예시 값                                    |
| ---------------------- | -------------------------- | ------------------------------------------ |
| `AWS_ACCESS_KEY`       | AWS 액세스 키              | `AKIAIOSFODNN7EXAMPLE`                     |
| `AWS_SECRET_KEY`       | AWS 시크릿 키              | `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` |
| `AWS_S3_BUCKET`        | S3 버킷 이름               | `gitnote-bucket`                           |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth Client Secret | `your-client-secret`                       |
| `GEMINI_PROJECT_ID`    | Gemini 프로젝트 ID         | `your-project-id`                          |
| `GEMINI_API_KEY`       | Gemini API 키              | `your-api-key`                             |
| `FRONTEND_URL`         | 프론트엔드 URL             | `https://d1l3a7dvc3xbrk.cloudfront.net`    |

#### 8.1.2 Lambda Function

| 변수명              | 설명                 | 예시 값               |
| ------------------- | -------------------- | --------------------- |
| `USER_PRESET_TABLE` | DynamoDB 테이블 이름 | `UserPreset`          |
| `BACKEND_API_URL`   | 백엔드 API URL       | `http://your-alb-url` |

### 8.2 application.properties 설정

```properties
# Spring Profile
spring.profiles.active=dev

# Session Configuration
server.servlet.session.timeout=3600s
server.servlet.session.cookie.name=GITNOTE_SESSION
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=false
server.servlet.session.cookie.same-site=lax

# Frontend Configuration
frontend.url=${FRONTEND_URL:http://localhost:5173}

# GitHub OAuth Configuration
github.client.id=${GITHUB_CLIENT_ID:Iv23li9z4Gvt8aMMuB5w}
github.client.secret=${GITHUB_CLIENT_SECRET}
github.redirect.uri=${frontend.url}/callback.html

# Gemini API Configuration
gemini.project.id=${GEMINI_PROJECT_ID}
gemini.api.key=${GEMINI_API_KEY}
gemini.api.model=gemini-2.5-flash
gemini.api.location=us-central1

# AWS Configuration
spring.cloud.aws.credentials.access-key=${AWS_ACCESS_KEY}
spring.cloud.aws.credentials.secret-key=${AWS_SECRET_KEY}
spring.cloud.aws.region.static=${AWS_REGION:ap-northeast-2}
spring.cloud.aws.s3.bucket=${AWS_S3_BUCKET}

# DynamoDB Configuration
aws.dynamodb.table.user-preset=${DYNAMODB_USER_PRESET_TABLE:UserPreset}
aws.dynamodb.table.user-reports=${DYNAMODB_USER_REPORTS_TABLE:UserReports}

# SES Configuration
aws.ses.sender-email=${SES_SENDER_EMAIL:pkrystal.dev@gmail.com}
aws.ses.sender-name=${SES_SENDER_NAME:GitNote}
```

### 8.3 .env.sample 파일

프로젝트 루트에 `.env.sample` 파일을 생성하여 필수 환경 변수 목록을 제공합니다:

```bash
# AWS 설정
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
AWS_S3_BUCKET=your-bucket-name
AWS_REGION=ap-northeast-2

# GitHub OAuth
GITHUB_CLIENT_ID=Iv23li9z4Gvt8aMMuB5w
GITHUB_CLIENT_SECRET=your-github-client-secret

# Gemini API
GEMINI_PROJECT_ID=your-project-id
GEMINI_API_KEY=your-api-key

# Frontend URL
FRONTEND_URL=http://localhost:5173

# DynamoDB 테이블 이름 (선택사항)
DYNAMODB_USER_PRESET_TABLE=UserPreset
DYNAMODB_USER_REPORTS_TABLE=UserReports

# SES 설정 (선택사항)
SES_SENDER_EMAIL=your-email@gmail.com
SES_SENDER_NAME=GitNote
```

## 9. 팀원 및 역할, 진행 과정

### 9.1 역할 분담

#### 9.1.1 PM/아키텍트(김민욱)

- 프로젝트 소개 작성
- 시스템 아키텍처 설계 및 문서화
- AWS 인프라 구성 설계

#### 9.1.2 인프라 엔지니어(이재윤)

- AWS 인프라 구성 및 배포
- CloudFront, S3, ECS, ALB 설정
- Lambda 및 EventBridge 구성
- 모니터링 및 알람 설정

#### 9.1.3 백엔드 / 프론트엔드 개발자(박수정, 송민지, 최유경)

- 애플리케이션 구조 설계 및 구현
- 패키지 구조 정리
- 도메인 모델 설계
- API 엔드포인트 개발
- 환경 변수 및 설정 관리

#### 9.1.4 DevOps 엔지니어(김지윤)

- Docker 이미지 빌드 및 ECR 배포
- ECS 배포 파이프라인 구축
- CI/CD 파이프라인 설정
- 운영 및 모니터링 방법 수립

### 9.2 진행 과정

#### Phase 1: 기획 및 설계

- 프로젝트 요구사항 정의
- 시스템 아키텍처 설계
- 기술 스택 선정

#### Phase 2: 개발 환경 구축

- 로컬 개발 환경 설정
- AWS 인프라 초기 구성
- CI/CD 파이프라인 구축

#### Phase 3: 핵심 기능 개발

- GitHub OAuth 인증 구현
- 커밋 조회 기능 개발
- AI 보고서 생성 기능 개발
- 자동 보고서 스케줄링 구현

#### Phase 4: 배포 및 운영

- 프로덕션 환경 배포
- 모니터링 설정
- 문서화 완료

## 참고 자료

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [AWS ECS 문서](https://docs.aws.amazon.com/ecs/)
- [AWS Lambda 문서](https://docs.aws.amazon.com/lambda/)
- [GitHub API 문서](https://docs.github.com/en/rest)
- [Google Gemini API 문서](https://ai.google.dev/docs)
