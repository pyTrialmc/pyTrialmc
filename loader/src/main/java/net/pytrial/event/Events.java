package net.pytrial.event;

import java.nio.file.Path;
import java.util.List;

public final class Events {

    private Events() {
    }

    public static final class LoaderInitialized implements Event {
        private final Path gameDir;
        private final Path modsDir;
        private final String minecraftVersion;

        public LoaderInitialized(Path gameDir, Path modsDir, String minecraftVersion) {
            this.gameDir = gameDir;
            this.modsDir = modsDir;
            this.minecraftVersion = minecraftVersion;
        }

        public Path gameDir() {
            return gameDir;
        }

        public Path modsDir() {
            return modsDir;
        }

        public String minecraftVersion() {
            return minecraftVersion;
        }
    }

    public static final class ModDiscovered implements Event.Cancellable {
        private final String modId;
        private final Path source;
        private boolean cancelled;

        public ModDiscovered(String modId, Path source) {
            this.modId = modId;
            this.source = source;
        }

        public String modId() {
            return modId;
        }

        public Path source() {
            return source;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    public static final class ModsResolved implements Event {
        private final List<String> modIds;

        public ModsResolved(List<String> modIds) {
            this.modIds = List.copyOf(modIds);
        }

        public List<String> modIds() {
            return modIds;
        }
    }

    public static final class ModLoading implements Event {
        private final String modId;

        public ModLoading(String modId) {
            this.modId = modId;
        }

        public String modId() {
            return modId;
        }
    }

    public static final class ModLoaded implements Event {
        private final String modId;

        public ModLoaded(String modId) {
            this.modId = modId;
        }

        public String modId() {
            return modId;
        }
    }

    public static final class GameStarting implements Event {
    }

    public static final class LoaderShutdown implements Event {
    }
}
