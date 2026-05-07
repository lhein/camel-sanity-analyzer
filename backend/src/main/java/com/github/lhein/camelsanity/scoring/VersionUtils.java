package com.github.lhein.camelsanity.scoring;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionUtils {

    private static final Pattern MAJOR = Pattern.compile("^(\\d+).*");
    private static final Pattern QUALIFIER_TAIL =
            Pattern.compile(".*[.\\-](rc|cr|m|ea|pr)\\d*\\b.*", Pattern.CASE_INSENSITIVE);

    private VersionUtils() {}

    /**
     * True for pre-release versions (alpha, beta, RC, milestone, snapshot, etc.).
     * The check is intentionally permissive to avoid treating Alpha2 / RC4 / M1 / SNAPSHOT
     * as the "latest stable" of a project.
     */
    public static boolean isPreRelease(String version) {
        if (version == null) return false;
        String v = version.toLowerCase();
        if (v.contains("alpha") || v.contains("beta") || v.contains("milestone")
                || v.contains("snapshot") || v.contains("preview") || v.contains("incubat")) {
            return true;
        }
        return QUALIFIER_TAIL.matcher(v).matches();
    }

    public static Integer majorOf(String version) {
        if (version == null) return null;
        Matcher m = MAJOR.matcher(version.trim());
        if (m.matches()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static Integer majorVersionsBehind(String current, String latest) {
        Integer c = majorOf(current);
        Integer l = majorOf(latest);
        if (c == null || l == null) return null;
        return Math.max(0, l - c);
    }
}
