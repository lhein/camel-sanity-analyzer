package com.github.lhein.camelsanity.model;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated health data for one dependency, gathered from multiple sources.
 * Any field can be null if the corresponding source had no data.
 */
public record HealthInfo(
        Coordinate coordinate,

        // Maven Central
        String latestVersion,
        Instant releaseDate,
        Instant latestReleaseDate,
        Integer majorVersionsBehind,

        // SCM / GitHub
        String organization,
        String website,
        String repoUrl,
        Instant lastCommit,
        Integer stars,
        Integer contributors,
        Integer openIssues,
        Boolean archived,
        Instant lastGithubRelease,

        // deps.dev
        String license,
        Integer dependents,

        // Security
        List<Vulnerability> vulnerabilities,

        // OpenSSF Scorecard
        Double scorecardScore,

        // Aggregated
        int healthScore,           // 0-100
        HealthStatus status,
        List<String> reasons,      // human-readable reasons for the status

        // Tree position: all scopes this coordinate appears with in the tree
        List<String> scopes,

        // Update classification: NONE / PATCH / MINOR / MAJOR / UNKNOWN
        String updateLevel,

        // License category: PERMISSIVE / WEAK_COPYLEFT / COPYLEFT / PUBLIC_DOMAIN / PROPRIETARY / UNKNOWN
        String licenseCategory,

        // Other versions of this artifact that appeared in the tree but lost
        // version-conflict resolution. Empty if none.
        List<String> conflictedVersions,

        // Reverse paths from root to this coordinate. Each path is the chain of
        // GAVs leading to it. Multiple paths if the dep is reachable through
        // several routes.
        List<List<String>> paths
) {
    public boolean isTestOnly() {
        return scopes != null && !scopes.isEmpty()
                && scopes.stream().allMatch(s -> "test".equals(s));
    }
}
