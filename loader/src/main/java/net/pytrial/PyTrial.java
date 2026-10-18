package net.pytrial;

public final class PyTrial {

    public static final String NAME = "pyTrial";

    public static final String VERSION = "0.1.0-SNAPSHOT";

    public static final String MOD_METADATA_FILE = "pytrial.mod.json";

    public static final String INSTALL_DIR = "pytrial";

    public static final String LOG_PREFIX = "[pyTrial] ";

    private PyTrial() {
    }

    public static String version() {
        String value = PyTrial.class.getPackage() != null
                ? PyTrial.class.getPackage().getImplementationVersion()
                : null;
        return value != null ? value : VERSION;
    }
}
