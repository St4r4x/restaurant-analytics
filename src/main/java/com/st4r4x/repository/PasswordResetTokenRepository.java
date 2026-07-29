package com.st4r4x.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.st4r4x.entity.PasswordResetTokenEntity;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
}
