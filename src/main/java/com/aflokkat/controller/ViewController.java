package com.aflokkat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * View Controller — serves the main HTML pages
 */
@Controller
public class ViewController {
    
    @GetMapping("/")
    public String index() {
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
}
