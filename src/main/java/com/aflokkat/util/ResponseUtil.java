package com.aflokkat.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;

public class ResponseUtil {

    private ResponseUtil() {}

    public static ResponseEntity<Map<String, Object>> errorResponse(Exception e) {
        int status = (e instanceof IllegalArgumentException) ? 400 : 500;
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", e.getMessage());
        return ResponseEntity.status(status).body(response);
    }
}
