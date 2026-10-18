package net.pytrial.game;

import net.pytrial.ui.CreativeTabTextures;
import net.pytrial.ui.ModTranslations;
import net.pytrial.util.PyTrialLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class GameBridge {

    private static final String BLOCK = "net.minecraft.world.level.block.Block";
    private static final String BLOCK_PROPERTIES = "net.minecraft.world.level.block.state.BlockBehaviour$Properties";
    private static final String ITEM = "net.minecraft.world.item.Item";
    private static final String BLOCK_ITEM = "net.minecraft.world.item.BlockItem";
    private static final String ITEM_PROPERTIES = "net.minecraft.world.item.Item$Properties";
    private static final String ITEM_STACK = "net.minecraft.world.item.ItemStack";
    private static final String ITEM_LIKE = "net.minecraft.world.level.ItemLike";
    private static final String CREATIVE_TAB = "net.minecraft.world.item.CreativeModeTab";
    private static final String CREATIVE_TAB_BUILDER = "net.minecraft.world.item.CreativeModeTab$Builder";
    private static final String DISPLAY_GENERATOR = "net.minecraft.world.item.CreativeModeTab$DisplayItemsGenerator";
    private static final String COMPONENT = "net.minecraft.network.chat.Component";
    private static final String IDENTIFIER = "net.minecraft.resources.Identifier";
    private static final String REGISTRY = "net.minecraft.core.Registry";
    private static final String BUILT_IN_REGISTRIES = "net.minecraft.core.registries.BuiltInRegistries";
    private static final String REGISTRIES = "net.minecraft.core.registries.Registries";
    private static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey";

    private static final int FIRST_TAB_COLUMN = 7;
    private static final AtomicInteger TAB_POSITION = new AtomicInteger(FIRST_TAB_COLUMN);
    private static final String FALLBACK_ICON = "minecraft:stone";

    private final ClassLoader loader;

    private boolean prepared;
    private boolean usable;
    private boolean itemsUsable;
    private boolean tabsUsable;

    private Class<?> blockClass;
    private Class<?> blockPropertiesClass;
    private Class<?> itemClass;
    private Class<?> blockItemClass;
    private Class<?> itemPropertiesClass;
    private Class<?> itemStackClass;
    private Class<?> itemLikeClass;
    private Class<?> creativeTabClass;
    private Class<?> displayGeneratorClass;

    private Method identifierFactory;
    private Method propertiesFactory;
    private Method setIdMethod;
    private Method resourceKeyFactory;
    private Method registerMethod;
    private Method componentLiteral;
    private Method itemSetId;
    private Method tabBuilderFactory;
    private Method tabTitle;
    private Method tabIcon;
    private Method tabDisplayItems;
    private Method tabBuild;
    private Method outputAccept;

    private Constructor<?> itemPropertiesCtor;
    private Constructor<?> itemStackCtor;

    private Method blockStateDefinition;
    private Method possibleStates;
    private Method initCache;
    private Method stateIdOf;
    private Method stateAdd;
    private Object stateRegistry;
    private boolean stateSupportResolved;

    private Method nameComponentMethod;
    private Object nameComponentType;
    private boolean nameSupportResolved;

    private Class<?> rowClass;
    private Object rowTop;
    private Object blockRegistry;
    private Object blockRegistryKey;
    private Object itemRegistry;
    private Object itemRegistryKey;
    private Object tabRegistry;

    public GameBridge(ClassLoader loader) {
        this.loader = loader;
    }

    public boolean isUsable() {
        prepare();
        return usable;
    }

    public Object registerBlock(String namespace, String path, String display) {
        prepare();
        if (!usable) {
            return null;
        }
        ModTranslations.put(namespace, path, display);
        try {
            Object id = identifierFactory.invoke(null, namespace, path);
            Object key = resourceKeyFactory.invoke(null, blockRegistryKey, id);
            Object properties = propertiesFactory.invoke(null);
            setIdMethod.invoke(properties, key);
            Object block = blockClass.getConstructor(blockPropertiesClass).newInstance(properties);
            registerMethod.invoke(null, blockRegistry, id, block);
            initializeBlockStates(block, namespace + ":" + path);
            registerBlockItem(block, namespace, path, display);
            return block;
        } catch (Throwable t) {
            PyTrialLog.error("Failed to register block " + namespace + ":" + path + ": " + t, t);
            return null;
        }
    }

    public Object registerItem(String namespace, String path, String display) {
        prepare();
        if (!itemsUsable) {
            return null;
        }
        ModTranslations.put(namespace, path, display);
        try {
            Object id = identifierFactory.invoke(null, namespace, path);
            Object key = resourceKeyFactory.invoke(null, itemRegistryKey, id);
            Object properties = createItemProperties();
            applyDisplayName(properties, namespace, path, display);
            itemSetId.invoke(properties, key);
            Object item = itemClass.getConstructor(itemPropertiesClass).newInstance(properties);
            registerMethod.invoke(null, itemRegistry, id, item);
            return item;
        } catch (Throwable t) {
            PyTrialLog.error("Failed to register item " + namespace + ":" + path + ": " + t, t);
            return null;
        }
    }

    public Object registerCreativeTab(String namespace, String path, String title, String icon,
                                      List<String> itemIds, String row) {
        return buildCreativeTab(namespace, path, title, icon, itemIds, row);
    }

    private Object buildCreativeTab(String namespace, String path, String title, String icon,
                                    List<String> itemIds, String row) {
        prepare();
        if (!tabsUsable) {
            return null;
        }
        try {
            int column = TAB_POSITION.get();
            if (!CreativeTabTextures.fits(column)) {
                PyTrialLog.warn("Cannot register creative tab " + namespace + ":" + path
                        + ": the tab bar is full (" + CreativeTabTextures.CAPACITY + " columns)");
                return null;
            }
            Object id = identifierFactory.invoke(null, namespace, path);
            Object builder = tabBuilderFactory.invoke(null, resolveRow(row), column);
            TAB_POSITION.set(column + 1);
            String label = title == null || title.isBlank() ? namespace + ":" + path : title;
            tabTitle.invoke(builder, componentLiteral.invoke(null, label));

            Object iconItem = resolveItem(icon);
            if (iconItem == null && itemIds != null && !itemIds.isEmpty()) {
                iconItem = resolveItem(itemIds.get(0));
            }
            if (iconItem == null) {
                iconItem = resolveItem(FALLBACK_ICON);
            }
            final Object tabIconItem = iconItem;
            Supplier<Object> iconSupplier = () -> {
                try {
                    return tabIconItem == null || itemStackCtor == null
                            ? null
                            : itemStackCtor.newInstance(tabIconItem);
                } catch (Throwable t) {
                    throw new IllegalStateException(t);
                }
            };
            tabIcon.invoke(builder, iconSupplier);
            tabDisplayItems.invoke(builder, displayGenerator(itemIds));

            Object tab = tabBuild.invoke(builder);
            registerMethod.invoke(null, tabRegistry, id, tab);
            return tab;
        } catch (Throwable t) {
            PyTrialLog.error("Failed to register creative tab " + namespace + ":" + path + ": " + t, t);
            return null;
        }
    }

    /**
     * Runs the per-state initialisation vanilla performs while building {@code Blocks}.
     *
     * <p>A block created later misses two steps: {@code BlockStateBase.initCache()} (fluid state,
     * random tick flag and the shape/occlusion cache, which otherwise throws when the block is
     * rendered) and the entry in {@code Block.BLOCK_STATE_REGISTRY} (without an id the server cannot
     * encode a block update and the player gets disconnected).
     */
    private void initializeBlockStates(Object block, String id) {
        try {
            resolveStateSupport();
            if (blockStateDefinition == null || possibleStates == null) {
                return;
            }
            Object definition = blockStateDefinition.invoke(block);
            Object states = possibleStates.invoke(definition);
            if (!(states instanceof Iterable<?> iterable)) {
                return;
            }
            int count = 0;
            int added = 0;
            for (Object state : iterable) {
                if (initCache != null) {
                    initCache.invoke(state);
                }
                if (stateRegistry != null && stateIdOf != null && stateAdd != null) {
                    Object current = stateIdOf.invoke(stateRegistry, state);
                    if (current instanceof Integer value && value == -1) {
                        stateAdd.invoke(stateRegistry, state);
                        added++;
                    }
                }
                count++;
            }
            PyTrialLog.info("Initialized " + count + " block state(s) of " + id
                    + (added > 0 ? ", assigned " + added + " state id(s)" : ""));
        } catch (Throwable t) {
            PyTrialLog.warn("Failed to initialize the block states of " + id + ": " + t.getMessage());
        }
    }

    private void applyDisplayName(Object properties, String namespace, String path, String display) {
        resolveNameSupport();
        if (nameComponentMethod == null || nameComponentType == null || componentLiteral == null) {
            return;
        }
        try {
            String name = display == null || display.isBlank() ? ModTranslations.prettify(path) : display;
            Object component = componentLiteral.invoke(null, name);
            nameComponentMethod.invoke(properties, nameComponentType, component);
            PyTrialLog.debug("Display name of " + namespace + ":" + path + " is " + name);
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot set the display name of " + namespace + ":" + path + ": " + t.getMessage());
        }
    }

    private void resolveNameSupport() {
        if (nameSupportResolved) {
            return;
        }
        nameSupportResolved = true;
        try {
            Class<?> componentsClass = Class.forName(
                    "net.minecraft.core.component.DataComponents", true, loader);
            Class<?> componentTypeClass = Class.forName(
                    "net.minecraft.core.component.DataComponentType", true, loader);
            nameComponentType = componentsClass.getField("ITEM_NAME").get(null);
            nameComponentMethod = itemPropertiesClass.getMethod("component", componentTypeClass, Object.class);
        } catch (Throwable t) {
            PyTrialLog.warn("Item display names are unavailable: " + t.getMessage());
        }
    }

    private void resolveStateSupport() {
        if (stateSupportResolved) {
            return;
        }
        stateSupportResolved = true;
        try {
            blockStateDefinition = blockClass.getMethod("getStateDefinition");
            Class<?> definitionClass = Class.forName(
                    "net.minecraft.world.level.block.state.StateDefinition", true, loader);
            possibleStates = definitionClass.getMethod("getPossibleStates");
            Class<?> stateBaseClass = Class.forName(
                    "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase", true, loader);
            initCache = stateBaseClass.getMethod("initCache");
            Object registry = blockClass.getField("BLOCK_STATE_REGISTRY").get(null);
            stateRegistry = registry;
            stateIdOf = registry.getClass().getMethod("getId", Object.class);
            try {
                stateAdd = registry.getClass().getMethod("add", Object.class);
            } catch (NoSuchMethodException missing) {
                PyTrialLog.debug("The block state registry has no public add method");
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Block state initialisation is unavailable: " + t.getMessage());
        }
    }

    private void registerBlockItem(Object block, String namespace, String path, String display) {
        if (!itemsUsable) {
            return;
        }
        try {
            Object id = identifierFactory.invoke(null, namespace, path);
            Object key = resourceKeyFactory.invoke(null, itemRegistryKey, id);
            Object properties = createItemProperties();
            applyDisplayName(properties, namespace, path, display);
            itemSetId.invoke(properties, key);
            Object item = blockItemClass.getConstructor(blockClass, itemPropertiesClass)
                    .newInstance(block, properties);
            registerMethod.invoke(null, itemRegistry, id, item);
        } catch (Throwable t) {
            PyTrialLog.warn("Failed to register the item form of " + namespace + ":" + path + ": "
                    + t.getMessage());
        }
    }

    private Object resolveRow(String row) {
        if (row == null || row.isBlank() || rowClass == null) {
            return rowTop;
        }
        try {
            Field field = rowClass.getField(row.trim().toUpperCase());
            return field.get(null);
        } catch (Throwable t) {
            PyTrialLog.warn("Unknown creative tab row '" + row + "', falling back to TOP");
            return rowTop;
        }
    }

    private Object displayGenerator(List<String> itemIds) {
        List<String> ids = itemIds == null ? List.of() : List.copyOf(itemIds);
        return Proxy.newProxyInstance(loader, new Class<?>[]{displayGeneratorClass}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "accept" -> acceptItems(args, ids);
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == (args != null && args.length > 0 ? args[0] : null);
                }
                case "toString" -> {
                    return "pyTrialDisplayItems";
                }
                default -> {
                }
            }
            return null;
        });
    }

    private void acceptItems(Object[] args, List<String> ids) {
        if (args == null || args.length < 2 || args[1] == null) {
            return;
        }
        Object output = args[1];
        for (String id : ids) {
            Object item = resolveItem(id);
            if (item == null) {
                continue;
            }
            try {
                outputAccept.invoke(output, item);
            } catch (Throwable t) {
                PyTrialLog.warn("Could not add " + id + " to a creative tab: " + t.getMessage());
            }
        }
    }

    private Object resolveItem(String id) {
        if (id == null || id.isBlank() || !itemsUsable) {
            return null;
        }
        try {
            int separator = id.indexOf(':');
            String namespace = separator > 0 ? id.substring(0, separator) : "minecraft";
            String path = separator > 0 ? id.substring(separator + 1) : id;
            Object identifier = identifierFactory.invoke(null, namespace, path);
            return itemRegistry.getClass().getMethod("getValue", Class.forName(IDENTIFIER, false, loader))
                    .invoke(itemRegistry, identifier);
        } catch (Throwable t) {
            return null;
        }
    }

    private Object createItemProperties() throws Throwable {
        if (itemPropertiesCtor != null) {
            return itemPropertiesCtor.newInstance();
        }
        for (Method method : itemPropertiesClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
                    && itemPropertiesClass.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return method.invoke(null);
            }
        }
        throw new IllegalStateException("no factory for " + ITEM_PROPERTIES);
    }

    private synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        try {
            blockClass = Class.forName(BLOCK, true, loader);
            blockPropertiesClass = Class.forName(BLOCK_PROPERTIES, true, loader);
            Class<?> identifierClass = Class.forName(IDENTIFIER, true, loader);
            Class<?> registryClass = Class.forName(REGISTRY, true, loader);
            Class<?> resourceKeyClass = Class.forName(RESOURCE_KEY, true, loader);
            Class<?> registriesClass = Class.forName(REGISTRIES, true, loader);
            Class<?> builtInClass = Class.forName(BUILT_IN_REGISTRIES, true, loader);

            identifierFactory = findMethod(identifierClass, "fromNamespaceAndPath", String.class, String.class);
            propertiesFactory = findNoArgFactory(blockPropertiesClass);
            setIdMethod = findMethod(blockPropertiesClass, "setId", resourceKeyClass);
            resourceKeyFactory = findMethod(resourceKeyClass, "create", resourceKeyClass, identifierClass);
            registerMethod = findRegister(registryClass, identifierClass);
            blockRegistry = builtInClass.getField("BLOCK").get(null);
            blockRegistryKey = registriesClass.getField("BLOCK").get(null);

            usable = identifierFactory != null && propertiesFactory != null && setIdMethod != null
                    && resourceKeyFactory != null && registerMethod != null
                    && blockRegistry != null && blockRegistryKey != null;
            if (usable) {
                PyTrialLog.info("Block registration bridge ready");
            } else {
                PyTrialLog.warn("Block registration bridge unavailable, game content registration skipped");
            }

            prepareItems(identifierClass, builtInClass, registriesClass);
            prepareTabs(identifierClass, builtInClass, resourceKeyClass);
        } catch (Throwable t) {
            usable = false;
            PyTrialLog.warn("Block registration bridge initialisation failed: " + t.getMessage());
        }
    }

    private void prepareItems(Class<?> identifierClass, Class<?> builtInClass, Class<?> registriesClass) {
        try {
            itemClass = Class.forName(ITEM, true, loader);
            blockItemClass = Class.forName(BLOCK_ITEM, true, loader);
            itemPropertiesClass = Class.forName(ITEM_PROPERTIES, true, loader);
            itemStackClass = Class.forName(ITEM_STACK, true, loader);
            itemLikeClass = Class.forName(ITEM_LIKE, true, loader);

            Class<?> resourceKeyClass = Class.forName(RESOURCE_KEY, true, loader);
            itemSetId = findMethod(itemPropertiesClass, "setId", resourceKeyClass);
            itemPropertiesCtor = findConstructor(itemPropertiesClass);
            itemStackCtor = findConstructor(itemStackClass, itemLikeClass);
            itemRegistry = builtInClass.getField("ITEM").get(null);
            itemRegistryKey = registriesClass.getField("ITEM").get(null);

            itemsUsable = itemSetId != null && itemRegistry != null && itemRegistryKey != null;
            if (itemsUsable) {
                PyTrialLog.info("Item registration bridge ready");
            } else {
                PyTrialLog.warn("Item registration bridge unavailable, items will not be registered");
            }
        } catch (Throwable t) {
            itemsUsable = false;
            PyTrialLog.warn("Item registration bridge initialisation failed: " + t.getMessage());
        }
    }

    private void prepareTabs(Class<?> identifierClass, Class<?> builtInClass, Class<?> resourceKeyClass) {
        try {
            creativeTabClass = Class.forName(CREATIVE_TAB, true, loader);
            Class<?> builderClass = Class.forName(CREATIVE_TAB_BUILDER, true, loader);
            displayGeneratorClass = Class.forName(DISPLAY_GENERATOR, true, loader);
            Class<?> componentClass = Class.forName(COMPONENT, true, loader);
            rowClass = Class.forName(CREATIVE_TAB + "$Row", true, loader);
            Class<?> outputClass = Class.forName(CREATIVE_TAB + "$Output", true, loader);

            tabBuilderFactory = creativeTabClass.getMethod("builder", rowClass, int.class);
            tabTitle = builderClass.getMethod("title", componentClass);
            tabIcon = builderClass.getMethod("icon", Supplier.class);
            tabDisplayItems = builderClass.getMethod("displayItems", displayGeneratorClass);
            tabBuild = builderClass.getMethod("build");
            componentLiteral = componentClass.getMethod("literal", String.class);
            outputAccept = outputClass.getMethod("accept", itemLikeClass);
            rowTop = this.rowClass.getField("TOP").get(null);
            tabRegistry = builtInClass.getField("CREATIVE_MODE_TAB").get(null);

            tabsUsable = tabBuilderFactory != null && tabTitle != null && tabIcon != null
                    && tabDisplayItems != null && tabBuild != null && tabRegistry != null && rowTop != null;
            if (tabsUsable) {
                PyTrialLog.info("Creative tab bridge ready");
            } else {
                PyTrialLog.warn("Creative tab bridge unavailable, tabs will not be registered");
            }
        } catch (Throwable t) {
            tabsUsable = false;
            PyTrialLog.warn("Creative tab bridge initialisation failed: " + t.getMessage());
        }
    }

    private static Constructor<?> findConstructor(Class<?> owner, Class<?>... parameters) {
        try {
            Constructor<?> constructor = owner.getDeclaredConstructor(parameters);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            return null;
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

    private static Method findNoArgFactory(Class<?> owner) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }
            if (owner.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
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

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
