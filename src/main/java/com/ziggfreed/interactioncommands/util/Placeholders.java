package com.ziggfreed.interactioncommands.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pure placeholder substitution for {@code RunCommand} command strings. Carries zero
 * Hytale imports so it exercises fully in a plain unit-test JVM; {@code
 * RunCommandInteraction} pulls the raw values (player name/uuid, position, world,
 * target, held item) off the live Hytale context and hands them here as plain
 * primitives/Strings via {@link #buildValues}.
 *
 * <p><b>Unresolved-placeholder rule (SPEC):</b> after substitution, if a command string
 * still contains ANY known token this class understands, the whole command is skipped
 * (a fine-level log, the rest of the list still runs) rather than dispatched with a
 * literal {@code {target}} in it. {@link #KNOWN_TOKENS} lists every token {@link
 * #buildValues} can ever populate, so a resolvable-but-absent-this-fire token (e.g.
 * {@code {target}} with no target entity) is simply omitted from the substitution map
 * and still gets caught: {@link #substitute} tracks the unresolved flag DURING its
 * single pass over the raw command (never by re-scanning the substituted output), so a
 * world name, item id, or player name that happens to contain literal {@code {x}}-shaped
 * text is substituted in and can never itself trip a false unresolved-token skip.
 */
public final class Placeholders {

    private static final String[] KNOWN_TOKENS = {
            "{player}", "{uuid}", "{x}", "{y}", "{z}", "{world}", "{target}", "{targetUuid}", "{item}"
    };

    /**
     * Read-only view of every token {@link #buildValues} can ever populate, exposed
     * package-private so {@code PlaceholdersTest} can drive its full-coverage assertion
     * off the real production set instead of a hardcoded copy that silently stops
     * covering a token added here later.
     */
    @Nonnull
    static List<String> knownTokens() {
        return List.of(KNOWN_TOKENS);
    }

    /** Matches any {@code {word}} token, known or not; drives the single-pass substitution. */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{(\\w+)\\}");

    private Placeholders() {
    }

    /**
     * The outcome of one {@link #substitute} pass: the fully-substituted command text,
     * and whether any KNOWN token (per {@link #KNOWN_TOKENS}) was left unresolved
     * because {@code values} had no entry for it. {@code hasUnresolvedToken} is set
     * while scanning the RAW input, not by re-inspecting {@code text} afterward, so an
     * injected value that happens to contain literal {@code {x}}-shaped text can never
     * flip it.
     */
    public record Result(@Nonnull String text, boolean hasUnresolvedToken) {
    }

    /**
     * Builds the substitution map for one fire. A value is present in the map ONLY
     * when it is actually resolvable this fire (e.g. {@code targetName} is null when
     * the chain has no target); an absent value leaves its token untouched in the
     * command string so {@link Result#hasUnresolvedToken()} can catch it after
     * substitution (see {@link #substitute}).
     *
     * <p>{@code x}/{@code y}/{@code z} are the interacting player's position, floored
     * to int, per SPEC, and follow the same present-only-when-resolvable rule as
     * {@code targetName}/{@code targetUuid}/{@code heldItemId}: pass {@code null} for
     * all three (never a fallback of {@code 0.0}) when the firing entity has no
     * resolvable position (e.g. no {@code TransformComponent}), so {@code {x}}/
     * {@code {y}}/{@code {z}} are left unresolved rather than silently substituted
     * with a wrong coordinate. The floored digits are locale-independent by
     * construction ({@link Integer#toString(int)} applies no locale-sensitive
     * grouping), so no explicit {@code Locale.ROOT} formatting is needed.
     */
    @Nonnull
    public static Map<String, String> buildValues(
            @Nonnull String playerName, @Nonnull UUID playerUuid,
            @Nullable Double x, @Nullable Double y, @Nullable Double z,
            @Nonnull String worldName,
            @Nullable String targetName, @Nullable UUID targetUuid,
            @Nullable String heldItemId) {
        Map<String, String> values = new HashMap<>();
        values.put("{player}", playerName);
        values.put("{uuid}", playerUuid.toString());
        if (x != null) {
            values.put("{x}", Integer.toString((int) Math.floor(x)));
        }
        if (y != null) {
            values.put("{y}", Integer.toString((int) Math.floor(y)));
        }
        if (z != null) {
            values.put("{z}", Integer.toString((int) Math.floor(z)));
        }
        values.put("{world}", worldName);
        if (targetName != null) {
            values.put("{target}", targetName);
        }
        if (targetUuid != null) {
            values.put("{targetUuid}", targetUuid.toString());
        }
        if (heldItemId != null) {
            values.put("{item}", heldItemId);
        }
        return values;
    }

    /**
     * Substitutes every entry of {@code values} into {@code command} in a single
     * left-to-right pass over the raw string. A token with no entry in {@code values}
     * is left untouched (literally re-emitted) in the returned text; if that token is
     * also a KNOWN token (per {@link #KNOWN_TOKENS}), {@link Result#hasUnresolvedToken}
     * is set. That flag is decided HERE, per raw token encountered during the pass, and
     * is never derived by re-scanning the substituted output afterward, so an injected
     * value (a world name, item id, or player name) that happens to contain literal
     * {@code {x}}-shaped text can never itself be mistaken for an unresolved token.
     * Doing this in one pass (rather than one {@code String.replace} per map entry) also
     * makes substitution independent of {@code values}' iteration order, and guarantees
     * a substituted value is never itself re-scanned for further tokens.
     */
    @Nonnull
    public static Result substitute(@Nonnull String command, @Nonnull Map<String, String> values) {
        Matcher matcher = TOKEN_PATTERN.matcher(command);
        StringBuilder result = new StringBuilder();
        boolean unresolved = false;
        while (matcher.find()) {
            String token = matcher.group();
            String replacement = values.get(token);
            if (replacement == null) {
                if (isKnownToken(token)) {
                    unresolved = true;
                }
                replacement = token;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return new Result(result.toString(), unresolved);
    }

    private static boolean isKnownToken(@Nonnull String token) {
        for (String known : KNOWN_TOKENS) {
            if (known.equals(token)) {
                return true;
            }
        }
        return false;
    }
}
