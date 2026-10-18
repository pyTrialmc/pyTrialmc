package net.pytrial.game;

import net.pytrial.util.PyTrialLog;

import java.util.ArrayList;
import java.util.List;

public final class ContentRegistry {

    public record BlockEntry(String namespace, String path, Object block, String texture) {

        public String id() {
            return namespace + ":" + path;
        }
    }

    public record ItemEntry(String namespace, String path, Object item) {

        public String id() {
            return namespace + ":" + path;
        }
    }

    public record TabEntry(String namespace, String path, String title, String icon, List<String> items,
                           String row, Object tab) {

        public String id() {
            return namespace + ":" + path;
        }
    }

    private record PendingTab(String namespace, String path, String title, String icon, List<String> items,
                              String row) {
    }

    private static final List<String[]> PENDING_BLOCKS = new ArrayList<>();
    private static final List<String[]> PENDING_ITEMS = new ArrayList<>();
    private static final List<PendingTab> PENDING_TABS = new ArrayList<>();
    private static final List<BlockEntry> REGISTERED_BLOCKS = new ArrayList<>();
    private static final List<ItemEntry> REGISTERED_ITEMS = new ArrayList<>();
    private static final List<TabEntry> REGISTERED_TABS = new ArrayList<>();

    private static boolean applied;

    private ContentRegistry() {
    }

    public static synchronized boolean isApplied() {
        return applied;
    }

    public static synchronized void addBlock(String namespace, String path, String display, String texture) {
        PENDING_BLOCKS.add(new String[]{namespace, path, display, texture});
    }

    public static synchronized void addItem(String namespace, String path, String display) {
        PENDING_ITEMS.add(new String[]{namespace, path, display});
    }

    public static synchronized void addCreativeTab(String namespace, String path, String title,
                                                   String icon, List<String> items, String row) {
        PENDING_TABS.add(new PendingTab(namespace, path, title, icon, List.copyOf(items), row));
    }

    public static synchronized int pendingCount() {
        return PENDING_BLOCKS.size() + PENDING_ITEMS.size() + PENDING_TABS.size();
    }

    public static synchronized List<BlockEntry> apply(GameBridge bridge) {
        for (String[] entry : PENDING_BLOCKS) {
            Object block = bridge.registerBlock(entry[0], entry[1], entry[2]);
            if (block != null) {
                BlockEntry registered = new BlockEntry(entry[0], entry[1], block, entry[3]);
                REGISTERED_BLOCKS.add(registered);
                PyTrialLog.info("Registered block " + registered.id());
            }
        }
        PENDING_BLOCKS.clear();

        for (String[] entry : PENDING_ITEMS) {
            Object item = bridge.registerItem(entry[0], entry[1], entry[2]);
            if (item != null) {
                ItemEntry registered = new ItemEntry(entry[0], entry[1], item);
                REGISTERED_ITEMS.add(registered);
                PyTrialLog.info("Registered item " + registered.id());
            }
        }
        PENDING_ITEMS.clear();

        for (PendingTab pending : PENDING_TABS) {
            Object tab = bridge.registerCreativeTab(pending.namespace(), pending.path(), pending.title(),
                    pending.icon(), pending.items(), pending.row());
            if (tab != null) {
                TabEntry registered = new TabEntry(pending.namespace(), pending.path(), pending.title(),
                        pending.icon(), pending.items(), pending.row(), tab);
                REGISTERED_TABS.add(registered);
                PyTrialLog.info("Registered creative tab " + registered.id());
            }
        }
        PENDING_TABS.clear();
        applied = true;

        return List.copyOf(REGISTERED_BLOCKS);
    }

    public static synchronized List<BlockEntry> blocks() {
        return List.copyOf(REGISTERED_BLOCKS);
    }

    public static synchronized List<ItemEntry> items() {
        return List.copyOf(REGISTERED_ITEMS);
    }

    public static synchronized List<TabEntry> tabs() {
        return List.copyOf(REGISTERED_TABS);
    }

    public static synchronized void clear() {
        applied = false;
        PENDING_BLOCKS.clear();
        PENDING_ITEMS.clear();
        PENDING_TABS.clear();
        REGISTERED_BLOCKS.clear();
        REGISTERED_ITEMS.clear();
        REGISTERED_TABS.clear();
    }
}
