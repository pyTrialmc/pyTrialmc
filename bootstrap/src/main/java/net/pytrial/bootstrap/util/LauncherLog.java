package net.pytrial.bootstrap.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Mirrors everything the launcher prints into a log file.
 *
 * <p>The launcher is often started without a console (double click, {@code javaw}, launch script),
 * so output that only goes to stdout is lost. Attaching this log keeps the reason of a silent
 * failure available on disk.
 */
public final class LauncherLog {

    public static final String FILE_NAME = "launcher.log";

    private static boolean attached;

    private LauncherLog() {
    }

    public static synchronized void attach(Path logFile) {
        if (attached || logFile == null) {
            return;
        }
        try {
            Files.createDirectories(logFile.getParent());
            OutputStream file = Files.newOutputStream(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            PrintStream out = tee(System.out, file);
            PrintStream err = tee(System.err, file);
            System.setOut(out);
            System.setErr(err);
            attached = true;
            BootstrapLog.info("Launcher log: " + logFile);
        } catch (IOException e) {
            BootstrapLog.warn("Cannot write the launcher log: " + e.getMessage());
        }
    }

    private static PrintStream tee(PrintStream console, OutputStream file) {
        OutputStream mirror = new OutputStream() {

            @Override
            public void write(int value) throws IOException {
                console.write(value);
                file.write(value);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                console.write(bytes, offset, length);
                file.write(bytes, offset, length);
            }

            @Override
            public void flush() throws IOException {
                console.flush();
                file.flush();
            }
        };
        return new PrintStream(mirror, true, StandardCharsets.UTF_8);
    }
}
