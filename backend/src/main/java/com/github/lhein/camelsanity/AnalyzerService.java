package com.github.lhein.camelsanity;

import com.github.lhein.camelsanity.enrichment.DependencyEnricher;
import com.github.lhein.camelsanity.model.AnalysisResult;
import com.github.lhein.camelsanity.model.Coordinate;
import com.github.lhein.camelsanity.model.DependencyNode;
import com.github.lhein.camelsanity.model.HealthInfo;
import com.github.lhein.camelsanity.model.HealthStatus;
import com.github.lhein.camelsanity.resolver.MavenResolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public class AnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerService.class);

    private final MavenResolverService resolver;
    private final DependencyEnricher enricher;
    private final Executor enrichmentPool = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "analyzer-enrich");
        t.setDaemon(true);
        return t;
    });

    public AnalyzerService(MavenResolverService resolver, DependencyEnricher enricher) {
        this.resolver = resolver;
        this.enricher = enricher;
    }

    /**
     * Full pipeline: resolve tree → enrich each unique GAV → compute summary.
     * The progress callback is invoked between phases and on each completed enrichment.
     */
    public AnalysisResult analyze(Coordinate root, Consumer<Progress> progress) {
        progress.accept(new Progress("RESOLVING", 0, 0, "Resolving dependency tree…"));
        DependencyNode tree = resolver.resolveTree(root);

        Set<Coordinate> unique = new LinkedHashSet<>();
        Map<String, Set<String>> scopesByGav = new HashMap<>();
        collect(tree, unique, scopesByGav);
        int total = unique.size();
        progress.accept(new Progress("ENRICHING", 0, total, "Tree resolved: " + total + " unique dependencies"));

        Map<String, HealthInfo> health = new LinkedHashMap<>();

        // Submit all enrichments in parallel and update progress as they finish
        var futures = unique.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return enricher.enrich(c);
                    } catch (Exception e) {
                        log.warn("Enrichment failed for {}: {}", c.gav(), e.getMessage());
                        return null;
                    }
                }, enrichmentPool))
                .toList();

        int done = 0;
        for (var f : futures) {
            HealthInfo info = f.join();
            done++;
            if (info != null) {
                String gav = info.coordinate().gav();
                List<String> scopes = scopesByGav.getOrDefault(gav, Set.of()).stream()
                        .sorted().toList();
                HealthInfo withScopes = withScopes(info, scopes);
                synchronized (health) {
                    health.put(gav, withScopes);
                }
                progress.accept(new Progress("ENRICHING", done, total,
                        gav + " → " + withScopes.status()));
            }
        }

        AnalysisResult.Summary summary = computeSummary(health);
        progress.accept(new Progress("DONE", total, total, "Analysis complete"));
        return new AnalysisResult(root, Instant.now(), tree, health, summary);
    }

    private void collect(DependencyNode node, Set<Coordinate> out, Map<String, Set<String>> scopesByGav) {
        if (node == null) return;
        out.add(node.coordinate());
        if (node.scope() != null) {
            scopesByGav.computeIfAbsent(node.coordinate().gav(), k -> new TreeSet<>()).add(node.scope());
        }
        for (DependencyNode c : node.children()) {
            collect(c, out, scopesByGav);
        }
    }

    private HealthInfo withScopes(HealthInfo h, List<String> scopes) {
        return new HealthInfo(
                h.coordinate(), h.latestVersion(), h.releaseDate(), h.latestReleaseDate(),
                h.majorVersionsBehind(), h.organization(), h.website(), h.repoUrl(),
                h.lastCommit(), h.stars(), h.contributors(), h.openIssues(), h.archived(),
                h.lastGithubRelease(), h.license(), h.dependents(), h.vulnerabilities(),
                h.scorecardScore(), h.healthScore(), h.status(), h.reasons(), scopes);
    }

    private AnalysisResult.Summary computeSummary(Map<String, HealthInfo> health) {
        int total = health.size(), healthy = 0, outdated = 0, warning = 0, critical = 0, unknown = 0;
        int withVulns = 0, totalVulns = 0, archived = 0, testOnly = 0;
        for (HealthInfo h : health.values()) {
            switch (h.status()) {
                case HEALTHY -> healthy++;
                case OUTDATED -> outdated++;
                case WARNING -> warning++;
                case CRITICAL -> critical++;
                case UNKNOWN -> unknown++;
            }
            if (h.vulnerabilities() != null && !h.vulnerabilities().isEmpty()) {
                withVulns++;
                totalVulns += h.vulnerabilities().size();
            }
            if (Boolean.TRUE.equals(h.archived())) archived++;
            if (h.isTestOnly()) testOnly++;
        }
        return new AnalysisResult.Summary(total, healthy, outdated, warning, critical, unknown,
                withVulns, totalVulns, archived, testOnly);
    }

    public record Progress(String phase, int completed, int total, String message) {}
}
