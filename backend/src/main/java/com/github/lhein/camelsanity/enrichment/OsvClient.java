package com.github.lhein.camelsanity.enrichment;

import com.github.lhein.camelsanity.model.Coordinate;
import com.github.lhein.camelsanity.model.Vulnerability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OSV.dev vulnerability lookup. Docs: https://google.github.io/osv.dev/post-v1-query/
 */
@Component
public class OsvClient {

    private static final Logger log = LoggerFactory.getLogger(OsvClient.class);
    private static final String QUERY_URL = "https://api.osv.dev/v1/query";

    private final WebClient client;

    public OsvClient(WebClient client) {
        this.client = client;
    }

    @Cacheable(value = "osv", key = "#coord.gav()")
    public List<Vulnerability> query(Coordinate coord) {
        Map<String, Object> request = Map.of(
                "version", coord.version(),
                "package", Map.of(
                        "name", coord.groupId() + ":" + coord.artifactId(),
                        "ecosystem", "Maven"));
        try {
            Map<String, Object> body = client.post()
                    .uri(QUERY_URL)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            if (body == null) return List.of();
            List<Map<String, Object>> vulns = (List<Map<String, Object>>) body.get("vulns");
            if (vulns == null || vulns.isEmpty()) return List.of();

            List<Vulnerability> result = new ArrayList<>();
            for (Map<String, Object> v : vulns) {
                String id = (String) v.get("id");
                String summary = (String) v.get("summary");
                String severity = extractSeverity(v);
                String url = "https://osv.dev/vulnerability/" + id;
                result.add(new Vulnerability(id, summary, severity, url));
            }
            return result;
        } catch (Exception e) {
            log.debug("OSV query failed for {}: {}", coord.gav(), e.getMessage());
            return List.of();
        }
    }

    private String extractSeverity(Map<String, Object> v) {
        // Prefer database_specific severity (e.g. GHSA labels HIGH/CRITICAL/etc)
        Map<String, Object> dbSpec = (Map<String, Object>) v.get("database_specific");
        if (dbSpec != null) {
            Object sev = dbSpec.get("severity");
            if (sev instanceof String s) return s;
        }
        List<Map<String, Object>> severities = (List<Map<String, Object>>) v.get("severity");
        if (severities != null && !severities.isEmpty()) {
            Map<String, Object> first = severities.get(0);
            Object score = first.get("score");
            if (score instanceof String s) return s;
        }
        return "UNKNOWN";
    }
}
