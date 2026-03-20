package com.aflokkat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Configures the Redis connection using AppConfig (env vars / .env / application.properties).
 * Spring Boot auto-creates StringRedisTemplate and RedisTemplate once this factory bean is present.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(AppConfig.getRedisHost(), AppConfig.getRedisPort());
        return new LettuceConnectionFactory(config);
    }
}
