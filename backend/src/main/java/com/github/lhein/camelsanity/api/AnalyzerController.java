package com.github.lhein.camelsanity.api;

import com.github.lhein.camelsanity.AnalyzerService;
import com.github.lhein.camelsanity.config.AnalyzerProperties;
import com.github.lhein.camelsanity.enrichment.CamelCatalogClient;
import com.github.lhein.camelsanity.enrichment.MavenCentralClient;
import com.github.lhein.camelsanity.model.AnalysisResult;
import com.github.lhein.camelsanity.model.Coordinate;
import com.github.lhein.camelsanity.scoring.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyzerController {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerController.class);

    private final MavenCentralClient mavenCentral;
    private final CamelCatalogClient catalog;
    private final AnalyzerService analyzer;
    private final AnalyzerProperties props;

    public AnalyzerController(MavenCentralClient mavenCentral,
                              CamelCatalogClient catalog,
                              AnalyzerService analyzer,
                              AnalyzerProperties props) {
        this.mavenCentral = mavenCentral;
        this.catalog = catalog;
        this.analyzer = analyzer;
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "githubTokenConfigured", props.github().hasToken()
        );
    }

    /**
     * Lists stable Camel versions for which a catalog has been published,
     * newest first. Pre-releases (Alpha/Beta/RC/Milestone) are filtered out.
     */
    @GetMapping("/camel-versions")
    public List<String> camelVersions() {
        return mavenCentral
                .listVersions("org.apache.camel", "camel-catalog")
                .stream()
                .map(MavenCentralClient.VersionInfo::version)
                .filter(v -> !VersionUtils.isPreRelease(v))
                .toList();
    }

    /**
     * Returns the Camel catalog grouped by kind plus a flat "all" list with
     * {artifactId, kinds[]} entries. If {@code camelVersion} is omitted, the
     * latest stable version is used.
     */
    @GetMapping("/artifacts")
    public Map<String, Object> artifacts(
            @RequestParam(required = false) String camelVersion) {
        CamelCatalogClient.Catalog c = (camelVersion == null || camelVersion.isBlank())
                ? catalog.fetchLatest()
                : catalog.fetch(camelVersion);
        return Map.of(
                "components", c.components(),
                "dataformats", c.dataformats(),
                "languages", c.languages(),
                "all", c.flatten().entrySet().stream()
                        .map(e -> Map.of("artifactId", e.getKey(), "kinds", e.getValue()))
                        .toList()
        );
    }

    @GetMapping("/components/{artifactId}/versions")
    public List<MavenCentralClient.VersionInfo> versions(@PathVariable String artifactId,
                                                         @RequestParam(defaultValue = "org.apache.camel") String groupId) {
        return mavenCentral.listVersions(groupId, artifactId);
    }

    @GetMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyze(@RequestParam String artifactId,
                              @RequestParam String version,
                              @RequestParam(defaultValue = "org.apache.camel") String groupId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        Coordinate coord = new Coordinate(groupId, artifactId, version);
        log.info("Starting analysis for {}", coord.gav());

        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "analyze-" + artifactId);
            t.setDaemon(true);
            return t;
        }).submit(() -> {
            try {
                AnalysisResult result = analyzer.analyze(coord, p -> {
                    try {
                        emitter.send(SseEmitter.event().name("progress").data(p));
                    } catch (IOException ignored) {
                    }
                });
                emitter.send(SseEmitter.event().name("result").data(result));
                emitter.complete();
            } catch (Exception e) {
                log.error("Analysis failed for {}: {}", coord.gav(), e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", e.getMessage() == null ? "Unknown error" : e.getMessage())));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
