package com.github.lhein.camelsanity.scoring;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionUtils {

    private static final Pattern MAJOR = Pattern.compile("^(\\d+).*");

    private VersionUtils() {}

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
