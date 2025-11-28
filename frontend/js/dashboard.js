const API_BASE_URL = "http://localhost:8080";

// 전역 변수로 GitHub 이메일 저장
let githubEmail = "";

// 페이지 로드 시 사용자 정보 가져오기
window.addEventListener("load", async () => {
  const code = localStorage.getItem("github_code");

  if (!code) {
    window.location.href = "index.html";
    return;
  }

  try {
    // 백엔드 API로 사용자 정보 요청 (credentials 포함해서 세션 쿠키 받기)
    const response = await fetch(
      `${API_BASE_URL}/api/github/user?code=${code}`,
      {
        credentials: "include" // 세션 쿠키를 받고 저장하기 위해 필수!
      }
    );

    if (!response.ok) {
      throw new Error("Failed to fetch user info");
    }

    const user = await response.json();

    console.log("✅ 로그인 성공! 세션이 생성되었습니다.");
    console.log("User:", user.login);

    // GitHub 이메일 저장
    githubEmail = user.email || "";

    // 사용자 정보를 로컬 스토리지에 저장
    localStorage.setItem("user_info", JSON.stringify(user));
    localStorage.removeItem("github_code"); // code는 한 번만 사용

    // 세션 스토리지에도 저장 (커밋 페이지에서 사용)
    sessionStorage.setItem("username", user.login);
    sessionStorage.setItem("avatar", user.avatarUrl);

    // UI 업데이트
    displayUserInfo(user);

    // User Preset 초기화 및 불러오기
    initializePresetUI();
    loadUserPreset();
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
  document.getElementById("userId").textContent = user.id;
  document.getElementById("login").textContent = user.login;

  if (user.email) {
    document.getElementById("emailRow").style.display = "flex";
    document.getElementById("email").textContent = user.email;
  }

  document.getElementById("repos").textContent = user.publicRepos;
  document.getElementById("followers").textContent = user.followers;
  document.getElementById("following").textContent = user.following;
  document.getElementById("created").textContent = new Date(
    user.createdAt
  ).toLocaleDateString("ko-KR");
}

// 로그아웃 버튼 핸들러
document.getElementById("logoutBtn").addEventListener("click", async () => {
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
      document.getElementById("autoReportEnabled").checked = preset.autoReportEnabled || false;
      togglePresetOptions();

      // 이메일 알림
      document.getElementById("emailNotificationEnabled").checked = preset.emailNotificationEnabled || false;
      toggleEmailInfo();

      // 보고서 스타일 버튼 선택
      if (preset.reportStyle) {
        selectStyleButton(preset.reportStyle);
      }

      // 보고서 생성 주기 버튼 선택
      if (preset.reportFrequency) {
        selectFrequencyButton(preset.reportFrequency);
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
    optionsEl.style.display = "flex";
  } else {
    optionsEl.style.display = "none";
  }
}

// 이메일 알림 토글 시 이메일 정보 표시/숨김
function toggleEmailInfo() {
  const isEnabled = document.getElementById("emailNotificationEnabled").checked;
  const emailInfoEl = document.getElementById("emailInfo");

  if (isEnabled) {
    emailInfoEl.style.display = "block";
  } else {
    emailInfoEl.style.display = "none";
  }
}

// 보고서 스타일 버튼 선택
function selectStyleButton(style) {
  const buttons = document.querySelectorAll('[data-style]');
  buttons.forEach(btn => {
    btn.classList.remove('selected');
    if (btn.dataset.style === style) {
      btn.classList.add('selected');
    }
  });
}

// 보고서 생성 주기 버튼 선택
function selectFrequencyButton(frequency) {
  const buttons = document.querySelectorAll('[data-frequency]');
  buttons.forEach(btn => {
    btn.classList.remove('selected');
    if (btn.dataset.frequency === frequency) {
      btn.classList.add('selected');
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
      autoReportToggle.addEventListener("change", function() {
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
    const styleButtons = document.querySelectorAll('[data-style]');
    console.log("스타일 버튼 개수:", styleButtons.length);
    styleButtons.forEach(button => {
      button.addEventListener('click', () => {
        selectStyleButton(button.dataset.style);
      });
    });

    // 보고서 생성 주기 버튼 클릭 이벤트
    const frequencyButtons = document.querySelectorAll('[data-frequency]');
    console.log("주기 버튼 개수:", frequencyButtons.length);
    frequencyButtons.forEach(button => {
      button.addEventListener('click', () => {
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
    const selectedStyleBtn = document.querySelector('[data-style].selected');
    const reportStyle = selectedStyleBtn ? selectedStyleBtn.dataset.style : null;

    // 선택된 주기 찾기
    const selectedFrequencyBtn = document.querySelector('[data-frequency].selected');
    const reportFrequency = selectedFrequencyBtn ? selectedFrequencyBtn.dataset.frequency : null;

    const presetData = {
      autoReportEnabled: document.getElementById("autoReportEnabled").checked,
      email: githubEmail || null, // GitHub 이메일 사용, 없으면 null
      emailNotificationEnabled: document.getElementById("emailNotificationEnabled").checked,
      reportStyle: reportStyle,
      reportFrequency: reportFrequency,
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
