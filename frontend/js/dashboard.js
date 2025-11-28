const API_BASE_URL = "http://localhost:8080";

// 전역 변수로 GitHub 이메일 저장
let githubEmail = "";

// 다크모드 초기화 - 즉시 실행하여 깜빡임 방지
(function initThemeImmediately() {
  const savedTheme = localStorage.getItem("theme") || "light";
  document.documentElement.setAttribute("data-theme", savedTheme);
})();

// 테마 토글
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

// 테마 버튼 초기화
function initThemeButton() {
  const savedTheme = localStorage.getItem("theme") || "light";
  updateThemeButton(savedTheme);

  const themeToggle = document.getElementById("themeToggle");
  if (themeToggle) {
    // 기존 이벤트 리스너 제거 (중복 방지)
    const newThemeToggle = themeToggle.cloneNode(true);
    themeToggle.parentNode.replaceChild(newThemeToggle, themeToggle);

    // 새 이벤트 리스너 등록
    newThemeToggle.addEventListener("click", toggleTheme);
    console.log("✅ 다크모드 토글 버튼 초기화 완료");
  } else {
    console.warn("⚠️ 테마 토글 버튼을 찾을 수 없습니다.");
  }
}

// 페이지 로드 시 사용자 정보 가져오기
window.addEventListener("load", async () => {
  const code = localStorage.getItem("github_code");
  const savedUserInfo = localStorage.getItem("user_info");

  try {
    let user;

    // 1. code가 있으면 최초 로그인 프로세스
    if (code) {
      console.log("🔑 GitHub code로 로그인 중...");
      const response = await fetch(
        `${API_BASE_URL}/api/github/user?code=${code}`,
        {
          credentials: "include",
        }
      );

      if (!response.ok) {
        throw new Error("Failed to fetch user info");
      }

      user = await response.json();
      console.log("✅ 로그인 성공! 세션이 생성되었습니다.");

      // 사용자 정보 저장
      localStorage.setItem("user_info", JSON.stringify(user));
      localStorage.removeItem("github_code");
    }
    // 2. 저장된 사용자 정보가 있으면 세션 확인
    else if (savedUserInfo) {
      console.log("🔍 저장된 세션 확인 중...");

      // 세션이 유효한지 체크
      const sessionResponse = await fetch(`${API_BASE_URL}/api/user/session`, {
        credentials: "include",
      });

      if (sessionResponse.ok) {
        // 세션이 유효하면 저장된 정보 사용
        user = JSON.parse(savedUserInfo);
        console.log("✅ 세션 유효! 저장된 사용자 정보 사용");
      } else {
        // 세션이 만료되었으면 로그인 페이지로
        console.log("❌ 세션 만료");
        localStorage.removeItem("user_info");
        window.location.href = "index.html";
        return;
      }
    }
    // 3. code도 없고 저장된 정보도 없으면 로그인 페이지로
    else {
      console.log("❌ 로그인 정보 없음");
      window.location.href = "index.html";
      return;
    }

    // GitHub 이메일 저장
    githubEmail = user.email || "";

    // 세션 스토리지에도 저장 (커밋 페이지에서 사용)
    sessionStorage.setItem("username", user.login);
    sessionStorage.setItem("avatar", user.avatarUrl);

    // UI 업데이트
    displayUserInfo(user);

    // User Preset 초기화 및 불러오기
    initializePresetUI();
    loadUserRepositories();
    loadUserPreset();

    // 로그아웃 버튼 이벤트 리스너 등록
    initializeLogoutButton();

    // Dashboard 전용 헤더 커스터마이즈
    customizeDashboardHeader();
  } catch (error) {
    console.error("Error:", error);
    document.getElementById("loading").style.display = "none";
    document.getElementById("error").style.display = "block";
    document.getElementById("error").textContent =
      "사용자 정보를 가져오는데 실패했습니다.";
  }
});

