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
        List<String> reasons       // human-readable reasons for the status
) {
}
