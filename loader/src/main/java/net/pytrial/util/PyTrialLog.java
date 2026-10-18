package net.pytrial.util;

import net.pytrial.PyTrial;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class PyTrialLog {

    public record Entry(String level, String text) {
    }

    private static final int HISTORY_CAPACITY = 12;
    private static final Deque<Entry> HISTORY = new ArrayDeque<>();

    private static volatile PrintWriter file;

    private PyTrialLog() {
    }

    public static void attachFile(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            file = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
        } catch (Throwable t) {
            file = null;
        }
    }

    public static List<Entry> history() {
        synchronized (HISTORY) {
            return List.copyOf(HISTORY);
        }
    }

    private static void record(String level, String text) {
        synchronized (HISTORY) {
            HISTORY.addLast(new Entry(level, text));
            while (HISTORY.size() > HISTORY_CAPACITY) {
                HISTORY.removeFirst();
            }
        }
    }

    private static void mirror(String line) {
        PrintWriter writer = file;
        if (writer != null) {
            writer.println(line);
        }
    }

    public static void info(String message) {
        String line = PyTrial.LOG_PREFIX + message;
        System.out.println(line);
        mirror(line);
        record("INFO", message);
    }

    public static void status(String message) {
        info(message);
    }

    public static void warn(String message) {
        String line = PyTrial.LOG_PREFIX + "WARN  " + message;
        System.out.println(line);
        mirror(line);
        record("WARN", message);
    }

    public static void error(String message) {
        String line = PyTrial.LOG_PREFIX + "ERROR " + message;
        System.err.println(line);
        mirror(line);
        record("ERROR", message);
    }

    public static void error(String message, Throwable cause) {
        error(message);
        cause.printStackTrace(System.err);
        PrintWriter writer = file;
        if (writer != null) {
            cause.printStackTrace(writer);
        }
    }

    public static void debug(String message) {
        if (isDebug()) {
            String line = PyTrial.LOG_PREFIX + "DEBUG " + message;
            System.out.println(line);
            mirror(line);
            record("DEBUG", message);
        }
    }

    public static boolean isDebug() {
        return Boolean.getBoolean("pytrial.debug");
    }

    public static void mod(String modId, String message) {
        String text = "[" + modId + "] " + message;
        String line = PyTrial.LOG_PREFIX + text;
        System.out.println(line);
        mirror(line);
        record("INFO", text);
    }
}
