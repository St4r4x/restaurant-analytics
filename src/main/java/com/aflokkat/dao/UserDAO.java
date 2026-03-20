package com.aflokkat.dao;

import com.aflokkat.domain.User;

public interface UserDAO {
    User createUser(User user);
    User findByUsername(String username);
    User findByEmail(String email);
    User findById(String id);
}
