package com.github.lhein.camelsanity.scoring;

/**
 * Coarse categorization of an SPDX-ish license identifier. Used to group
 * dependencies in the Licenses view. The match is a substring/case-insensitive
 * heuristic — close enough for the License panel without trying to be a full
 * SPDX validator.
 */
public enum LicenseCategory {
    PERMISSIVE,
    WEAK_COPYLEFT,
    COPYLEFT,
    PUBLIC_DOMAIN,
    PROPRIETARY,
    UNKNOWN;

    public static LicenseCategory classify(String license) {
        if (license == null || license.isBlank()) return UNKNOWN;
        String l = license.toLowerCase();

        if (l.contains("public domain") || l.contains("cc0") || l.contains("unlicense")) {
            return PUBLIC_DOMAIN;
        }
        if (l.contains("agpl")) return COPYLEFT;
        if (l.contains("gpl")) {
            return l.contains("lgpl") ? WEAK_COPYLEFT : COPYLEFT;
        }
        if (l.contains("mpl") || l.contains("mozilla")
                || l.contains("epl") || l.contains("eclipse public license")
                || l.contains("cddl") || l.contains("eupl")) {
            return WEAK_COPYLEFT;
        }
        if (l.contains("apache") || l.contains("mit") || l.contains("bsd")
                || l.contains("isc") || l.contains("zlib") || l.contains("boost")
                || l.contains("wtfpl")) {
            return PERMISSIVE;
        }
        if (l.contains("proprietary") || l.contains("commercial")
                || l.contains("all rights reserved")) {
            return PROPRIETARY;
        }
        return UNKNOWN;
    }
}
