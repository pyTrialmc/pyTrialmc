package net.pytrial.ui;

import net.pytrial.util.PyTrialLog;

import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CreativeTabTextures {

    public static final int CAPACITY = 32;
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private CreativeTabTextures() {
    }

    public static boolean fits(int column) {
        return column >= 0 && column < CAPACITY;
    }

    public static Object[] expand(Object[] original) {
        if (original == null || original.length == 0 || original.length >= CAPACITY) {
            return original;
        }
        Object filler = original[original.length - 1];
        Object[] expanded = (Object[]) Array.newInstance(original.getClass().getComponentType(), CAPACITY);
        System.arraycopy(original, 0, expanded, 0, original.length);
        for (int index = original.length; index < CAPACITY; index++) {
            expanded[index] = filler;
        }
        if (LOGGED.compareAndSet(false, true)) {
            PyTrialLog.info("Creative tab textures expanded from " + original.length + " to " + CAPACITY
                    + " slots, extra tabs reuse the last vanilla tab texture");
        }
        return expanded;
    }
}
