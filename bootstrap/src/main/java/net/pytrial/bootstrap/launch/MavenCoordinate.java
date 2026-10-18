package net.pytrial.bootstrap.launch;

public final class MavenCoordinate {

    private final String group;
    private final String artifact;
    private final String version;
    private final String classifier;
    private final String extension;

    private MavenCoordinate(String group, String artifact, String version, String classifier, String extension) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
        this.classifier = classifier;
        this.extension = extension;
    }

    public static MavenCoordinate parse(String notation) {
        if (notation == null || notation.isBlank()) {
            throw new IllegalArgumentException("Empty Maven coordinate");
        }

        String raw = notation.trim();
        String extension = "jar";
        int at = raw.indexOf('@');
        if (at >= 0) {
            extension = raw.substring(at + 1);
            raw = raw.substring(0, at);
        }

        String[] parts = raw.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Maven coordinate: " + notation);
        }

        String classifier = parts.length >= 4 ? parts[3] : null;
        return new MavenCoordinate(parts[0], parts[1], parts[2], classifier, extension);
    }

    public MavenCoordinate withClassifier(String newClassifier) {
        return new MavenCoordinate(group, artifact, version, newClassifier, extension);
    }

    public String group() {
        return group;
    }

    public String artifact() {
        return artifact;
    }

    public String version() {
        return version;
    }

    public String classifier() {
        return classifier;
    }

    public String extension() {
        return extension;
    }

    public String fileName() {
        StringBuilder sb = new StringBuilder(artifact).append('-').append(version);
        if (classifier != null && !classifier.isBlank()) {
            sb.append('-').append(classifier);
        }
        return sb.append('.').append(extension).toString();
    }

    public String directory() {
        return group.replace('.', '/') + '/' + artifact + '/' + version;
    }

    public String path() {
        return directory() + '/' + fileName();
    }

    @Override
    public String toString() {
        return group + ':' + artifact + ':' + version
                + (classifier == null ? "" : ':' + classifier)
                + (extension.equals("jar") ? "" : '@' + extension);
    }
}