function displayUserInfo(user) {
  // 로딩 숨기기
  document.getElementById("loading").style.display = "none";
  document.getElementById("userProfile").style.display = "block";

  // 프로필 이미지
  document.getElementById("avatar").src = user.avatarUrl;

  // 기본 정보
  document.getElementById("name").textContent = user.name || user.login;
  document.getElementById("username").textContent = `@${user.login}`;

  // 선택적 정보
  if (user.bio) {
    document.getElementById("bio").textContent = user.bio;
  } else {
    document.getElementById("bio").style.display = "none";
  }

  if (user.location) {
    document.getElementById("location").textContent = user.location;
  } else {
    document.getElementById("location").style.display = "none";
  }

  if (user.company) {
    document.getElementById("company").textContent = user.company;
  } else {
    document.getElementById("company").style.display = "none";
  }

  // 상세 정보
  if (user.email) {
    document.getElementById("emailRow").style.display = "block";
    document.getElementById("email").textContent = user.email;
  }

  document.getElementById("repos").textContent = user.publicRepos;
  document.getElementById("created").textContent = new Date(
    user.createdAt
  ).toLocaleDateString("ko-KR");
}

// Dashboard 전용 헤더 커스터마이즈
function customizeDashboardHeader() {
  const header = document.querySelector(".app-header");
  if (!header) {
    console.warn("헤더를 찾을 수 없습니다.");
    return;
  }

  // 이미 로고가 있으면 추가하지 않음
  if (header.querySelector(".logo-section")) {
    return;
  }

  // 로고 섹션 생성
  const logoSection = document.createElement("div");
  logoSection.className = "logo-section";
  logoSection.innerHTML = `
    <a href="dashboard.html">
      <img src="img/logo_text.png" alt="GitNote Logo" class="header-logo">
    </a>
  `;

  // 헤더의 맨 앞에 로고 추가
  header.insertBefore(logoSection, header.firstChild);

  // 다크모드 토글 버튼을 헤더에 추가
  const userSection = header.querySelector(".user-section");
  if (userSection) {
    // 다크모드 토글 버튼 생성
    const themeToggleBtn = document.createElement("button");
    themeToggleBtn.id = "themeToggle";
    themeToggleBtn.className = "theme-toggle";
    themeToggleBtn.setAttribute("aria-label", "테마 전환");
    themeToggleBtn.innerHTML = `
      <span id="themeIcon">🌙</span>
      <span id="themeText">다크모드</span>
    `;

    // 로그아웃 버튼 앞에 추가
    const logoutBtn = userSection.querySelector("#logoutBtn");
    if (logoutBtn) {
      userSection.insertBefore(themeToggleBtn, logoutBtn);
    } else {
      userSection.appendChild(themeToggleBtn);
    }

    // 테마 버튼 초기화
    initThemeButton();
  }
}

// 로그아웃 버튼 초기화
function initializeLogoutButton() {
  const logoutBtn = document.getElementById("logoutBtn");

  if (!logoutBtn) {
    console.warn("logoutBtn을 찾을 수 없습니다.");
    return;
  }

  // header.js에서 등록한 이벤트를 제거하고 새로 등록
  const newLogoutBtn = logoutBtn.cloneNode(true);
  logoutBtn.parentNode.replaceChild(newLogoutBtn, logoutBtn);

  newLogoutBtn.addEventListener("click", async (e) => {
    e.preventDefault();

    try {
      // 로컬 스토리지 클리어
      localStorage.removeItem("user_info");
      localStorage.removeItem("github_code");

      // 백엔드 로그아웃 엔드포인트 호출 (선택적)
      await fetch(`${API_BASE_URL}/api/logout`, {
        method: "POST",
        credentials: "include",
      });

      // 로그인 페이지로 이동
      window.location.href = "index.html";
    } catch (error) {
      console.error("Logout error:", error);
      // 에러가 있어도 로그인 페이지로 이동
      window.location.href = "index.html";
    }
  });
}

