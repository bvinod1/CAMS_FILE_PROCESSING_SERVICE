package com.cams.fileprocessing.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures Caffeine-backed in-memory caches.
 *
 * <p>Caches defined here:
 * <ul>
 *   <li>{@code validationTemplateCache} — active validation templates per flowType, 60 s TTL</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String VALIDATION_TEMPLATE_CACHE = "validationTemplateCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(VALIDATION_TEMPLATE_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(200));
        return manager;
    }
}
