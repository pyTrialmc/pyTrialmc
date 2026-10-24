package net.pytrial.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.pytrial.util.PyTrialLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Locale;

/**
 * Registry bridge for the element kinds that are a single object in a vanilla registry.
 *
 * <p>{@link GameBridge} covers blocks, items and creative tabs, which need bespoke wiring. Everything
 * else that ends up in a registry — a particle type, an attribute, a mob effect, a game rule, a
 * custom registry entry — goes through here, so adding a kind is a row in {@link #register} rather
 * than a new class.
 *
 * <p>Every kind is attempted defensively: a missing class or constructor logs one line and returns
 * {@code null} instead of failing the whole registration pass.
 */
public final class RegistryBridge {

    private static final String IDENTIFIER = "net.minecraft.resources.Identifier";
    private static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";
    private static final String REGISTRY = "net.minecraft.core.Registry";
    private static final String BUILT_IN_REGISTRIES = "net.minecraft.core.registries.BuiltInRegistries";
    private static final String REGISTRIES = "net.minecraft.core.registries.Registries";

    private final ClassLoader loader;

    private boolean prepared;
    private Class<?> identifierClass;
    private Class<?> resourceKeyClass;
    private Class<?> registryClass;
    private Object builtIn;
    private Object registries;
    private Method identifierFactory;
    private Method resourceKeyFactory;
    private Method registerMethod;

