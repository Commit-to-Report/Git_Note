package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubUserInfo;
import com.gitnote.backend.service.GitHubService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class WebController {

    @Value("${github.client.id}")
    private String clientId;
    @Value("${github.redirect.uri}")
    private String redirectUri;

    private final GitHubService gitHubService;

    // 생성자 통해 GitHubService 주입
    public WebController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    /**
     * 메인 페이지 진입
     * - 로그인 상태면 대시보드로, 아니면 로그인 뷰 반환
     */
    @GetMapping("/")
    public String home(HttpSession session) {
        if (isLoggedIn(session)) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    /**
     * 대시보드(성공) 페이지
     * - 로그인 안되어 있으면 로그인 페이지로 리다이렉트
     * - 사용자 정보 모델에 추가
     */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        GitHubUserInfo user = getSessionUser(session);
        if (user == null) {
            return "redirect:/";
        }
        model.addAttribute("user", user);
        return "success";
    }

    /**
     * 깃허브 OAUTH 로그인 버튼 클릭 시 인증 페이지로 리다이렉트
     */
    @GetMapping("/login/github")
    public RedirectView loginGithub() {
        // 인증 URL 구성
        String githubAuthUrl = String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=user:email,read:user",
                clientId, redirectUri
        );
        return new RedirectView(githubAuthUrl);
    }

    /**
     * GitHub 인증 콜백
     * - access token 발급 및 사용자 정보 세션 저장
     * - 실패 시 에러 페이지로
     */
    @GetMapping("/oauth/callback")
    public String oauthCallback(@RequestParam("code") String code, HttpSession session, Model model) {
        try {
            String accessToken = gitHubService.getAccessToken(code, redirectUri);

            if (accessToken == null) {
                model.addAttribute("error", "Failed to get access token from GitHub");
                return "error";
            }

            GitHubUserInfo userInfo = gitHubService.getUserInfo(accessToken);

            if (userInfo == null) {
                model.addAttribute("error", "Failed to get user information from GitHub");
                return "error";
            }

            // 세션에 사용자 및 토큰 저장
            session.setAttribute("user", userInfo);
            session.setAttribute("accessToken", accessToken);

            return "redirect:/dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Error during authentication: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 성공 페이지 (템플릿에서 사용)
     */
    @GetMapping("/success")
    public String success() {
        return "success";
    }

    /**
     * 에러 페이지 (템플릿에서 사용)
     */
    @GetMapping("/error")
    public String error() {
        return "error";
    }

    /**
     * 로그아웃
     * - GitHub 액세스 토큰 revoke 실행
     * - 세션 종료 및 쿠키 삭제
     * - 로그인 페이지로 이동
     */
    @GetMapping("/logout")
    public RedirectView logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            revokeAccessTokenAndInvalidateSession(session);
        }
        deleteAllCookies(request, response);
        return new RedirectView("/");
    }

    /* --------------------- 헬퍼 메서드 --------------------- */

    /**
     * 세션에 로그인된 GitHubUserInfo 반환
     */
    private GitHubUserInfo getSessionUser(HttpSession session) {
        Object userObj = session.getAttribute("user");
        return (userObj instanceof GitHubUserInfo) ? (GitHubUserInfo) userObj : null;
    }

    /**
     * 로그인 여부 확인
     */
    private boolean isLoggedIn(HttpSession session) {
        return getSessionUser(session) != null;
    }

    /**
     * accessToken revoke 및 세션 무효화
     */
    private void revokeAccessTokenAndInvalidateSession(HttpSession session) {
        String accessToken = (String) session.getAttribute("accessToken");
        if (accessToken != null) {
            gitHubService.revokeToken(accessToken);
        }
        session.invalidate();
    }

    /**
     * 요청 내 모든 쿠키 삭제
     */
    private void deleteAllCookies(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return;
        for (Cookie cookie : cookies) {
            cookie.setValue("");
            cookie.setPath("/");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }
    }
}
