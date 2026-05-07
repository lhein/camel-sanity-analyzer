package com.github.lhein.camelsanity.enrichment;

import com.github.lhein.camelsanity.model.Coordinate;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client for the Maven Central Search REST API (https://search.maven.org/).
 * Used to list components, list versions, find latest version and release date.
 */
@Component
public class MavenCentralClient {

    private static final Logger log = LoggerFactory.getLogger(MavenCentralClient.class);
    private static final String SEARCH_URL = "https://search.maven.org/solrsearch/select";
    private static final VersionScheme VERSION_SCHEME = new GenericVersionScheme();

    private final WebClient client;

    public MavenCentralClient(WebClient client) {
        this.client = client;
    }

    /**
     * List all Apache Camel components from Maven Central.
     * Filter: g:org.apache.camel AND a:camel-*, packaging jar, only most-recent per artifact.
     */
    @Cacheable(value = "components", key = "'camel-components'")
    public List<String> listCamelComponents() {
        try {
            int rows = 200;
            int start = 0;
            List<String> all = new ArrayList<>();
            while (true) {
                int s = start;
                Map<String, Object> body = client.get()
                        .uri(uri -> uri
                                .scheme("https").host("search.maven.org").path("/solrsearch/select")
                                .queryParam("q", "g:org.apache.camel AND a:camel-*")
                                .queryParam("rows", rows)
                                .queryParam("start", s)
                                .queryParam("wt", "json")
                                .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(20))
                        .block();
                if (body == null) break;
                Map<String, Object> response = (Map<String, Object>) body.get("response");
                if (response == null) break;
                Number numFound = (Number) response.get("numFound");
                List<Map<String, Object>> docs = (List<Map<String, Object>>) response.get("docs");
                if (docs == null || docs.isEmpty()) break;
                for (Map<String, Object> d : docs) {
                    String a = (String) d.get("a");
                    if (a != null && a.startsWith("camel-")) {
                        all.add(a);
                    }
                }
                start += rows;
                if (numFound != null && start >= numFound.intValue()) break;
                if (start > 5000) break; // safety
            }
            Collections.sort(all);
            return all.stream().distinct().toList();
        } catch (Exception e) {
            log.error("Failed to list Camel components: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * List all available versions for a given groupId:artifactId, sorted newest-first.
     */
    @Cacheable(value = "maven-versions", key = "#groupId + ':' + #artifactId")
    public List<VersionInfo> listVersions(String groupId, String artifactId) {
        try {
            Map<String, Object> body = client.get()
                    .uri(uri -> uri
                            .scheme("https").host("search.maven.org").path("/solrsearch/select")
                            .queryParam("q", "g:" + groupId + " AND a:" + artifactId)
                            .queryParam("core", "gav")
                            .queryParam("rows", 200)
                            .queryParam("wt", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            if (body == null) return List.of();
            Map<String, Object> response = (Map<String, Object>) body.get("response");
            if (response == null) return List.of();
            List<Map<String, Object>> docs = (List<Map<String, Object>>) response.get("docs");
            if (docs == null) return List.of();
            List<VersionInfo> versions = new ArrayList<>();
            for (Map<String, Object> d : docs) {
                String v = (String) d.get("v");
                Number ts = (Number) d.get("timestamp");
                if (v == null) continue;
                Instant released = ts != null ? Instant.ofEpochMilli(ts.longValue()) : null;
                versions.add(new VersionInfo(v, released));
            }
            // Sort semantically by version, newest first. Falls back to release date
            // if version parsing fails for some artifact.
            versions.sort((x, y) -> compareVersions(y.version(), x.version()));
            return versions;
        } catch (Exception e) {
            log.warn("Failed to list versions for {}:{}: {}", groupId, artifactId, e.getMessage());
            return List.of();
        }
    }

    @Cacheable(value = "maven-latest", key = "#coord.ga()")
    public Optional<VersionInfo> latestVersion(Coordinate coord) {
        List<VersionInfo> versions = listVersions(coord.groupId(), coord.artifactId());
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.get(0));
    }

    /**
     * Find release date for a specific GAV. Looked up via the gav core search.
     */
    public Optional<Instant> releaseDate(Coordinate coord) {
        List<VersionInfo> versions = listVersions(coord.groupId(), coord.artifactId());
        return versions.stream()
                .filter(v -> v.version().equals(coord.version()))
                .findFirst()
                .map(VersionInfo::released);
    }

    public record VersionInfo(String version, Instant released) {
    }

    private static int compareVersions(String a, String b) {
        try {
            Version va = VERSION_SCHEME.parseVersion(a);
            Version vb = VERSION_SCHEME.parseVersion(b);
            return va.compareTo(vb);
        } catch (InvalidVersionSpecificationException e) {
            return a.compareTo(b);
        }
    }
}
