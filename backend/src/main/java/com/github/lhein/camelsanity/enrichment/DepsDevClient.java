package com.github.lhein.camelsanity.enrichment;

import com.github.lhein.camelsanity.model.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * deps.dev v3 API: fetches license, source repo URL, dependents count.
 * Docs: https://docs.deps.dev/api/v3/
 */
@Component
public class DepsDevClient {

    private static final Logger log = LoggerFactory.getLogger(DepsDevClient.class);
    private static final String BASE = "https://api.deps.dev/v3";

    private final WebClient client;

    public DepsDevClient(WebClient client) {
        this.client = client;
    }

    @Cacheable(value = "depsdev", key = "'v:' + #coord.gav()")
    public DepsDevInfo fetchVersion(Coordinate coord) {
        String name = UriUtils.encode(coord.groupId() + ":" + coord.artifactId(), StandardCharsets.UTF_8);
        String version = UriUtils.encode(coord.version(), StandardCharsets.UTF_8);
        // Pass an already-built URI so WebClient does not re-encode percent escapes.
        URI url = URI.create(BASE + "/systems/maven/packages/" + name + "/versions/" + version);

        try {
            Map<String, Object> body = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            if (body == null) return DepsDevInfo.empty();

            String license = null;
            List<String> licenses = (List<String>) body.get("licenses");
            if (licenses != null && !licenses.isEmpty()) {
                license = String.join(", ", licenses);
            }

            String sourceRepo = null;
            List<Map<String, Object>> related = (List<Map<String, Object>>) body.get("relatedProjects");
            if (related != null) {
                for (Map<String, Object> rel : related) {
                    String relType = (String) rel.get("relationType");
                    Map<String, Object> projectKey = (Map<String, Object>) rel.get("projectKey");
                    if (("SOURCE_REPO".equals(relType) || "ORIGIN".equals(relType)) && projectKey != null) {
                        sourceRepo = (String) projectKey.get("id");
                        break;
                    }
                }
            }

            Instant publishedAt = parseInstant((String) body.get("publishedAt"));

            return new DepsDevInfo(license, sourceRepo, publishedAt);
        } catch (Exception e) {
            log.debug("deps.dev fetch failed for {}: {}", coord.gav(), e.getMessage());
            return DepsDevInfo.empty();
        }
    }

    private static Instant parseInstant(String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Cacheable(value = "depsdev", key = "'p:' + #projectId")
    public ProjectInfo fetchProject(String projectId) {
        if (projectId == null || projectId.isBlank()) return ProjectInfo.empty();
        URI url = URI.create(BASE + "/projects/"
                + UriUtils.encode(projectId, StandardCharsets.UTF_8));
        try {
            Map<String, Object> body = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            if (body == null) return ProjectInfo.empty();

            Number stars = (Number) body.get("starsCount");
            Number forks = (Number) body.get("forksCount");
            Number openIssues = (Number) body.get("openIssuesCount");
            Map<String, Object> sc = (Map<String, Object>) body.get("scorecard");
            Double scorecardScore = null;
            if (sc != null) {
                Number n = (Number) sc.get("overallScore");
                if (n != null) scorecardScore = n.doubleValue();
            }
            return new ProjectInfo(
                    stars != null ? stars.intValue() : null,
                    forks != null ? forks.intValue() : null,
                    openIssues != null ? openIssues.intValue() : null,
                    scorecardScore);
        } catch (Exception e) {
            log.debug("deps.dev project fetch failed for {}: {}", projectId, e.getMessage());
            return ProjectInfo.empty();
        }
    }

    public record DepsDevInfo(String license, String sourceRepo, Instant publishedAt) {
        public static DepsDevInfo empty() {
            return new DepsDevInfo(null, null, null);
        }
    }

    public record ProjectInfo(Integer stars, Integer forks, Integer openIssues, Double scorecardScore) {
        public static ProjectInfo empty() {
            return new ProjectInfo(null, null, null, null);
        }
    }
}
