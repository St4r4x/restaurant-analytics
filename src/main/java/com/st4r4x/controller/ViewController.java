package com.st4r4x.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * View Controller — serves the main HTML pages
 */
@Controller
public class ViewController {

    /** Search page, for every visitor: the navbar's "Search" link points here. */
    @GetMapping("/")
    public String index() {
        return "landing";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/reset-password")
    public String resetPassword() {
        return "reset-password";
    }

    @GetMapping("/restaurant/{id}")
    public String restaurantDetail() {
        return "restaurant";
    }

    @GetMapping("/inspection-map")
    public String inspectionMap() {
        return "inspection-map";
    }

    @GetMapping("/my-bookmarks")
    public String myBookmarks() {
        return "my-bookmarks";
    }

    /**
     * Role-dependent dashboard: inspector dashboard for controllers, bookmarks and nearby
     * restaurants (index.html) for the other signed-in users. An expired access token reaches
     * here as anonymous, so dashboard.html refreshes the session and resolves the role itself.
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication auth) {
        if (auth == null || auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CONTROLLER"))) {
            return "dashboard";
        }
        return "index";
    }

    /**
     * Wave 0 stub — route registered here so ViewControllerAnalyticsTest compiles.
     * Thymeleaf template created in Plan 06-03.
     */
    @GetMapping("/analytics")
    public String analytics() {
        return "analytics";
    }

    @GetMapping("/uncontrolled")
    public String uncontrolled() {
        return "uncontrolled";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
