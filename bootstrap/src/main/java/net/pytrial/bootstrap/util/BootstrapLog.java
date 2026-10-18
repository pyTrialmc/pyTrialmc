package net.pytrial.bootstrap.util;

public final class BootstrapLog {

    private static final String PREFIX = "[pyTrial/bootstrap] ";

    private BootstrapLog() {
    }

    public static void info(String message) {
        System.out.println(PREFIX + message);
    }

    public static void warn(String message) {
        System.out.println(PREFIX + "WARN  " + message);
    }

    public static void error(String message) {
        System.err.println(PREFIX + "ERROR " + message);
    }

    public static void debug(String message) {
        if (Boolean.getBoolean("pytrial.debug")) {
            System.out.println(PREFIX + "DEBUG " + message);
        }
    }
}
