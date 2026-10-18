package net.pytrial.ui;

import net.pytrial.util.PyTrialLog;

import java.lang.reflect.Method;

/**
 * Resolves the display name of items.
 *
 * <p>{@code Item.getName} is patched to call {@link #of}, which keeps the vanilla behaviour (the
 * {@code ITEM_NAME} component, then the translation key) and serves the names declared by the mods
 * in between. Going through the component alone was not enough: an item built by reflection does not
 * run the component initialisers of {@code Item.Properties}.
 */
public final class ItemNames {

    private static final String ITEM = "net.minecraft.world.item.Item";
    private static final String ITEM_STACK = "net.minecraft.world.item.ItemStack";
    private static final String COMPONENT = "net.minecraft.network.chat.Component";
    private static final String COMMON_COMPONENTS = "net.minecraft.network.chat.CommonComponents";
    private static final String COMPONENTS = "net.minecraft.core.component.DataComponents";
    private static final String COMPONENT_MAP = "net.minecraft.core.component.DataComponentMap";
    private static final String COMPONENT_TYPE = "net.minecraft.core.component.DataComponentType";

    private static Method descriptionId;
    private static Method getComponents;
    private static Method getOrDefault;
    private static Method literal;
    private static Method translatable;
    private static Object itemNameType;
    private static Object emptyComponent;
    private static boolean resolved;
    private static boolean announced;

    private ItemNames() {
    }

    /** Called from the patched {@code Item.getName(ItemStack)}. */
    public static Object of(Object item, Object stack) {
        resolve();
        String key = descriptionId(item);
        if (key == null) {
            return null;
        }

        String declared = PyTrialSettings.modItemNames() ? ModTranslations.name(key) : null;
        if (declared != null) {
            if (!announced) {
                announced = true;
                PyTrialLog.info("Item names come from the mod declarations, first one: " + key
                        + " -> " + declared);
            }
            return invoke(literal, declared);
        }

        Object component = componentOf(stack);
        if (component != null) {
            return component;
        }
        return invoke(translatable, key);
    }

    private static Object componentOf(Object stack) {
        if (stack == null || getComponents == null || getOrDefault == null || itemNameType == null) {
            return null;
        }
        try {
            Object components = getComponents.invoke(stack);
            if (components == null) {
                return null;
            }
            Object name = getOrDefault.invoke(components, itemNameType, emptyComponent);
            return name == emptyComponent ? null : name;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String descriptionId(Object item) {
        try {
            Object value = descriptionId == null ? null : descriptionId.invoke(item);
            return value instanceof String id ? id : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invoke(Method method, String argument) {
        try {
            return method == null ? null : method.invoke(null, argument);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            ClassLoader loader = ItemNames.class.getClassLoader();
            descriptionId = Class.forName(ITEM, false, loader).getMethod("getDescriptionId");
            Class<?> stackClass = Class.forName(ITEM_STACK, false, loader);
            getComponents = stackClass.getMethod("getComponents");
            Class<?> mapClass = Class.forName(COMPONENT_MAP, false, loader);
            Class<?> typeClass = Class.forName(COMPONENT_TYPE, false, loader);
            getOrDefault = mapClass.getMethod("getOrDefault", typeClass, Object.class);
            Class<?> componentClass = Class.forName(COMPONENT, false, loader);
            literal = componentClass.getMethod("literal", String.class);
            translatable = componentClass.getMethod("translatable", String.class);
            itemNameType = Class.forName(COMPONENTS, false, loader).getField("ITEM_NAME").get(null);
            emptyComponent = Class.forName(COMMON_COMPONENTS, false, loader).getField("EMPTY").get(null);
        } catch (Throwable t) {
            PyTrialLog.warn("Item name resolution is unavailable: " + t.getMessage());
        }
    }
}
