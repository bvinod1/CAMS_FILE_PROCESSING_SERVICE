package com.cams.fileprocessing.business.validation;

import com.cams.fileprocessing.features.validation.models.ValidationTemplate;
import com.cams.fileprocessing.interfaces.ValidationTemplatePort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * Caching decorator around {@link ValidationTemplatePort}.
 *
 * <p>Maintains a Caffeine in-memory cache with a 60-second TTL per flow type.
 * Cache is invalidated on template updates so new templates take effect immediately.
 *
 * <p>This class resides in {@code business.validation} — it may only depend on the
 * {@link ValidationTemplatePort} interface, never on a JPA/infrastructure class (ArchUnit-enforced).
 */
@Primary
@Service
@Profile({"local", "gcp"})
public class CachingValidationTemplateService implements ValidationTemplatePort {

    private static final Logger log = LoggerFactory.getLogger(CachingValidationTemplateService.class);

    private final ValidationTemplatePort delegate;

    /** In-memory lookup: flowType → active ValidationTemplate (60 s TTL). */
    private final Cache<String, ValidationTemplate> cache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(200)
            .build();

    public CachingValidationTemplateService(
            @Qualifier("rawValidationTemplatePort") ValidationTemplatePort delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<ValidationTemplate> getActiveTemplate(String flowType) {
        ValidationTemplate cached = cache.getIfPresent(flowType);
        if (cached != null) {
            log.debug("Cache HIT for flowType={} version={}", flowType, cached.version());
            return Mono.just(cached);
        }
        return delegate.getActiveTemplate(flowType)
                .doOnNext(t -> {
                    cache.put(flowType, t);
                    log.debug("Cache MISS — loaded and cached template flowType={} version={}",
                            flowType, t.version());
                });
    }

    @Override
    public Mono<ValidationTemplate> saveTemplate(ValidationTemplate template) {
        return delegate.saveTemplate(template)
                .doOnNext(saved -> {
                    // Evict old entry so the next read picks up the new template immediately
                    cache.invalidate(saved.flowType());
                    log.info("Cache EVICTED for flowType={} after save (new version={})",
                            saved.flowType(), saved.version());
                });
    }

    @Override
    public Flux<ValidationTemplate> listTemplates(String flowType) {
        // List queries are not cached — they always go to the DB
        return delegate.listTemplates(flowType);
    }

    @Override
    public Flux<String> listActiveFlowTypes() {
        return delegate.listActiveFlowTypes();
    }

    /**
     * Manually evict the cache for a specific flow type.
     * Useful in tests or administrative operations.
     */
    public void evict(String flowType) {
        cache.invalidate(flowType);
        log.info("Cache manually evicted for flowType={}", flowType);
    }

    /**
     * Returns the number of entries currently in the cache.
     */
    public long cacheSize() {
        return cache.estimatedSize();
    }
}