    public RegistryBridge(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Register one element.
     *
     * @param kind    element kind, as declared by the mod
     * @param payload the declaration, options included
     * @return the registered object, or {@code null} when this kind cannot be bridged
     */
    public Object register(String kind, String namespace, String path, JsonObject payload) {
        prepare();
        if (registerMethod == null) {
            return null;
        }
        try {
            return switch (kind) {
                case "particle" -> particle(namespace, path, payload);
                case "attribute" -> attribute(namespace, path, payload);
                case "potion_effect" -> potionEffect(namespace, path, payload);
                case "game_rule" -> gameRule(namespace, path, payload);
                case "tool", "armor", "potion_item", "plant", "item_extension" ->
                        componentItem(kind, namespace, path, payload);
                case "living_entity" -> entity(namespace, path, payload, "net.minecraft.world.entity.animal.Pig",
                        "CREATURE");
                case "projectile" -> entity(namespace, path, payload, "net.minecraft.world.entity.projectile.Arrow",
                        "MISC");
                case "special_entity" -> entity(namespace, path, payload,
                        "net.minecraft.world.entity.decoration.ArmorStand", "MISC");
                case "custom_element" -> customElement(namespace, path, payload);
                case "fluid" -> fluid(namespace, path, payload);
                case "key_binding" -> keyBinding(namespace, path, payload);
                default -> null;
            };
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot register " + kind + " " + namespace + ":" + path + ": " + t);
            return null;
        }
    }

    /** True when this class knows how to bridge the kind. */
    public boolean handles(String kind) {
        return switch (kind) {
            case "particle", "attribute", "potion_effect", "game_rule", "tool", "armor", "potion_item",
                 "plant", "item_extension", "living_entity", "projectile", "special_entity",
                 "custom_element", "fluid", "key_binding" -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ kinds

    private Object particle(String namespace, String path, JsonObject payload) throws Throwable {
        Class<?> type = Class.forName("net.minecraft.core.particles.SimpleParticleType", true, loader);
        Constructor<?> constructor = type.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        Object value = constructor.newInstance(!payload.has("alwaysShow") || payload.get("alwaysShow").getAsBoolean());
        return put("PARTICLE_TYPE", namespace, path, value);
    }

    private Object attribute(String namespace, String path, JsonObject payload) throws Throwable {
        Class<?> type = Class.forName("net.minecraft.world.entity.ai.attributes.RangedAttribute", true, loader);
        double fallback = number(payload, "default", 0);
        double min = number(payload, "min", 0);
        double max = number(payload, "max", 1024);
        Object value = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            try {
                constructor.setAccessible(true);
                if (parameters.length == 4 && parameters[0] == String.class) {
                    value = constructor.newInstance(namespace + "." + path, fallback, min, max);
                } else if (parameters.length == 4 && parameters[0] == identifierClass) {
                    value = constructor.newInstance(identifier(namespace, path), fallback, min, max);
                } else if (parameters.length == 5) {
                    value = constructor.newInstance(identifier(namespace, path), fallback, min, max, true);
                }
                if (value != null) {
                    break;
                }
            } catch (Throwable ignored) {
                // try the next signature
            }
        }
        if (value == null) {
            throw new IllegalStateException("no usable RangedAttribute constructor");
        }
        try {
            Method sync = type.getMethod("setSyncable", boolean.class);
            sync.invoke(value, true);
        } catch (Throwable ignored) {
            // older versions are always syncable
        }
        return put("ATTRIBUTE", namespace, path, value);
    }

    private Object potionEffect(String namespace, String path, JsonObject payload) throws Throwable {
        Class<?> type = Class.forName("net.minecraft.world.effect.MobEffect", true, loader);
        Class<?> category = Class.forName("net.minecraft.world.effect.MobEffectCategory", true, loader);
        Object benign = Enum.valueOf(category.asSubclass(Enum.class),
                String.valueOf(payload.has("category") ? payload.get("category").getAsString() : "BENEFICIAL")
                        .toUpperCase(Locale.ROOT));
        int color = (int) number(payload, "color", 0x3F76E4);
        Constructor<?> constructor = type.getDeclaredConstructor(category, int.class);
        constructor.setAccessible(true);
        return put("MOB_EFFECT", namespace, path, constructor.newInstance(benign, color));
    }

    private Object gameRule(String namespace, String path, JsonObject payload) throws Throwable {
        Class<?> rules = Class.forName("net.minecraft.world.level.GameRules", true, loader);
        String name = namespace + ":" + path;
        String type = String.valueOf(payload.has("type") ? payload.get("type").getAsString() : "boolean");
        Object value;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "int", "integer" -> {
                Class<?> valueType = Class.forName("net.minecraft.world.level.GameRules$IntegerValue", true, loader);
                Constructor<?> constructor = valueType.getDeclaredConstructor(int.class);
                constructor.setAccessible(true);
                value = constructor.newInstance((int) number(payload, "default", 0));
            }
            default -> {
                Class<?> valueType = Class.forName("net.minecraft.world.level.GameRules$BooleanValue", true, loader);
                Constructor<?> constructor = valueType.getDeclaredConstructor(boolean.class);
                constructor.setAccessible(true);
                value = constructor.newInstance(payload.has("default") && payload.get("default").getAsBoolean());
            }
        }
        Method register = null;
        for (Method method : rules.getDeclaredMethods()) {
            if (method.getName().equals("register") && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 2 && method.getParameterTypes()[0] == String.class) {
                register = method;
                register.setAccessible(true);
                break;
            }
        }
        if (register == null) {
            throw new IllegalStateException("GameRules.register not found");
        }
        register.invoke(null, name, value);
        return value;
    }

    /**
     * An item whose behaviour comes from data components rather than a subclass: tools and armour are
     * plain items with a durability and an attribute modifier, potions are items with potion contents.
     */
    private Object componentItem(String kind, String namespace, String path, JsonObject payload) throws Throwable {
        Class<?> itemClass = Class.forName("net.minecraft.world.item.Item", true, loader);
        Class<?> propertiesClass = Class.forName("net.minecraft.world.item.Item$Properties", true, loader);
        Class<?> componentsClass = Class.forName("net.minecraft.core.component.DataComponents", true, loader);

        Object properties = newProperties(propertiesClass);
        applyComponents(properties, componentsClass, kind, payload);

        Object id = identifier(namespace, path);
        Object key = resourceKeyFactory.invoke(null, registryKey("ITEM"), id);
        Method setId = findMethod(propertiesClass, "setId", resourceKeyClass);
        if (setId != null) {
            setId.invoke(properties, key);
        }
        Object item = itemClass.getConstructor(propertiesClass).newInstance(properties);
        Object registry = builtInField("ITEM");
        registerMethod.invoke(null, registry, id, item);
        return item;
    }

    private void applyComponents(Object properties, Class<?> componentsClass, String kind, JsonObject payload)
            throws Throwable {
        Class<?> typeClass = Class.forName("net.minecraft.core.component.DataComponentType", true, loader);
        Method component = null;
        for (Method method : properties.getClass().getMethods()) {
            if (method.getName().equals("component") && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == typeClass) {
                component = method;
                break;
            }
        }
        if (component == null) {
            return;
        }
        component.setAccessible(true);

        // Sensible defaults per kind, then whatever the mod asked for on top.
        if ("tool".equals(kind) || "armor".equals(kind)) {
            set(component, properties, componentsClass, "MAX_STACK_SIZE", 1);
            set(component, properties, componentsClass, "MAX_DAMAGE",
                    (int) number(payload, "durability", "tool".equals(kind) ? 250 : 165));
        }
        if ("plant".equals(kind)) {
            set(component, properties, componentsClass, "MAX_STACK_SIZE", 64);
        }
        if (payload.has("components") && payload.get("components").isJsonObject()) {
            JsonObject custom = payload.getAsJsonObject("components");
            for (String name : custom.keySet()) {
                set(component, properties, componentsClass, name.toUpperCase(Locale.ROOT).replace('-', '_'),
                        scalar(custom.get(name)));
            }
        }
    }

    private void set(Method component, Object properties, Class<?> componentsClass, String field, Object value) {
        try {
            Field declared = componentsClass.getDeclaredField(field);
            declared.setAccessible(true);
            Object type = declared.get(null);
            if (type == null) {
                return;
            }
            component.invoke(properties, type, value);
        } catch (Throwable t) {
            PyTrialLog.warn("  component " + field + " not applied: " + t.getMessage());
        }
    }

    private static Object scalar(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive()) {
            return element.toString();
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            return value == Math.rint(value) ? (int) value : value;
        }
        return primitive.getAsString();
    }

