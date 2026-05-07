package com.github.lhein.camelsanity.enrichment;

import com.github.lhein.camelsanity.model.Coordinate;
import com.github.lhein.camelsanity.scoring.VersionUtils;
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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads version metadata from the Maven Central repo. Used to list available
 * versions of an artifact and to pick the latest stable one.
 */
@Component
public class MavenCentralClient {

    private static final Logger log = LoggerFactory.getLogger(MavenCentralClient.class);
    private static final String CENTRAL_REPO = "https://repo.maven.apache.org/maven2/";
    private static final VersionScheme VERSION_SCHEME = new GenericVersionScheme();
    private static final Pattern VERSION_TAG = Pattern.compile("<version>([^<]+)</version>");

    private final WebClient client;

    public MavenCentralClient(WebClient client) {
        this.client = client;
    }

    /**
     * List all available versions for a given groupId:artifactId, sorted newest-first.
     * <p>
     * Uses the artifact's maven-metadata.xml from the Central repo, which is authoritative
     * and complete. The Solr search API is unreliable for this: with core=gav it caps rows
     * to 20 regardless of the rows parameter, and the index can lag behind the repo by months.
     * <p>
     * Per-version timestamps are not included — they are not in maven-metadata.xml. The
     * enricher pulls them from deps.dev where needed.
     */
    @Cacheable(value = "maven-versions", key = "#groupId + ':' + #artifactId",
            unless = "#result.isEmpty()")
    public List<VersionInfo> listVersions(String groupId, String artifactId) {
        String url = CENTRAL_REPO + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        try {
            String xml = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            if (xml == null) return List.of();
            List<VersionInfo> versions = new ArrayList<>();
            Matcher m = VERSION_TAG.matcher(xml);
            while (m.find()) {
                String v = m.group(1).trim();
                if (!v.isEmpty()) versions.add(new VersionInfo(v, null));
            }
            versions.sort((x, y) -> compareVersions(y.version(), x.version()));
            return versions;
        } catch (Exception e) {
            log.warn("Failed to read maven-metadata.xml for {}:{}: {}", groupId, artifactId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Highest stable version (excluding alpha/beta/RC/milestone/snapshot). Falls back
     * to the highest pre-release if the artifact has only pre-releases.
     */
    public Optional<VersionInfo> latestVersion(Coordinate coord) {
        List<VersionInfo> versions = listVersions(coord.groupId(), coord.artifactId());
        return versions.stream()
                .filter(v -> !VersionUtils.isPreRelease(v.version()))
                .findFirst()
                .or(() -> versions.stream().findFirst());
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
