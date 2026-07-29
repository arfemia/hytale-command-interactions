package com.ziggfreed.interactioncommands.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void substitutesASingleToken() {
        assertEquals("hi Steve", Placeholders.substitute("hi {player}", Map.of("{player}", "Steve")).text());
    }

    @Test
    void substitutesEachTokenBuiltByBuildValues() {
        Map<String, String> values = Placeholders.buildValues(
                "Steve", PLAYER_UUID, 12.9, 64.0, -3.1, "overworld",
                "Alex", TARGET_UUID, "Tool_Pickaxe_Iron");

        assertEquals("Steve", Placeholders.substitute("{player}", values).text());
        assertEquals(PLAYER_UUID.toString(), Placeholders.substitute("{uuid}", values).text());
        assertEquals("overworld", Placeholders.substitute("{world}", values).text());
        assertEquals("Alex", Placeholders.substitute("{target}", values).text());
        assertEquals(TARGET_UUID.toString(), Placeholders.substitute("{targetUuid}", values).text());
        assertEquals("Tool_Pickaxe_Iron", Placeholders.substitute("{item}", values).text());
    }

    @Test
    void substitutesMixedTokensInOneCommand() {
        Map<String, String> values = Placeholders.buildValues(
                "Steve", PLAYER_UUID, 12.9, 64.0, -3.1, "overworld",
                "Alex", TARGET_UUID, "Tool_Pickaxe_Iron");

        Placeholders.Result result = Placeholders.substitute(
                "tp {player} {x} {y} {z} in {world}, target={target}, item={item}", values);

        // Math.floor(-3.1) == -4.0 (floor rounds toward negative infinity), not -3.
        assertEquals("tp Steve 12 64 -4 in overworld, target=Alex, item=Tool_Pickaxe_Iron", result.text());
        assertFalse(result.hasUnresolvedToken());
    }

    @Test
    void injectedValueContainingTokenShapedTextIsNotRescannedOrMisflagged() {
        // A world/item/player name that happens to look like "{x}"-shaped text must be
        // injected verbatim and must never itself trip a false unresolved-token flag,
        // and the still-genuinely-unresolved {target} token must still be caught.
        Map<String, String> values = Placeholders.buildValues(
                "Steve", PLAYER_UUID, 0.0, 0.0, 0.0, "world_{y}_weird", null, null, "{uuid}_item");

        Placeholders.Result result = Placeholders.substitute("tp {player} in {world} holding {item}, hi {target}", values);

        assertEquals("tp Steve in world_{y}_weird holding {uuid}_item, hi {target}", result.text());
        assertTrue(result.hasUnresolvedToken());
    }

    @Test
    void coordinatesFloorTowardNegativeInfinityAsLocaleIndependentDigits() {
        Map<String, String> values = Placeholders.buildValues(
                "Steve", PLAYER_UUID, -0.5, 100.999, 7.0, "overworld", null, null, null);

        // Math.floor(-0.5) == -1.0 (floor rounds toward negative infinity, not toward
        // zero); the digits themselves come from Integer#toString, which is inherently
        // locale-independent (no grouping/decimal separators to vary by Locale).
        assertEquals("-1", values.get("{x}"));
        assertEquals("100", values.get("{y}"));
        assertEquals("7", values.get("{z}"));
    }

    @Test
    void noPositionLeavesCoordinateTokensUnresolved() {
        // No TransformComponent on the firing entity means the caller passes null for
        // all three of x/y/z (never a 0.0/0.0/0.0 fallback); {x}/{y}/{z} must be left
        // unresolved rather than silently substituted with the world origin.
        Map<String, String> values = Placeholders.buildValues(
                "Steve", PLAYER_UUID, null, null, null, "overworld", null, null, null);

        Placeholders.Result result = Placeholders.substitute("tp {player} {x} {y} {z}", values);

        assertEquals("tp Steve {x} {y} {z}", result.text());
        assertTrue(result.hasUnresolvedToken());
    }

    @Test
    void noTargetLeavesTargetTokensUnresolved() {
        Map<String, String> values = Placeholders.buildValues(
                "Steve", PLAYER_UUID, 0.0, 0.0, 0.0, "overworld", null, null, null);

        Placeholders.Result result = Placeholders.substitute("say hi {target}, id {targetUuid}", values);

        assertEquals("say hi {target}, id {targetUuid}", result.text());
        assertTrue(result.hasUnresolvedToken());
    }

    @Test
    void fullyResolvedCommandHasNoUnresolvedToken() {
        Map<String, String> values = Placeholders.buildValues(
                "Steve", PLAYER_UUID, 0.0, 0.0, 0.0, "overworld", "Alex", TARGET_UUID, "Wood");

        Placeholders.Result result = Placeholders.substitute("give {target} {item}", values);

        assertFalse(result.hasUnresolvedToken());
    }

    @Test
    void unresolvedTokenScanCoversEveryKnownToken() {
        // Exercises Result#hasUnresolvedToken() through substitute() directly (the real
        // production path) for every known token, rather than the removed
        // Placeholders.hasUnresolvedToken(String) dead-code helper. Driven off
        // Placeholders.knownTokens() (the real production set) rather than a hardcoded
        // copy here, so a token added to KNOWN_TOKENS later is automatically covered.
        // An empty values map guarantees every known token is absent, so each must be
        // flagged unresolved.
        for (String token : Placeholders.knownTokens()) {
            Placeholders.Result result = Placeholders.substitute("cmd " + token, Map.of());
            assertTrue(result.hasUnresolvedToken(), "expected token to be caught: " + token);
        }

        Placeholders.Result noTokenResult = Placeholders.substitute("cmd with no placeholder tokens", Map.of());
        assertFalse(noTokenResult.hasUnresolvedToken());
    }

    @Test
    void noHeldItemLeavesItemTokenUnresolved() {
        Map<String, String> values = Placeholders.buildValues(
                "Steve", PLAYER_UUID, 0.0, 0.0, 0.0, "overworld", null, null, null);

        Placeholders.Result result = Placeholders.substitute("give {item}", values);

        assertTrue(result.hasUnresolvedToken());
    }
}
