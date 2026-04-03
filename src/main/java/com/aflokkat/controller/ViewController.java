package com.aflokkat.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * View Controller — serves the main HTML pages
 */
@Controller
public class ViewController {

    @GetMapping("/")
    public String index(Authentication auth) {
        if (auth == null) {
            return "landing";
        }
        if (auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CONTROLLER"))) {
            return "redirect:/dashboard";
        }
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
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

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    /**
     * Wave 0 stub — route registered here so ViewControllerAnalyticsTest compiles.
     * Thymeleaf template created in Plan 06-03.
     */
    @GetMapping("/analytics")
    public String analytics() {
        return "analytics";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }
}
