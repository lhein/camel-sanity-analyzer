package com.github.lhein.camelsanity.enrichment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the {@code org.apache.camel:camel-catalog} JAR for a given Camel
 * version and extracts the list of artifactIds per kind (component,
 * dataformat, language). Each entry's JSON descriptor is parsed to get
 * the canonical {@code artifactId} — multiple catalog names can map to
 * the same Maven artifact (e.g. {@code bindyCsv}, {@code bindyFixed},
 * {@code bindyKvp} all resolve to {@code camel-bindy}).
 */
@Component
public class CamelCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CamelCatalogClient.class);
    private static final String CATALOG_BASE =
            "https://repo.maven.apache.org/maven2/org/apache/camel/camel-catalog/";
    private static final String CATALOG_PATH_PREFIX = "org/apache/camel/catalog/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient client;
    private final MavenCentralClient mavenCentral;

    public CamelCatalogClient(WebClient client, MavenCentralClient mavenCentral) {
        this.client = client;
        this.mavenCentral = mavenCentral;
    }

    /**
     * Fetches the catalog for the latest stable Camel version and returns
     * artifactIds grouped by kind. Each list is alphabetically sorted and
     * de-duplicated. Not cached at this level (the heavy work in {@link #fetch}
     * is cached); re-resolving the version each call is cheap because that
     * lookup itself is cached, and avoids sticking with an empty catalog when
     * a transient network error happens during the first call.
     */
    public Catalog fetchLatest() {
        String version = mavenCentral
                .latestVersion(new com.github.lhein.camelsanity.model.Coordinate(
                        "org.apache.camel", "camel-catalog", "0"))
                .map(v -> v.version())
                .orElse(null);
        if (version == null) {
            log.warn("Could not determine latest camel-catalog version");
            return Catalog.empty();
        }
        return fetch(version);
    }

    @Cacheable(
            value = "components",
            key = "'camel-catalog-' + #version",
            unless = "#result.components().isEmpty() && #result.dataformats().isEmpty() && #result.languages().isEmpty()")
    public Catalog fetch(String version) {
        String url = CATALOG_BASE + version + "/camel-catalog-" + version + ".jar";
        try {
            byte[] jar = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            if (jar == null) {
                log.warn("camel-catalog {} returned empty body", version);
                return Catalog.empty();
            }
            return parse(jar);
        } catch (Exception e) {
            log.error("Failed to fetch camel-catalog {}: {}", version, e.getMessage());
            return Catalog.empty();
        }
    }

    private Catalog parse(byte[] jar) throws Exception {
        TreeSet<String> components = new TreeSet<>();
        TreeSet<String> dataformats = new TreeSet<>();
        TreeSet<String> languages = new TreeSet<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (!name.startsWith(CATALOG_PATH_PREFIX) || !name.endsWith(".json")) {
                    continue;
                }
                String relative = name.substring(CATALOG_PATH_PREFIX.length());
                int slash = relative.indexOf('/');
                if (slash <= 0) continue;
                String kindDir = relative.substring(0, slash);
                String artifactId = readArtifactId(zis, kindDir);
                if (artifactId == null) continue;
                switch (kindDir) {
                    case "components" -> components.add(artifactId);
                    case "dataformats" -> dataformats.add(artifactId);
                    case "languages" -> languages.add(artifactId);
                    default -> { /* ignore others */ }
                }
            }
        }
        return new Catalog(List.copyOf(components), List.copyOf(dataformats), List.copyOf(languages));
    }

    /**
     * Reads JSON of a single catalog entry from the open zip stream and
     * returns the artifactId. The JSON is wrapped under a top-level key
     * matching the kind ({@code "component"}, {@code "dataformat"} or
     * {@code "language"}).
     */
    private String readArtifactId(ZipInputStream zis, String kindDir) throws Exception {
        byte[] bytes = zis.readAllBytes();
        JsonNode root = MAPPER.readTree(bytes);
        // Top-level key is the singular form of kindDir
        String topKey = switch (kindDir) {
            case "components" -> "component";
            case "dataformats" -> "dataformat";
            case "languages" -> "language";
            default -> null;
        };
        if (topKey == null) return null;
        JsonNode meta = root.path(topKey);
        if (!meta.isObject()) return null;
        JsonNode artifactId = meta.get("artifactId");
        if (artifactId == null || !artifactId.isTextual()) return null;
        return artifactId.asText();
    }

    public record Catalog(List<String> components,
                          List<String> dataformats,
                          List<String> languages) {
        public static Catalog empty() {
            return new Catalog(List.of(), List.of(), List.of());
        }

        /** Returns a flat {artifactId -> [kind, ...]} map sorted by artifactId. */
        public Map<String, List<String>> flatten() {
            Map<String, List<String>> tmp = new java.util.TreeMap<>();
            for (String a : components) tmp.computeIfAbsent(a, k -> new ArrayList<>()).add("component");
            for (String a : dataformats) tmp.computeIfAbsent(a, k -> new ArrayList<>()).add("dataformat");
            for (String a : languages) tmp.computeIfAbsent(a, k -> new ArrayList<>()).add("language");
            return new LinkedHashMap<>(tmp);
        }
    }
}
