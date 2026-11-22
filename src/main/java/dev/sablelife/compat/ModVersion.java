package dev.sablelife.compat;

import org.apache.maven.artifact.versioning.ComparableVersion;

public final class ModVersion {
    public static boolean atLeast(String actual, String minimum) {
        return !actual.isBlank() && new ComparableVersion(actual).compareTo(new ComparableVersion(minimum)) >= 0;
    }

    private ModVersion() {}
}
