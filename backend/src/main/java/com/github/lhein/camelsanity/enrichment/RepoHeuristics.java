package com.github.lhein.camelsanity.enrichment;

import com.github.lhein.camelsanity.model.Coordinate;

/**
 * Last-resort guesser for source repo URLs when neither deps.dev nor the deployed
 * POM provides one. Many Apache and other well-known projects deploy POMs without
 * an &lt;scm&gt; element, so a few heuristic mappings dramatically reduce UNKNOWN entries.
 */
public final class RepoHeuristics {

    private RepoHeuristics() {}

    public static String guess(Coordinate coord) {
        String g = coord.groupId();
        String a = coord.artifactId();

        // Apache Camel mono-repo
        if (g.equals("org.apache.camel") || g.startsWith("org.apache.camel.")) {
            return "https://github.com/apache/camel";
        }
        if (g.equals("org.apache.camel.kamelets")) {
            return "https://github.com/apache/camel-kamelets";
        }
        if (g.equals("org.apache.camel.springboot") || g.equals("org.apache.camel.spring-boot")) {
            return "https://github.com/apache/camel-spring-boot";
        }
        if (g.equals("org.apache.camel.quarkus")) {
            return "https://github.com/apache/camel-quarkus";
        }
        if (g.equals("org.apache.camel.k")) {
            return "https://github.com/apache/camel-k";
        }
        if (g.equals("org.apache.camel.karavan")) {
            return "https://github.com/apache/camel-karavan";
        }

        // Apache Commons each project lives in its own repo
        if (g.equals("org.apache.commons")) {
            return "https://github.com/apache/" + a;
        }

        // Spring projects
        if (g.startsWith("org.springframework.boot")) {
            return "https://github.com/spring-projects/spring-boot";
        }
        if (g.startsWith("org.springframework.cloud")) {
            return "https://github.com/spring-cloud/" + a;
        }
        if (g.startsWith("org.springframework.security")) {
            return "https://github.com/spring-projects/spring-security";
        }
        if (g.startsWith("org.springframework.data")) {
            return "https://github.com/spring-projects/" + a;
        }
        if (g.equals("org.springframework")) {
            return "https://github.com/spring-projects/spring-framework";
        }

        // Jackson
        if (g.startsWith("com.fasterxml.jackson")) {
            // Most are in jackson-core or jackson-databind; we use the meta repo
            return "https://github.com/FasterXML/" + a;
        }

        // Netty
        if (g.equals("io.netty")) {
            return "https://github.com/netty/netty";
        }

        // SLF4J / Logback
        if (g.equals("org.slf4j")) {
            return "https://github.com/qos-ch/slf4j";
        }
        if (g.equals("ch.qos.logback")) {
            return "https://github.com/qos-ch/logback";
        }

        // Quarkus
        if (g.startsWith("io.quarkus")) {
            return "https://github.com/quarkusio/quarkus";
        }

        // Vert.x
        if (g.equals("io.vertx")) {
            return "https://github.com/eclipse-vertx/" + a;
        }

        // Other common Apache projects (best-effort: "g.last.segment == repo name")
        if (g.startsWith("org.apache.")) {
            String project = g.substring("org.apache.".length()).split("\\.")[0];
            return "https://github.com/apache/" + project;
        }

        return null;
    }
}