    /**
     * An entity type backed by a vanilla implementation class until the mod ships its own. The factory
     * is a dynamic proxy, so no subclass has to be generated for the entity to exist in game.
     */
    private Object entity(String namespace, String path, JsonObject payload, String fallbackClass,
                          String category) throws Throwable {
        String className = payload.has("entityClass") ? payload.get("entityClass").getAsString() : fallbackClass;
        Class<?> entityClass = Class.forName(className, true, loader);
        Class<?> typeClass = Class.forName("net.minecraft.world.entity.EntityType", true, loader);
        Class<?> factoryClass = Class.forName("net.minecraft.world.entity.EntityType$EntityFactory", true, loader);
        Class<?> categoryClass = Class.forName("net.minecraft.world.entity.MobCategory", true, loader);
        Class<?> levelClass = Class.forName("net.minecraft.world.level.Level", true, loader);

        Constructor<?> entityConstructor = null;
        for (Constructor<?> constructor : entityClass.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2 && parameters[0] == typeClass && parameters[1] == levelClass) {
                constructor.setAccessible(true);
                entityConstructor = constructor;
                break;
            }
        }
        if (entityConstructor == null) {
            throw new IllegalStateException(className + " has no (EntityType, Level) constructor");
        }
        Constructor<?> finalConstructor = entityConstructor;

        Object factory = Proxy.newProxyInstance(loader, new Class<?>[]{factoryClass}, (proxy, method, args) -> {
            if ("invoke".equals(method.getName()) && args != null && args.length == 2) {
                return finalConstructor.newInstance(args[0], args[1]);
            }
            return switch (method.getName()) {
                case "toString" -> "pyTrialEntityFactory";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        });

        Object mobCategory = Enum.valueOf(categoryClass.asSubclass(Enum.class), category.toUpperCase(Locale.ROOT));
        Object builder = null;
        for (Method method : typeClass.getDeclaredMethods()) {
            if (method.getName().equals("of") && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 2) {
                method.setAccessible(true);
                builder = method.invoke(null, factory, mobCategory);
                break;
            }
        }
        if (builder == null) {
            throw new IllegalStateException("EntityType.of not found");
        }
        Object id = identifier(namespace, path);
        Object key = resourceKeyFactory.invoke(null, registryKey("ENTITY_TYPE"), id);
        Object type = null;
        for (Method method : builder.getClass().getMethods()) {
            if (!method.getName().equals("build") || method.getParameterCount() != 1) {
                continue;
            }
            try {
                method.setAccessible(true);
                type = method.invoke(builder, key);
                break;
            } catch (Throwable ignored) {
                // try the next overload
            }
        }
        if (type == null) {
            throw new IllegalStateException("EntityType.Builder.build not found");
        }
        registerMethod.invoke(null, builtInField("ENTITY_TYPE"), id, type);
        return type;
    }

    /**
     * A fluid pair. Vanilla has no FluidType to configure, so the bridge registers a source and a
     * flowing instance; {@code WaterFluid} supplies both with a public constructor, which gives a
     * working fluid under the mod namespace. A mod that ships its own classes passes
     * {@code sourceClass} / {@code flowingClass}.
     */
    private Object fluid(String namespace, String path, JsonObject payload) throws Throwable {
        String sourceName = payload.has("sourceClass") ? payload.get("sourceClass").getAsString()
                : "net.minecraft.world.level.material.WaterFluid$Source";
        String flowingName = payload.has("flowingClass") ? payload.get("flowingClass").getAsString()
                : "net.minecraft.world.level.material.WaterFluid$Flowing";

        Object source = instantiate(sourceName);
        Object flowing = instantiate(flowingName);
        put("FLUID", namespace, path, source);
        put("FLUID", namespace, path + "_flowing", flowing);
        return source;
    }

