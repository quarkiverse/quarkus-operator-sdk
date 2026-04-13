package io.halkyon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;

class HelmTarContentIT {

    @Test
    void helmTarContainsCRD() throws IOException {
        Path tarFile = Files.walk(Path.of("target/helm/kubernetes"))
                .filter(p -> p.getFileName().toString().endsWith(".tar.gz"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Helm tar file found"));

        boolean hasCRD = false;
        try (var tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(new FileInputStream(tarFile.toFile())))) {
            var entry = tar.getNextEntry();
            while (entry != null) {
                if (entry.getName().endsWith("crds/exposedapps.halkyon.io-v1.yml")) {
                    hasCRD = true;
                    break;
                }
                entry = tar.getNextEntry();
            }
        }

        assertTrue(hasCRD, "Helm tar should contain crds/exposedapps.halkyon.io-v1.yml");
    }
}
