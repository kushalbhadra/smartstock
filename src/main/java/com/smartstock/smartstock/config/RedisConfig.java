
package com.smartstock.smartstock.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory factory) {

        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(30));

        Map<String, RedisCacheConfiguration> cacheConfigs =
                new HashMap<>();

        cacheConfigs.put("products",
                defaultConfig.entryTtl(Duration.ofHours(2)));

        cacheConfigs.put("warehouses",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        cacheConfigs.put("forecasts",
                defaultConfig.entryTtl(Duration.ofHours(6)));

        cacheConfigs.put("dashboard",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        cacheConfigs.put("suppliers",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
