package net.pytrial.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.pytrial.util.PyTrialLog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class CommandBridge {

    public record CommandEntry(String name, String description) {
    }

    private static final List<CommandEntry> DEFINITIONS = new ArrayList<>();

    private final Supplier<String> statusSupplier;

    public CommandBridge(Supplier<String> statusSupplier) {
        this.statusSupplier = statusSupplier;
    }

    public static synchronized void add(String name, String description) {
        DEFINITIONS.add(new CommandEntry(name, description));
    }

    public static synchronized List<CommandEntry> definitions() {
        return List.copyOf(DEFINITIONS);
    }

    @SuppressWarnings("unchecked")
    public int apply(Object commandsInstance) {
        if (commandsInstance == null) {
            return 0;
        }
        CommandDispatcher<Object> dispatcher;
        try {
            Method getter = commandsInstance.getClass().getMethod("getDispatcher");
            dispatcher = (CommandDispatcher<Object>) getter.invoke(commandsInstance);
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot obtain the command dispatcher: " + t.getMessage());
            return 0;
        }
        if (dispatcher == null) {
            return 0;
        }

        int registered = 0;
        for (CommandEntry entry : definitions()) {
            if (dispatcher.getRoot().getChild(entry.name()) != null) {
                continue;
            }
            if (register(dispatcher, entry, commandsInstance.getClass().getClassLoader())) {
                registered++;
            }
        }
        return registered;
    }

    private boolean register(CommandDispatcher<Object> dispatcher, CommandEntry entry, ClassLoader loader) {
        try {
            LiteralArgumentBuilder<Object> builder = LiteralArgumentBuilder.literal(entry.name());
            builder.executes(context -> {
                announce(context.getSource(), loader);
                return 1;
            });
            dispatcher.register(builder);
            PyTrialLog.info("Registered command /" + entry.name());
            return true;
        } catch (Throwable t) {
            PyTrialLog.error("Failed to register command /" + entry.name() + ": " + t, t);
            return false;
        }
    }

    private void announce(Object source, ClassLoader loader) {
        String text = statusSupplier != null ? statusSupplier.get() : "pyTrial";
        try {
            Object message = literal(text, loader);
            if (message == null) {
                return;
            }
            Method send = findSendMethod(source.getClass());
            if (send == null) {
                return;
            }
            send.invoke(source, message);
        } catch (Throwable t) {
            PyTrialLog.warn("Failed to send command feedback: " + t.getMessage());
        }
    }

    private static Method findSendMethod(Class<?> owner) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals("sendSystemMessage") && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    private static Object literal(String text, ClassLoader loader) {
        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component", true, loader);
            Method literal = componentClass.getMethod("literal", String.class);
            return literal.invoke(null, text);
        } catch (Throwable t) {
            return null;
        }
    }
}
