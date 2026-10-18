package net.pytrial.ui;

import net.pytrial.util.PyTrialLog;

import java.awt.Desktop;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

public final class FailureScreen {

    private static final String MINECRAFT = "net.minecraft.client.Minecraft";
    private static final String SCREEN = "net.minecraft.client.gui.screens.Screen";
    private static final String COMPONENT = "net.minecraft.network.chat.Component";
    private static final String CONFIRM_SCREEN = "net.minecraft.client.gui.screens.ConfirmScreen";
    private static final String BOOLEAN_CONSUMER = "it.unimi.dsi.fastutil.booleans.BooleanConsumer";
    private static final long[] DELAYS = {500L, 1500L};

    private FailureScreen() {
    }

    public static void showLater(List<String> failures, Path logFile) {
        Thread thread = new Thread(() -> schedule(failures, logFile), "pyTrial-failure-screen");
        thread.setDaemon(true);
        thread.start();
    }

    private static void schedule(List<String> failures, Path logFile) {
        ClassLoader loader = FailureScreen.class.getClassLoader();
        try {
            Class<?> minecraftClass = Class.forName(MINECRAFT, false, loader);
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            if (minecraft == null) {
                return;
            }
            Method execute = minecraftClass.getMethod("execute", Runnable.class);
            for (long delay : DELAYS) {
                Thread.sleep(delay);
                execute.invoke(minecraft, task(loader, minecraftClass, minecraft, failures, logFile));
            }
        } catch (Throwable t) {
            PyTrialLog.error("Could not schedule the failure screen: " + t, t);
        }
    }

    private static Runnable task(ClassLoader loader, Class<?> minecraftClass, Object minecraft,
                                 List<String> failures, Path logFile) {
        return () -> {
            try {
                Class<?> screenClass = Class.forName(SCREEN, false, loader);
                Class<?> componentClass = Class.forName(COMPONENT, false, loader);
                Class<?> confirmClass = Class.forName(CONFIRM_SCREEN, false, loader);
                Class<?> consumerClass = Class.forName(BOOLEAN_CONSUMER, false, loader);

                Method literal = componentClass.getMethod("literal", String.class);
                Object title = literal.invoke(null, "pyTrial: mod loading failed");
                Object message = literal.invoke(null, describe(failures, logFile));
                Object yes = literal.invoke(null, "Open log");
                Object no = literal.invoke(null, "Quit game");

                Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{consumerClass}, handler(logFile));
                Constructor<?> constructor = confirmClass.getConstructor(consumerClass, componentClass,
                        componentClass, componentClass, componentClass);
                Object screen = constructor.newInstance(callback, title, message, yes, no);

                minecraftClass.getMethod("setScreenAndShow", screenClass).invoke(minecraft, screen);
            } catch (Throwable t) {
                PyTrialLog.error("Could not show the failure screen: " + t, t);
            }
        };
    }

    private static InvocationHandler handler(Path logFile) {
        return (proxy, method, args) -> {
            switch (method.getName()) {
                case "accept" -> {
                    if (args != null && args.length > 0 && Boolean.TRUE.equals(args[0])) {
                        openLog(logFile);
                    } else {
                        quit();
                    }
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == (args != null && args.length > 0 ? args[0] : null);
                }
                case "toString" -> {
                    return "pyTrialFailureCallback";
                }
                default -> {
                }
            }
            return null;
        };
    }

    private static String describe(List<String> failures, Path logFile) {
        StringBuilder builder = new StringBuilder("pyTrial could not load these mods:\n\n");
        for (String failure : failures) {
            builder.append("- ").append(failure).append('\n');
        }
        builder.append("\nLog file:\n").append(logFile);
        return builder.toString();
    }

    private static void openLog(Path logFile) {
        PyTrialLog.info("Open log requested: " + logFile);
        if (launchViewer(logFile)) {
            return;
        }
        if (openWithDesktop(logFile)) {
            return;
        }
        PyTrialLog.warn("Could not open the log file, it is located at: " + logFile);
    }

    private static boolean launchViewer(Path logFile) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder builder;
            if (os.contains("win")) {
                builder = new ProcessBuilder("cmd", "/c", "start", "", logFile.toString());
            } else if (os.contains("mac")) {
                builder = new ProcessBuilder("open", logFile.toString());
            } else {
                builder = new ProcessBuilder("xdg-open", logFile.toString());
            }
            builder.redirectErrorStream(true);
            builder.start();
            PyTrialLog.info("Handed the log file to the system viewer");
            return true;
        } catch (Throwable t) {
            PyTrialLog.warn("Could not launch the system viewer: " + t.getMessage());
            return false;
        }
    }

    private static boolean openWithDesktop(Path logFile) {
        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop.getDesktop().open(logFile.toFile());
            PyTrialLog.info("Opened the log file with Desktop");
            return true;
        } catch (Throwable t) {
            PyTrialLog.warn("Desktop could not open the log file: " + t.getMessage());
            return false;
        }
    }

    private static void quit() {
        PyTrialLog.info("Quit requested from the failure screen");
        try {
            ClassLoader loader = FailureScreen.class.getClassLoader();
            Class<?> minecraftClass = Class.forName(MINECRAFT, false, loader);
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            if (minecraft != null) {
                minecraftClass.getMethod("stop").invoke(minecraft);
                return;
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Could not stop the game cleanly: " + t.getMessage());
        }
        System.exit(0);
    }
}
