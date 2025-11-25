# GitNote 실행 및 서버 관리 가이드

## 1. Backend 서버 실행

```sh
cd [프로젝트 경로]/Git_Note/backend
./gradlew bootRun
```

## 2. Frontend 서버 실행 (새 터미널에서)

```sh
cd [프로젝트 경로]/Git_Note/frontend
python3 -m http.server 5173
```

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
