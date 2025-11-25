package com.gitnote.backend.controller;

import com.gitnote.backend.dto.GitHubUserInfo;
import com.gitnote.backend.service.GitHubService;
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

    public WebController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        // 이미 로그인되어 있으면 success 페이지로 리다이렉트
        GitHubUserInfo user = (GitHubUserInfo) session.getAttribute("user");
        if (user != null) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        // 로그인 안되어 있으면 로그인 페이지로
        GitHubUserInfo user = (GitHubUserInfo) session.getAttribute("user");
        if (user == null) {
            return "redirect:/";
        }
        model.addAttribute("user", user);
        return "success";
    }

    @GetMapping("/login/github")
    public RedirectView loginGithub() {
        String githubAuthUrl = String.format(
            "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=user:email,read:user",
            clientId,
            redirectUri
        );
        return new RedirectView(githubAuthUrl);
    }

    @GetMapping("/oauth/callback")
    public String oauthCallback(@RequestParam("code") String code, HttpSession session, Model model) {
        try {
            // Access Token 가져오기
            String accessToken = gitHubService.getAccessToken(code);

            if (accessToken == null) {
                model.addAttribute("error", "Failed to get access token from GitHub");
                return "error";
            }

            // 사용자 정보 가져오기
            GitHubUserInfo userInfo = gitHubService.getUserInfo(accessToken);

            if (userInfo == null) {
                model.addAttribute("error", "Failed to get user information from GitHub");
                return "error";
            }

            // 세션에 사용자 정보와 access token 저장
            session.setAttribute("user", userInfo);
            session.setAttribute("accessToken", accessToken);

            // dashboard 페이지로 리다이렉트
            return "redirect:/dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Error during authentication: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/success")
    public String success() {
        return "success";
    }

    @GetMapping("/error")
    public String error() {
        return "error";
    }

    @GetMapping("/logout")
    public RedirectView logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);

        // GitHub access token revoke
        if (session != null) {
            String accessToken = (String) session.getAttribute("accessToken");
            if (accessToken != null) {
                gitHubService.revokeToken(accessToken);
            }
            // 세션 무효화
            session.invalidate();
        }

        // 쿠키 삭제
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                cookie.setValue("");
                cookie.setPath("/");
                cookie.setMaxAge(0);
                response.addCookie(cookie);
            }
        }

        // 로그인 페이지로 리다이렉트
        return new RedirectView("/");
    }
}
