package com.github.lhein.camelsanity.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AnalyzerProperties.class)
public class AppConfig {

    @Bean
    public CacheManager cacheManager(AnalyzerProperties props) {
        CaffeineCacheManager mgr = new CaffeineCacheManager(
                "github-repo", "maven-versions", "maven-latest",
                "depsdev", "osv", "scorecard", "components");
        mgr.setCaffeine(Caffeine.newBuilder()
                .maximumSize(props.cache().maxEntries())
                .expireAfterWrite(Duration.ofMinutes(props.cache().ttlMinutes())));
        return mgr;
    }

    @Bean
    public WebClient webClient(AnalyzerProperties props) {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }
}