// 사용자의 리포지토리 목록 불러오기
async function loadUserRepositories() {
  const repositoryLoadingEl = document.getElementById("repositoryLoading");
  const repositorySelectEl = document.getElementById("repositorySelect");

  try {
    repositoryLoadingEl.style.display = "block";

    const response = await fetch(`${API_BASE_URL}/api/github/repositories`, {
      credentials: "include",
    });

    if (!response.ok) {
      throw new Error("Failed to fetch repositories");
    }

    const data = await response.json();
    const repositories = data.repositories || [];
    console.log("✅ 리포지토리 목록:", repositories);
    console.log("✅ 첫 번째 리포지토리:", repositories[0]);

    // 드롭다운에 리포지토리 추가
    repositories.forEach((repo) => {
      console.log("리포지토리:", repo);
      const option = document.createElement("option");
      // full_name 또는 fullName 모두 시도
      const fullName = repo.full_name || repo.fullName;
      option.value = fullName;
      option.textContent = `${fullName} ${repo.private ? "🔒" : ""}`;
      repositorySelectEl.appendChild(option);
    });
  } catch (error) {
    console.error("Error loading repositories:", error);
  } finally {
    repositoryLoadingEl.style.display = "none";
  }
}

// User Preset 불러오기
async function loadUserPreset() {
  const presetLoadingEl = document.getElementById("presetLoading");
  const presetErrorEl = document.getElementById("presetError");

  // GitHub 이메일 표시
  if (githubEmail) {
    document.getElementById("userEmail").textContent = githubEmail;
  }

  try {
    presetLoadingEl.style.display = "block";
    presetErrorEl.style.display = "none";

    const response = await fetch(`${API_BASE_URL}/api/user/preset`, {
      credentials: "include",
    });

    if (response.ok) {
      const preset = await response.json();

      // 자동 보고서 생성
      document.getElementById("autoReportEnabled").checked =
        preset.autoReportEnabled || false;
      togglePresetOptions();

      // 이메일 알림
      document.getElementById("emailNotificationEnabled").checked =
        preset.emailNotificationEnabled || false;
      toggleEmailInfo();

      // 보고서 스타일 버튼 선택
      if (preset.reportStyle) {
        selectStyleButton(preset.reportStyle);
      }

      // 보고서 생성 주기 버튼 선택
      if (preset.reportFrequency) {
        selectFrequencyButton(preset.reportFrequency);
      }

      // 리포지토리 선택
      if (preset.repository) {
        document.getElementById("repositorySelect").value = preset.repository;
      }

      console.log("✅ User Preset 불러오기 성공", preset);
    } else if (response.status === 404) {
      // 설정이 없는 경우 (정상)
      console.log("ℹ️ 저장된 설정이 없습니다.");
    } else {
      throw new Error("Failed to load preset");
    }
  } catch (error) {
    console.error("Error loading preset:", error);
    presetErrorEl.style.display = "block";
    presetErrorEl.textContent = "설정을 불러오는데 실패했습니다.";
  } finally {
    presetLoadingEl.style.display = "none";
  }
}

// 자동 보고서 생성 토글 시 옵션 표시/숨김
function togglePresetOptions() {
  const isEnabled = document.getElementById("autoReportEnabled").checked;
  const optionsEl = document.getElementById("presetOptions");

  console.log("togglePresetOptions 호출:", isEnabled, optionsEl);

  if (isEnabled) {
    optionsEl.style.display = "block";
    // 애니메이션을 위한 클래스 추가
    setTimeout(() => optionsEl.classList.add("show"), 10);
  } else {
    optionsEl.classList.remove("show");
    setTimeout(() => {
      optionsEl.style.display = "none";
    }, 300);
  }
}

// 이메일 알림 토글 시 이메일 정보 표시/숨김
function toggleEmailInfo() {
  const isEnabled = document.getElementById("emailNotificationEnabled").checked;
  const emailInfoEl = document.getElementById("emailInfo");

  if (isEnabled) {
    emailInfoEl.style.display = "flex";
  } else {
    emailInfoEl.style.display = "none";
  }
}

// 보고서 스타일 버튼 선택
function selectStyleButton(style) {
  const buttons = document.querySelectorAll("[data-style]");
  buttons.forEach((btn) => {
    btn.classList.remove("selected");
    if (btn.dataset.style === style) {
      btn.classList.add("selected");
    }
  });
}

// 보고서 생성 주기 버튼 선택
function selectFrequencyButton(frequency) {
  const buttons = document.querySelectorAll("[data-frequency]");
  buttons.forEach((btn) => {
    btn.classList.remove("selected");
    if (btn.dataset.frequency === frequency) {
      btn.classList.add("selected");
    }
  });
}

