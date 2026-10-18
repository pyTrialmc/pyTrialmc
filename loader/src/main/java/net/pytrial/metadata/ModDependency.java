package net.pytrial.metadata;

public record ModDependency(String id, String version, boolean optional) {
    public ModDependency(String id) {
        this(id, "*", false);
    }

    public boolean satisfiedBy(String actualVersion) {
        return VersionConstraint.matches(actualVersion, version);
    }

    @Override
    public String toString() {
        return id + (version == null || version.isBlank() || "*".equals(version) ? "" : " " + version)
                + (optional ? " (optional)" : "");
    }
}
