package com.github.lhein.camelsanity.model;

public record Coordinate(String groupId, String artifactId, String version) {
    public String ga() {
        return groupId + ":" + artifactId;
    }

    public String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public static Coordinate parse(String gav) {
        String[] parts = gav.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid GAV: " + gav);
        }
        return new Coordinate(parts[0], parts[1], parts[2]);
    }
}
