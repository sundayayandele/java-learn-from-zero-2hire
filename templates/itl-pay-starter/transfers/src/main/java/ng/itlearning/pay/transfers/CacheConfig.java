package ng.itlearning.pay.transfers;

import java.time.Duration;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/** Cache-aside for transfer status in Redis (topic 13.4). */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfig implements CachingConfigurer {

    /** If Redis is down or slow, log it and answer from the database: the cache must never take the service down. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory redis) {
        var transfers = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))                    // a safety net: eviction on change does the real work
                .prefixCacheNameWith("itl-pay:")
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(Transfers.Transfer.class)));   // JSON, never Java serialisation
        return RedisCacheManager.builder(redis)
                .withCacheConfiguration("transfers", transfers)
                .transactionAware()                                  // evict after the database commits, not before
                .enableStatistics()                                  // cache.gets{result=hit|miss} in Prometheus
                .build();
    }
}
