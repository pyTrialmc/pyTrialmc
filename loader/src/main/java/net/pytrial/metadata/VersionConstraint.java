package net.pytrial.metadata;

import java.util.ArrayList;
import java.util.List;

public final class VersionConstraint {

    private VersionConstraint() {
    }

    public static boolean matches(String actualVersion, String requirement) {
        if (requirement == null || requirement.isBlank() || "*".equals(requirement.trim())) {
            return true;
        }
        String actual = actualVersion == null || actualVersion.isBlank() ? "0" : actualVersion.trim();

        for (String part : requirement.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (!evaluate(actual, token)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluate(String actual, String token) {
        if (token.startsWith(">=")) {
            return compare(actual, token.substring(2)) >= 0;
        }
        if (token.startsWith("<=")) {
            return compare(actual, token.substring(2)) <= 0;
        }
        if (token.startsWith("!=")) {
            return compare(actual, token.substring(2)) != 0;
        }
        if (token.startsWith(">")) {
            return compare(actual, token.substring(1)) > 0;
        }
        if (token.startsWith("<")) {
            return compare(actual, token.substring(1)) < 0;
        }
        if (token.startsWith("=")) {
            return compare(actual, token.substring(1)) == 0;
        }
        return compare(actual, token) == 0;
    }

    public static int compare(String left, String right) {
        List<Integer> a = segments(left);
        List<Integer> b = segments(right);
        int length = Math.max(a.size(), b.size());
        for (int i = 0; i < length; i++) {
            int x = i < a.size() ? a.get(i) : 0;
            int y = i < b.size() ? b.get(i) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static List<Integer> segments(String version) {
        List<Integer> result = new ArrayList<>();
        for (String part : version.split("[.\\-+]")) {
            StringBuilder digits = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else {
                    break;
                }
            }
            if (digits.length() == 0) {
                continue;
            }
            try {
                result.add(Integer.parseInt(digits.toString()));
            } catch (NumberFormatException e) {
                result.add(0);
            }
        }
        if (result.isEmpty()) {
            result.add(0);
        }
        return result;
    }
}
