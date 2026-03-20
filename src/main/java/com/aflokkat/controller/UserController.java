package com.aflokkat.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.entity.BookmarkEntity;
import com.aflokkat.entity.UserEntity;
import com.aflokkat.repository.BookmarkRepository;
import com.aflokkat.repository.UserRepository;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private RestaurantDAO restaurantDAO;

    private UserEntity getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getProfile() {
        UserEntity user = getCurrentUser();
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        data.put("createdAt", user.getCreatedAt());
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/bookmarks")
    public ResponseEntity<Map<String, Object>> getBookmarks() {
        UserEntity user = getCurrentUser();
        List<BookmarkEntity> bookmarks = bookmarkRepository.findByUserId(user.getId());
        List<String> restaurantIds = bookmarks.stream()
                .map(BookmarkEntity::getRestaurantId)
                .collect(Collectors.toList());
        List<Restaurant> restaurants = restaurantIds.isEmpty()
                ? Collections.emptyList()
                : restaurantDAO.findByIds(restaurantIds);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", restaurants);
        response.put("count", restaurants.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/bookmarks/{restaurantId}")
    public ResponseEntity<Map<String, Object>> addBookmark(@PathVariable String restaurantId) {
        UserEntity user = getCurrentUser();
        Map<String, Object> response = new HashMap<>();
        if (bookmarkRepository.existsByUserIdAndRestaurantId(user.getId(), restaurantId)) {
            response.put("status", "success");
            response.put("message", "Already bookmarked");
            return ResponseEntity.ok(response);
        }
        bookmarkRepository.save(new BookmarkEntity(user, restaurantId));
        response.put("status", "success");
        response.put("message", "Bookmark added");
        response.put("restaurantId", restaurantId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me/bookmarks/{restaurantId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeBookmark(@PathVariable String restaurantId) {
        UserEntity user = getCurrentUser();
        bookmarkRepository.deleteByUserIdAndRestaurantId(user.getId(), restaurantId);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Bookmark removed");
        return ResponseEntity.ok(response);
    }
}
