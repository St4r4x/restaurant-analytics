package com.st4r4x.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Configures the Redis connection using AppConfig (env vars / .env / application.properties).
 * Spring Boot auto-creates StringRedisTemplate and RedisTemplate once this factory bean is present.
 *
 * Note: Spring Boot 4 auto-configures tools.jackson (Jackson 3) beans rather than
 * com.fasterxml.jackson (Jackson 2). RestaurantCacheService depends on the Jackson 2
 * ObjectMapper for Redis serialization, so we provide it explicitly here.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(AppConfig.getRedisHost(), AppConfig.getRedisPort());
        String password = AppConfig.getRedisPassword();
        if (password != null && !password.isEmpty()) {
            config.setPassword(RedisPassword.of(password));
        }
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder();
        if (AppConfig.isRedisSsl()) {
            builder.useSsl();
        }
        return new LettuceConnectionFactory(config, builder.build());
    }

    /**
     * Jackson 2 ObjectMapper bean for RestaurantCacheService.
     * Boot 4 auto-configures Jackson 3 (tools.jackson) by default; we expose
     * Jackson 2 explicitly so that RestaurantCacheService can inject it.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
