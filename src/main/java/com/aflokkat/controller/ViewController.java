package com.aflokkat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Vue Controller - Sert les pages HTML principales
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
    
    @GetMapping("/trash-advisor")
    public String trashAdvisor() {
        return "trash-advisor";
    }
}
