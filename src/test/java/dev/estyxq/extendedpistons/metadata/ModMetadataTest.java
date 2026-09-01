package dev.estyxq.extendedpistons.metadata;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModMetadataTest {
    @Test
    void publishedRangesStayIndependentFromDevelopmentDependencies() throws IOException {
        String metadata = loadMetadata();

        assertTrue(metadata.contains("versionRange=\"[21.1.1,21.2)\""),
                "NeoForge support must cover the verified published 21.1.x line only");
        assertTrue(metadata.contains("versionRange=\"[1.21.1]\""),
                "The 1.21.1 JAR must not claim compatibility with other Minecraft patches");
    }

    private static String loadMetadata() throws IOException {
        String path = "/META-INF/neoforge.mods.toml";
        InputStream stream = ModMetadataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing generated mod metadata " + path);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
