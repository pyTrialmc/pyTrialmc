package net.pytrial.bootstrap;

import net.pytrial.bootstrap.install.ClientInstaller;
import net.pytrial.bootstrap.ui.InstallWindow;
import net.pytrial.bootstrap.util.BootstrapLog;
import net.pytrial.bootstrap.util.LauncherLog;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point of the pyTrial client installer.
 *
 * <p>Without arguments it opens the installation window; with a command name it behaves like the
 * bootstrap, except for {@code install}, which uses the loader bundled into this installer.
 */
public final class ClientInstallerMain {

    private static final List<String> BOOTSTRAP_COMMANDS = List.of(
            "launch", "info", "versions", "list-versions", "mods", "list-mods", "help", "--help", "-h");

    private ClientInstallerMain() {
    }

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : null;

        if (command != null && BOOTSTRAP_COMMANDS.contains(command)) {
            BootstrapMain.main(args);
            return;
        }

        if ("install".equals(command)) {
            int code = install(args);
            if (code != 0) {
                System.exit(code);
            }
            return;
        }

        if (GraphicsEnvironment.isHeadless()) {
            BootstrapLog.warn("No graphical environment available, use the command line instead:"
                    + " install / launch / help");
            BootstrapMain.main(new String[]{"help"});
            return;
        }

        InstallWindow.open();
    }

    private static int install(String[] args) {
        Path gameDir = null;
        String versionId = null;
        Path modsDir = null;
        Path loaderJar = null;
        boolean withScripts = false;

        try {
            for (int index = 1; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--game-dir", "--gamedir" -> gameDir = Path.of(next(args, ++index));
                    case "--version", "-v" -> versionId = next(args, ++index);
                    case "--mods", "--mods-dir" -> modsDir = Path.of(next(args, ++index));
                    case "--loader" -> loaderJar = Path.of(next(args, ++index));
                    case "--scripts" -> withScripts = true;
                    default -> BootstrapLog.warn("Ignoring unknown argument: " + arg);
                }
            }

            Path resolvedGameDir = ClientInstaller.detectGameDir(
                    gameDir != null ? gameDir.toString() : null);
            LauncherLog.attach(resolvedGameDir.resolve(ClientInstaller.DIRECTORY_NAME)
                    .resolve("logs").resolve(LauncherLog.FILE_NAME));
            ClientInstaller.install(resolvedGameDir, versionId, modsDir, loaderJar, withScripts,
                    BootstrapLog::info);
            return 0;
        } catch (Exception error) {
            BootstrapLog.error(error.getMessage() != null ? error.getMessage() : error.toString());
            if (Boolean.getBoolean("pytrial.debug")) {
                error.printStackTrace();
            }
            return 1;
        }
    }

    private static String next(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }
}
