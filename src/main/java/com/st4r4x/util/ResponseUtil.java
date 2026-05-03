package com.st4r4x.util;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

public class ResponseUtil {

    private static final Logger logger = LoggerFactory.getLogger(ResponseUtil.class);

    private ResponseUtil() {}

    public static ResponseEntity<Map<String, Object>> errorResponse(Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        if (e instanceof IllegalArgumentException) {
            response.put("message", e.getMessage());
            return ResponseEntity.status(400).body(response);
        }
        // Log full detail server-side; never expose internal stack trace to clients
        logger.error("Unhandled exception: {}", e.getMessage(), e);
        response.put("message", "An internal error occurred");
        return ResponseEntity.status(500).body(response);
    }
}
