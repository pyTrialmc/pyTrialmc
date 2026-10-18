package net.pytrial.bootstrap.install;

import net.pytrial.bootstrap.launch.VersionJson;
import net.pytrial.bootstrap.launch.VersionResolver;
import net.pytrial.bootstrap.util.BootstrapLog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class MappingDownloader {

    private MappingDownloader() {
    }

    public static void ensure(Path gameDir, Path installDir, String versionId) {
        try {
            String resolved = versionId;
            if (resolved == null || resolved.isBlank()) {
                resolved = VersionResolver.findLatestRelease(gameDir);
            }
            if (resolved == null) {
                BootstrapLog.warn("Cannot determine the game version, skipping mappings download");
                return;
            }

            Path target = installDir.resolve("mappings").resolve(resolved + "-client.txt");
            if (Files.isRegularFile(target)) {
                BootstrapLog.info("Mappings already present, skipping download: " + target);
                return;
            }

            Path versionJson = VersionResolver.resolveVersionJson(gameDir, resolved);
            VersionJson version = VersionResolver.read(versionJson);
            if (version.downloads == null || version.downloads.client_mappings == null
                    || version.downloads.client_mappings.url == null) {
                BootstrapLog.info(resolved + " ships no official mappings (the client is no longer obfuscated since 26.x), nothing to download");
                return;
            }

            Files.createDirectories(target.getParent());
            String url = version.downloads.client_mappings.url;
            BootstrapLog.info("Downloading official mappings: " + url);
            fetch(URI.create(url), target);
            BootstrapLog.info("Mappings saved: " + target + " (" + (Files.size(target) / 1024) + " KB)");
        } catch (Throwable t) {
            BootstrapLog.warn("Mappings download failed: " + t.getMessage());
        }
    }

    private static void fetch(URI uri, Path target) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(target);
            throw new IOException("HTTP " + response.statusCode());
        }
    }
}
