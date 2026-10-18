package net.pytrial.bootstrap.launch;

import java.util.List;
import java.util.Map;

public final class RuleEvaluator {

    private RuleEvaluator() {
    }

    public static boolean allows(List<VersionJson.Rule> rules, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        for (int i = rules.size() - 1; i >= 0; i--) {
            VersionJson.Rule rule = rules.get(i);
            if (matches(rule, features)) {
                return !"disallow".equalsIgnoreCase(rule.action);
            }
        }
        return false;
    }

    private static boolean matches(VersionJson.Rule rule, Map<String, Boolean> features) {
        if (rule.os != null && !matchesOs(rule.os)) {
            return false;
        }
        if (rule.features != null && !rule.features.isEmpty()) {
            for (Map.Entry<String, Boolean> entry : rule.features.entrySet()) {
                boolean actual = features != null && Boolean.TRUE.equals(features.get(entry.getKey()));
                if (actual != Boolean.TRUE.equals(entry.getValue())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesOs(VersionJson.Os os) {
        if (os.name != null && !os.name.isBlank() && !os.name.equalsIgnoreCase(currentOsName())) {
            return false;
        }
        if (os.arch != null && !os.arch.isBlank()
                && !os.arch.equalsIgnoreCase(System.getProperty("os.arch", ""))) {
            return false;
        }
        if (os.version != null && !os.version.isBlank()) {
            String actual = System.getProperty("os.version", "");
            try {
                if (!actual.matches(os.version)) {
                    return false;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return true;
    }

    public static String currentOsName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "osx";
        }
        return "linux";
    }
}
