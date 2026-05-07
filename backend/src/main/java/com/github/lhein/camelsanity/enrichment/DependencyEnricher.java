package com.github.lhein.camelsanity.enrichment;

import com.github.lhein.camelsanity.model.Coordinate;
import com.github.lhein.camelsanity.model.HealthInfo;
import com.github.lhein.camelsanity.model.HealthStatus;
import com.github.lhein.camelsanity.model.Vulnerability;
import com.github.lhein.camelsanity.scoring.HealthScorer;
import com.github.lhein.camelsanity.scoring.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Aggregates information about a single dependency from all enrichment sources
 * and produces a HealthInfo. Per-source failures are tolerated.
 */
@Service
public class DependencyEnricher {

    private static final Logger log = LoggerFactory.getLogger(DependencyEnricher.class);

    private final MavenCentralClient mavenCentral;
    private final PomMetadataClient pomClient;
    private final DepsDevClient depsDev;
    private final GitHubClient github;
    private final OsvClient osv;
    private final Executor ioPool = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "enricher-io");
        t.setDaemon(true);
        return t;
    });

    public DependencyEnricher(MavenCentralClient mavenCentral,
                              PomMetadataClient pomClient,
                              DepsDevClient depsDev,
                              GitHubClient github,
                              OsvClient osv) {
        this.mavenCentral = mavenCentral;
        this.pomClient = pomClient;
        this.depsDev = depsDev;
        this.github = github;
        this.osv = osv;
    }

    public HealthInfo enrich(Coordinate coord) {
        // Run independent fetches in parallel
        CompletableFuture<Optional<MavenCentralClient.VersionInfo>> latestF =
                supply(() -> mavenCentral.latestVersion(coord), Optional.empty());
        CompletableFuture<PomMetadataClient.PomMetadata> pomF =
                supply(() -> pomClient.fetch(coord), PomMetadataClient.PomMetadata.empty());
        CompletableFuture<DepsDevClient.DepsDevInfo> depsF =
                supply(() -> depsDev.fetchVersion(coord), DepsDevClient.DepsDevInfo.empty());
        CompletableFuture<List<Vulnerability>> osvF =
                supply(() -> osv.query(coord), List.of());

        // Wait for the ones that feed downstream lookups
        DepsDevClient.DepsDevInfo depsInfo = depsF.join();
        PomMetadataClient.PomMetadata pom = pomF.join();
        Optional<MavenCentralClient.VersionInfo> latest = latestF.join();

        // Repo URL resolution order (each step skipped if null/no GitHub match):
        //   1. deps.dev sourceRepo (most reliable when present)
        //   2. POM <scm> URL (parent chain followed)
        //   3. POM <url> / <organization>/<url> if they happen to point at GitHub
        //      — many small projects skip <scm> but link the GitHub repo as the
        //      project homepage
        //   4. RepoHeuristics for well-known group prefixes
        String resolvedRepo = depsInfo.sourceRepo();
        if (resolvedRepo == null) resolvedRepo = pom.scmUrl();
        if (resolvedRepo == null) resolvedRepo = githubLike(pom.projectUrl());
        if (resolvedRepo == null) resolvedRepo = githubLike(pom.organizationUrl());
        if (resolvedRepo == null) resolvedRepo = RepoHeuristics.guess(coord);
        final String repoUrl = resolvedRepo;

        // Now downstream: GitHub repo, deps.dev project, deps.dev for latest version's publishedAt
        Optional<GitHubClient.OwnerRepo> ownerRepo = GitHubClient.parseRepo(repoUrl);
        CompletableFuture<GitHubClient.RepoInfo> ghF = ownerRepo
                .map(or -> supply(() -> github.fetchRepo(or.owner(), or.repo()),
                        GitHubClient.RepoInfo.empty()))
                .orElse(CompletableFuture.completedFuture(GitHubClient.RepoInfo.empty()));

        CompletableFuture<DepsDevClient.ProjectInfo> projF = repoUrl != null
                ? supply(() -> depsDev.fetchProject(normalizeProjectId(repoUrl)),
                        DepsDevClient.ProjectInfo.empty())
                : CompletableFuture.completedFuture(DepsDevClient.ProjectInfo.empty());

        // Fetch the latest stable version's publishedAt, but only if it differs from the
        // current coord — otherwise we'd just hit the cache for the same key.
        CompletableFuture<DepsDevClient.DepsDevInfo> latestDepsF = latest
                .filter(v -> !v.version().equals(coord.version()))
                .map(v -> supply(() -> depsDev.fetchVersion(
                                new Coordinate(coord.groupId(), coord.artifactId(), v.version())),
                        DepsDevClient.DepsDevInfo.empty()))
                .orElse(CompletableFuture.completedFuture(depsInfo));

        GitHubClient.RepoInfo gh = ghF.join();
        DepsDevClient.ProjectInfo proj = projF.join();
        DepsDevClient.DepsDevInfo latestDeps = latestDepsF.join();
        List<Vulnerability> vulns = osvF.join();

        // Aggregate
        Integer behind = latest
                .map(v -> VersionUtils.majorVersionsBehind(coord.version(), v.version()))
                .orElse(null);
        Instant releaseDate = depsInfo.publishedAt();
        Instant latestReleaseDate = latestDeps.publishedAt();

        HealthScorer.Builder b = new HealthScorer.Builder();
        b.archived = gh.archived();
        b.lastCommit = gh.lastPush();
        b.lastGithubRelease = gh.latestRelease();
        b.latestMavenReleaseDate = latestReleaseDate;
        b.majorVersionsBehind = behind;
        b.contributors = gh.contributors();
        b.scorecardScore = proj.scorecardScore();
        b.vulnerabilities = vulns;
        HealthScorer.Result scored = HealthScorer.evaluate(b);

        HealthStatus status = scored.status();
        if (gh.lastPush() == null && proj.scorecardScore() == null && vulns.isEmpty()
                && status == HealthStatus.HEALTHY && repoUrl == null) {
            // No data → mark UNKNOWN, not HEALTHY
            status = HealthStatus.UNKNOWN;
        }

        Integer stars = gh.stars() != null ? gh.stars() : proj.stars();

        return new HealthInfo(
                coord,
                latest.map(MavenCentralClient.VersionInfo::version).orElse(null),
                releaseDate,
                latestReleaseDate,
                behind,
                pom.organizationName(),
                firstNonBlank(pom.projectUrl(), pom.organizationUrl(), gh.homepage()),
                gh.htmlUrl() != null ? gh.htmlUrl() : repoUrl,
                gh.lastPush(),
                stars,
                gh.contributors(),
                gh.openIssues(),
                gh.archived(),
                gh.latestRelease(),
                depsInfo.license(),
                null,
                vulns,
                proj.scorecardScore(),
                scored.score(),
                status,
                scored.reasons(),
                List.of()  // scopes filled in later by AnalyzerService
        );
    }

    private <T> CompletableFuture<T> supply(java.util.function.Supplier<T> s, T fallback) {
        return CompletableFuture.supplyAsync(s, ioPool).exceptionally(e -> {
            log.debug("Enrichment step failed: {}", e.getMessage());
            return fallback;
        });
    }

    private static String normalizeProjectId(String repoUrl) {
        // deps.dev uses "github.com/owner/repo"
        if (repoUrl == null) return null;
        String r = repoUrl.replaceAll("^.*github\\.com[/:]", "github.com/")
                .replaceAll("\\.git$", "")
                .replaceAll("/+$", "");
        if (!r.startsWith("github.com/")) return null;
        return r;
    }

    private static String firstNonBlank(String... s) {
        for (String x : s) if (x != null && !x.isBlank()) return x;
        return null;
    }

    /**
     * Returns the URL only if it looks like a GitHub repo (owner/repo path),
     * otherwise null. Used to opportunistically pick up SCM links from POM
     * fields like &lt;url&gt; or &lt;organization&gt;/&lt;url&gt; which sometimes
     * point at GitHub even though no &lt;scm&gt; was declared.
     */
    private static String githubLike(String url) {
        if (url == null || url.isBlank()) return null;
        return GitHubClient.parseRepo(url).map(or -> url).orElse(null);
    }
}
