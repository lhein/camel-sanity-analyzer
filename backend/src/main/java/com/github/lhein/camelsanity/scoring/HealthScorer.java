package com.github.lhein.camelsanity.scoring;

import com.github.lhein.camelsanity.model.HealthInfo;
import com.github.lhein.camelsanity.model.HealthStatus;
import com.github.lhein.camelsanity.model.Vulnerability;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes a 0-100 score and a HealthStatus based on multiple signals.
 * The score is informational; the status is what drives UI highlighting.
 */
public final class HealthScorer {

    private HealthScorer() {}

    public static Result evaluate(Builder b) {
        int score = 100;
        List<String> reasons = new ArrayList<>();
        HealthStatus status = HealthStatus.HEALTHY;

        // --- CRITICAL signals ------------------------------------------------
        if (Boolean.TRUE.equals(b.archived)) {
            score -= 50;
            reasons.add("Repository is archived on GitHub");
            status = worst(status, HealthStatus.CRITICAL);
        }

        long highCves = countSeverity(b.vulnerabilities, "HIGH", "CRITICAL");
        long mediumCves = countSeverity(b.vulnerabilities, "MODERATE", "MEDIUM");
        long lowCves = countSeverity(b.vulnerabilities, "LOW");
        if (highCves > 0) {
            score -= (int) Math.min(40, 20 + highCves * 5);
            reasons.add(highCves + " high/critical vulnerabilit" + (highCves == 1 ? "y" : "ies"));
            status = worst(status, HealthStatus.CRITICAL);
        }
        if (mediumCves > 0) {
            score -= (int) Math.min(15, mediumCves * 4);
            reasons.add(mediumCves + " medium vulnerabilit" + (mediumCves == 1 ? "y" : "ies"));
            status = worst(status, HealthStatus.WARNING);
        }
        if (lowCves > 0) {
            score -= (int) Math.min(8, lowCves * 2);
            reasons.add(lowCves + " low vulnerabilit" + (lowCves == 1 ? "y" : "ies"));
        }

        // --- Activity --------------------------------------------------------
        if (b.lastCommit != null) {
            long monthsSinceCommit = monthsSince(b.lastCommit);
            if (monthsSinceCommit >= 24) {
                score -= 25;
                reasons.add("No commit for " + monthsSinceCommit + " months");
                status = worst(status, HealthStatus.CRITICAL);
            } else if (monthsSinceCommit >= 12) {
                score -= 15;
                reasons.add("No commit for " + monthsSinceCommit + " months");
                status = worst(status, HealthStatus.WARNING);
            }
        }

        // --- Releases --------------------------------------------------------
        Instant latestRelease = laterOf(b.lastGithubRelease, b.latestMavenReleaseDate);
        if (latestRelease != null) {
            long monthsSinceRelease = monthsSince(latestRelease);
            if (monthsSinceRelease >= 36) {
                score -= 15;
                reasons.add("No release for " + monthsSinceRelease + " months");
                status = worst(status, HealthStatus.WARNING);
            } else if (monthsSinceRelease >= 24) {
                score -= 10;
                reasons.add("No release for " + monthsSinceRelease + " months");
                status = worst(status, HealthStatus.WARNING);
            }
        }

        // --- Outdated --------------------------------------------------------
        if (b.majorVersionsBehind != null && b.majorVersionsBehind >= 2) {
            score -= 10;
            reasons.add(b.majorVersionsBehind + " major versions behind latest");
            status = worst(status, HealthStatus.OUTDATED);
        } else if (b.majorVersionsBehind != null && b.majorVersionsBehind == 1) {
            score -= 5;
            reasons.add("1 major version behind latest");
        }

        // --- Bus factor / community -----------------------------------------
        if (b.contributors != null && b.contributors == 1) {
            score -= 8;
            reasons.add("Single contributor (bus factor 1)");
            status = worst(status, HealthStatus.WARNING);
        } else if (b.contributors != null && b.contributors <= 3) {
            score -= 3;
            reasons.add("Very few contributors (" + b.contributors + ")");
        }

        // --- Scorecard -------------------------------------------------------
        if (b.scorecardScore != null) {
            if (b.scorecardScore < 3.0) {
                score -= 12;
                reasons.add(String.format("Low OpenSSF Scorecard (%.1f)", b.scorecardScore));
                status = worst(status, HealthStatus.WARNING);
            } else if (b.scorecardScore < 5.0) {
                score -= 6;
                reasons.add(String.format("Mediocre OpenSSF Scorecard (%.1f)", b.scorecardScore));
            }
        }

        score = Math.max(0, Math.min(100, score));

        // Final status mapping by score floor
        if (status == HealthStatus.HEALTHY && score < 80) {
            status = HealthStatus.WARNING;
            reasons.add("Composite health score below 80");
        }

        return new Result(score, status, List.copyOf(reasons));
    }

    private static long monthsSince(Instant t) {
        return Duration.between(t, Instant.now()).toDays() / 30;
    }

    private static Instant laterOf(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static long countSeverity(List<Vulnerability> vulns, String... wanted) {
        if (vulns == null) return 0;
        return vulns.stream()
                .map(v -> v.severity() == null ? "" : v.severity().toUpperCase())
                .filter(s -> {
                    for (String w : wanted) if (s.contains(w)) return true;
                    return false;
                })
                .count();
    }

    private static HealthStatus worst(HealthStatus a, HealthStatus b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(HealthStatus s) {
        return switch (s) {
            case CRITICAL -> 4;
            case WARNING -> 3;
            case OUTDATED -> 2;
            case UNKNOWN -> 1;
            case HEALTHY -> 0;
        };
    }

    public record Result(int score, HealthStatus status, List<String> reasons) {}

    public static class Builder {
        public Boolean archived;
        public Instant lastCommit;
        public Instant lastGithubRelease;
        public Instant latestMavenReleaseDate;
        public Integer majorVersionsBehind;
        public Integer contributors;
        public Double scorecardScore;
        public List<Vulnerability> vulnerabilities;
    }
}
