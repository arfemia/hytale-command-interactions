package com.ziggfreed.interactioncommands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The example asset pack ({@code examples/InteractionCommandsExamples}) hard-depends on this jar and
 * ships beside it, so it targets the server line the jar targets. A server checks a pack's
 * {@code ServerVersion} the way it checks a plugin's and lists a pack that misses as outdated (a
 * WARNING, a SEVERE roll-up and a red notice to players allowed to see outdated mods), so a port that
 * widens one manifest and forgets the other fails here.
 *
 * <p>Engine-free like every test in this module: the jar's manifest is the processed one on the test
 * classpath, the pack's is read from its source folder, and the field is read as text.
 */
class ExamplePackManifestTest {

    private static final Path EXAMPLE_MANIFEST =
            Path.of("examples", "InteractionCommandsExamples", "manifest.json");
    private static final Pattern SERVER_VERSION = Pattern.compile("\"ServerVersion\"\\s*:\\s*\"([^\"]*)\"");

    @Test
    void theExamplePackTargetsTheServerLineItsJarTargets() throws IOException {
        String jarRange = serverVersion(jarManifest(), "the jar's processed manifest.json");
        String packRange = serverVersion(Files.readString(EXAMPLE_MANIFEST, StandardCharsets.UTF_8),
                EXAMPLE_MANIFEST.toString());

        assertEquals(jarRange, packRange, "the example pack must target the server line its jar targets");
    }

    private static String jarManifest() throws IOException {
        try (InputStream in = ExamplePackManifestTest.class.getClassLoader().getResourceAsStream("manifest.json")) {
            assertNotNull(in, "the jar's processed manifest.json is on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String serverVersion(String manifest, String where) {
        Matcher matcher = SERVER_VERSION.matcher(manifest);
        assertTrue(matcher.find(), where + " names a ServerVersion");
        return matcher.group(1);
    }
}
