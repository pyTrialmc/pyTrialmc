package net.pytrial.bootstrap.launch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LaunchOptions {

    public String versionId;

    public Path gameDir;

    public Path modsDir;

    public Path loaderJar;

    public boolean dryRun;

    public boolean installScripts;

    public String playerName = "Player";

    public String uuid = "00000000000000000000000000000000";

    public String accessToken = "0";

    public final List<String> extraGameArgs = new ArrayList<>();

    public Path effectiveModsDir() {
        return modsDir != null ? modsDir : gameDir.resolve("pytrial").resolve("mods");
    }
}
