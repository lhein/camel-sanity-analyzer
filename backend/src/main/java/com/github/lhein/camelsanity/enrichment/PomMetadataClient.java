package com.github.lhein.camelsanity.enrichment;

import com.github.lhein.camelsanity.model.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Fetches a project's POM directly from Maven Central to extract SCM URL,
 * organization, project URL etc. — useful when deps.dev has no source-repo info.
 */
@Component
public class PomMetadataClient {

    private static final Logger log = LoggerFactory.getLogger(PomMetadataClient.class);
    private static final String CENTRAL_BASE = "https://repo.maven.apache.org/maven2/";

    private final WebClient client;

    public PomMetadataClient(WebClient client) {
        this.client = client;
    }

    @Cacheable(value = "depsdev", key = "'pom:' + #coord.gav()")
    public PomMetadata fetch(Coordinate coord) {
        return fetchWithParents(coord, 0);
    }

    private PomMetadata fetchWithParents(Coordinate coord, int depth) {
        if (depth > 4) return PomMetadata.empty();
        String url = CENTRAL_BASE
                + coord.groupId().replace('.', '/') + "/"
                + coord.artifactId() + "/" + coord.version() + "/"
                + coord.artifactId() + "-" + coord.version() + ".pom";
        try {
            String xml = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();
            if (xml == null) return PomMetadata.empty();
            ParsedPom parsed = parse(xml);
            // Inherit organization / project URL from parent chain (those propagate sensibly).
            // Do NOT inherit scmUrl from parents — it points to the parent project's SCM,
            // not the artifact's. We rely on RepoHeuristics for that fallback.
            if (parsed.parent != null && (
                    parsed.metadata.organizationName == null ||
                    parsed.metadata.projectUrl == null ||
                    parsed.metadata.organizationUrl == null)) {
                PomMetadata parentMeta = fetchWithParents(parsed.parent, depth + 1);
                return new PomMetadata(
                        firstNonBlank(parsed.metadata.projectName, parentMeta.projectName),
                        firstNonBlank(parsed.metadata.projectUrl, parentMeta.projectUrl),
                        firstNonBlank(parsed.metadata.organizationName, parentMeta.organizationName),
                        firstNonBlank(parsed.metadata.organizationUrl, parentMeta.organizationUrl),
                        parsed.metadata.scmUrl);
            }
            return parsed.metadata;
        } catch (Exception e) {
            log.debug("POM fetch failed for {}: {}", coord.gav(), e.getMessage());
            return PomMetadata.empty();
        }
    }

    private ParsedPom parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            var doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var root = doc.getDocumentElement();

            String name = directChildText(root, "name");
            String url = directChildText(root, "url");
            String orgName = null;
            String orgUrl = null;
            var orgs = directChildElement(root, "organization");
            if (orgs != null) {
                orgName = directChildText(orgs, "name");
                orgUrl = directChildText(orgs, "url");
            }
            String scmUrl = null;
            var scm = directChildElement(root, "scm");
            if (scm != null) {
                scmUrl = Optional.ofNullable(directChildText(scm, "url"))
                        .orElse(directChildText(scm, "connection"));
                if (scmUrl != null) {
                    scmUrl = scmUrl.replaceFirst("^scm:git:", "")
                            .replaceFirst("^scm:", "");
                }
            }
            Coordinate parent = null;
            var parentEl = directChildElement(root, "parent");
            if (parentEl != null) {
                String pg = directChildText(parentEl, "groupId");
                String pa = directChildText(parentEl, "artifactId");
                String pv = directChildText(parentEl, "version");
                if (pg != null && pa != null && pv != null) {
                    parent = new Coordinate(pg, pa, pv);
                }
            }
            return new ParsedPom(new PomMetadata(name, url, orgName, orgUrl, scmUrl), parent);
        } catch (Exception e) {
            log.debug("POM parse failed: {}", e.getMessage());
            return new ParsedPom(PomMetadata.empty(), null);
        }
    }

    private static org.w3c.dom.Element directChildElement(org.w3c.dom.Element parent, String tag) {
        var nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            var n = nodes.item(i);
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
                return (org.w3c.dom.Element) n;
            }
        }
        return null;
    }

    private static String directChildText(org.w3c.dom.Element parent, String tag) {
        var el = directChildElement(parent, tag);
        if (el == null) return null;
        String t = el.getTextContent();
        return t == null ? null : t.trim();
    }

    private static String firstNonBlank(String... s) {
        for (String x : s) if (x != null && !x.isBlank()) return x;
        return null;
    }

    private record ParsedPom(PomMetadata metadata, Coordinate parent) {}

    public record PomMetadata(
            String projectName,
            String projectUrl,
            String organizationName,
            String organizationUrl,
            String scmUrl) {
        public static PomMetadata empty() {
            return new PomMetadata(null, null, null, null, null);
        }
    }
}