    private Object instantiate(String className) throws Throwable {
        Class<?> type = Class.forName(className, true, loader);
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * A client key binding. The {@code KeyMapping} constructor registers itself into the static
     * lookup the controls screen and the input loop both read, so creating it is the whole job.
     */
    private Object keyBinding(String namespace, String path, JsonObject payload) throws Throwable {
        Class<?> mappingClass = Class.forName("net.minecraft.client.KeyMapping", true, loader);
        Class<?> categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category", true, loader);

        String categoryId = payload.has("category") ? payload.get("category").getAsString() : namespace;
        Object category = null;
        for (Method method : categoryClass.getDeclaredMethods()) {
            if (method.getName().equals("register") && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class) {
                method.setAccessible(true);
                category = method.invoke(null, categoryId);
                break;
            }
        }
        if (category == null) {
            throw new IllegalStateException("KeyMapping.Category.register not found");
        }

        int keyCode = (int) number(payload, "key", 0);
        Constructor<?> constructor = mappingClass.getDeclaredConstructor(String.class, int.class,
                categoryClass);
        constructor.setAccessible(true);
        return constructor.newInstance("key." + namespace + "." + path, keyCode, category);
    }

    /** Escape hatch: any registry, any class, chosen by the mod. */
    private Object customElement(String namespace, String path, JsonObject payload) throws Throwable {
        String registry = payload.has("registry") ? payload.get("registry").getAsString() : null;
        String className = payload.has("class") ? payload.get("class").getAsString()
                : payload.has("class_name") ? payload.get("class_name").getAsString() : null;
        if (registry == null || className == null) {
            PyTrialLog.warn("custom_element " + namespace + ":" + path
                    + " needs both 'registry' and 'class', ignored");
            return null;
        }
        Class<?> type = Class.forName(className, true, loader);
        Object instance = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                constructor.setAccessible(true);
                instance = constructor.newInstance();
                break;
            }
        }
        if (instance == null) {
            throw new IllegalStateException(className + " has no no-arg constructor");
        }
        return put(registry.toUpperCase(Locale.ROOT).replace(':', '_').replace('/', '_'), namespace, path, instance);
    }

    // ------------------------------------------------------------------ plumbing

    private Object put(String registryField, String namespace, String path, Object value) throws Throwable {
        Object id = identifier(namespace, path);
        Object registry = builtInField(registryField);
        if (registry == null) {
            throw new IllegalStateException("BuiltInRegistries." + registryField + " not found");
        }
        registerMethod.invoke(null, registry, id, value);
        return value;
    }

    private Object identifier(String namespace, String path) throws Throwable {
        return identifierFactory.invoke(null, namespace, path);
    }

    private Object registryKey(String field) throws Throwable {
        Field declared = Class.forName(REGISTRIES, true, loader).getField(field);
        return declared.get(null);
    }

    private Object builtInField(String field) {
        try {
            return builtIn.getClass().getField(field).get(null);
        } catch (Throwable t) {
            try {
                return Class.forName(BUILT_IN_REGISTRIES, true, loader).getField(field).get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Object newProperties(Class<?> propertiesClass) throws Throwable {
        for (Method method : propertiesClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
                    && propertiesClass.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return method.invoke(null);
            }
        }
        Constructor<?> constructor = propertiesClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static double number(JsonObject json, String key, double fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsDouble();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        try {
            identifierClass = Class.forName(IDENTIFIER, true, loader);
            resourceKeyClass = Class.forName(RESOURCE_KEY, true, loader);
            registryClass = Class.forName(REGISTRY, true, loader);
            builtIn = Class.forName(BUILT_IN_REGISTRIES, true, loader);
            registries = Class.forName(REGISTRIES, true, loader);

            identifierFactory = findMethod(identifierClass, "fromNamespaceAndPath", String.class, String.class);
            resourceKeyFactory = findMethod(resourceKeyClass, "create", resourceKeyClass, identifierClass);
            registerMethod = findRegister(registryClass, identifierClass);
            if (identifierFactory == null || resourceKeyFactory == null || registerMethod == null) {
                PyTrialLog.warn("Registry bridge unavailable, extra element kinds are skipped");
            } else {
                PyTrialLog.info("Registry bridge ready");
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Registry bridge initialisation failed: " + t.getMessage());
        }
    }

    private static Method findRegister(Class<?> registryClass, Class<?> identifierClass) {
        for (Method method : registryClass.getDeclaredMethods()) {
            if (!method.getName().equals("register") || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3 && types[1] == identifierClass) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
}
