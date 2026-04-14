package com.aflokkat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aflokkat.entity.BookmarkEntity;

@Repository
public interface BookmarkRepository extends JpaRepository<BookmarkEntity, Long> {
    List<BookmarkEntity> findByUserId(Long userId);
    long countByUserId(Long userId);
    Optional<BookmarkEntity> findByUserIdAndRestaurantId(Long userId, String restaurantId);
    boolean existsByUserIdAndRestaurantId(Long userId, String restaurantId);
    void deleteByUserIdAndRestaurantId(Long userId, String restaurantId);
}
