package net.pytrial.bootstrap.launch;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VersionJson {

    public String id;
    public String type = "release";
    public String mainClass;
    public String releaseTime;
    public String time;
    public String assets;

    public String minecraftArguments;

    public AssetIndex assetIndex;
    public JavaVersion javaVersion;
    public Downloads downloads;
    public List<Library> libraries = new ArrayList<>();
    public Arguments arguments;

    public static final class AssetIndex {
        public String id;
        public String url;
        public String sha1;
        public long size;
        @SerializedName("totalSize")
        public long totalSize;
    }

    public static final class JavaVersion {
        public String component = "java-runtime-gamma";
        public int majorVersion = 21;
    }

    public static final class Downloads {
        public Artifact client;
        public Artifact server;
        public Artifact artifact;
        public Artifact client_mappings;
        public Map<String, Artifact> classifiers = new LinkedHashMap<>();
    }

    public static final class Artifact {
        public String path;
        public String url;
        public String sha1;
        public long size;
    }

    public static final class Library {
        public String name;
        public String url;
        public Downloads downloads;
        public Map<String, String> natives;
        public Extract extract;
        public List<Rule> rules = new ArrayList<>();
    }

    public static final class Extract {
        public List<String> exclude = new ArrayList<>();
    }

    public static final class Rule {
        public String action = "allow";
        public Os os;
        public Map<String, Boolean> features;
    }

    public static final class Os {
        public String name;
        public String version;
        public String arch;
    }

    public static final class Arguments {
        public List<Object> jvm = new ArrayList<>();
        public List<Object> game = new ArrayList<>();
    }
}
