package com.smartstock.smartstock.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching  // enables @Cacheable, @CacheEvict etc
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory factory) {

        // Default config — 30 minutes TTL
        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(30))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new GenericJackson2JsonRedisSerializer()
                                        )
                        );

        // Custom TTL per cache name
        Map<String, RedisCacheConfiguration> cacheConfigs =
                new HashMap<>();

        // Products rarely change — cache 2 hours
        cacheConfigs.put("products",
                defaultConfig.entryTtl(Duration.ofHours(2)));

        // Warehouses rarely change — cache 1 hour
        cacheConfigs.put("warehouses",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        // AI forecasts run nightly — cache 6 hours
        cacheConfigs.put("forecasts",
                defaultConfig.entryTtl(Duration.ofHours(6)));

        // Dashboard refreshes every 5 minutes
        cacheConfigs.put("dashboard",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // Suppliers rarely change — cache 1 hour
        cacheConfigs.put("suppliers",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}