// User Preset UI 초기화 (이벤트 리스너 등록)
function initializePresetUI() {
  console.log("🔧 initializePresetUI 호출됨");

  try {
    // 자동 보고서 생성 토글 이벤트
    const autoReportToggle = document.getElementById("autoReportEnabled");
    console.log("autoReportEnabled 요소:", autoReportToggle);

    if (autoReportToggle) {
      autoReportToggle.addEventListener("change", function () {
        console.log("✅ 자동 보고서 생성 토글 변경됨:", this.checked);
        togglePresetOptions();
      });
      console.log("✅ 토글 이벤트 리스너 등록 완료");
    } else {
      console.error("❌ autoReportEnabled 요소를 찾을 수 없습니다!");
      return;
    }

    // 이메일 알림 토글 이벤트
    const emailToggle = document.getElementById("emailNotificationEnabled");
    if (emailToggle) {
      emailToggle.addEventListener("change", toggleEmailInfo);
      console.log("✅ 이메일 알림 토글 이벤트 등록 완료");
    }

    // 보고서 스타일 버튼 클릭 이벤트
    const styleButtons = document.querySelectorAll("[data-style]");
    console.log("스타일 버튼 개수:", styleButtons.length);
    styleButtons.forEach((button) => {
      button.addEventListener("click", () => {
        selectStyleButton(button.dataset.style);
      });
    });

    // 보고서 생성 주기 버튼 클릭 이벤트
    const frequencyButtons = document.querySelectorAll("[data-frequency]");
    console.log("주기 버튼 개수:", frequencyButtons.length);
    frequencyButtons.forEach((button) => {
      button.addEventListener("click", () => {
        selectFrequencyButton(button.dataset.frequency);
      });
    });

    // 저장 버튼 이벤트
    const saveBtn = document.getElementById("savePresetBtn");
    if (saveBtn) {
      saveBtn.addEventListener("click", saveUserPreset);
      console.log("✅ 저장 버튼 이벤트 등록 완료");
    }

    console.log("✅ 모든 이벤트 리스너 등록 완료!");
  } catch (error) {
    console.error("❌ initializePresetUI 에러:", error);
  }
}

// User Preset 저장 함수
async function saveUserPreset() {
  const saveStatusEl = document.getElementById("saveStatus");
  const presetErrorEl = document.getElementById("presetError");

  try {
    saveStatusEl.textContent = "저장 중...";
    saveStatusEl.style.color = "#666";
    presetErrorEl.style.display = "none";

    // 선택된 스타일 찾기
    const selectedStyleBtn = document.querySelector("[data-style].selected");
    const reportStyle = selectedStyleBtn
      ? selectedStyleBtn.dataset.style
      : null;

    // 선택된 주기 찾기
    const selectedFrequencyBtn = document.querySelector(
      "[data-frequency].selected"
    );
    const reportFrequency = selectedFrequencyBtn
      ? selectedFrequencyBtn.dataset.frequency
      : null;

    const selectedRepository =
      document.getElementById("repositorySelect").value;

    const presetData = {
      autoReportEnabled: document.getElementById("autoReportEnabled").checked,
      email: githubEmail || null, // GitHub 이메일 사용, 없으면 null
      emailNotificationEnabled: document.getElementById(
        "emailNotificationEnabled"
      ).checked,
      reportStyle: reportStyle,
      reportFrequency: reportFrequency,
      repository: selectedRepository || null,
    };

    console.log("📤 전송할 데이터:", presetData);

    const response = await fetch(`${API_BASE_URL}/api/user/preset`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(presetData),
    });

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.error || "저장 실패");
    }

    const savedPreset = await response.json();
    console.log("✅ User Preset 저장 성공", savedPreset);

    saveStatusEl.textContent = "✓ 저장되었습니다!";
    saveStatusEl.style.color = "#28a745";

    // 3초 후 메시지 제거
    setTimeout(() => {
      saveStatusEl.textContent = "";
    }, 3000);
  } catch (error) {
    console.error("Error saving preset:", error);
    saveStatusEl.textContent = "";
    presetErrorEl.style.display = "block";
    presetErrorEl.textContent = `저장 실패: ${error.message}`;
  }
}
