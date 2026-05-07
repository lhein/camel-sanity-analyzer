package com.github.lhein.camelsanity.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalysisResult(
        Coordinate root,
        Instant analyzedAt,
        DependencyNode tree,
        Map<String, HealthInfo> healthByGav,  // key = "groupId:artifactId:version"
        Summary summary) {

    public record Summary(
            int total,
            int healthy,
            int outdated,
            int warning,
            int critical,
            int unknown,
            int withVulnerabilities,
            int totalVulnerabilities,
            int archivedRepos) {
    }

    public List<HealthInfo> flatList() {
        return healthByGav.values().stream().toList();
    }
}
