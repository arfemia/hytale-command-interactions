package com.ziggfreed.interactioncommands.util;

/**
 * Pure gate decisions for {@code RunCommandInteraction} - zero Hytale imports, so the
 * chance/cooldown boundary logic unit-tests without a live server. The engine's own
 * {@code CooldownHandler.Cooldown} does the actual per-player cooldown bookkeeping
 * (see {@code RunCommandInteraction}'s class javadoc); this class only holds the
 * trivial "is this knob even enabled" checks around it.
 */
public final class GateLogic {

    private GateLogic() {
    }

    /**
     * SPEC: {@code Cooldown} 0 or omitted means no cooldown at all.
     *
     * @param cooldownSeconds the configured cooldown, in seconds
     * @return true when the cooldown knob is disabled
     */
    public static boolean cooldownDisabled(float cooldownSeconds) {
        return cooldownSeconds <= 0f;
    }

    /**
     * SPEC: {@code Chance} omitted means 1.0 (always fires). A chance of exactly 1.0
     * always hits regardless of {@code roll} (so a roll of exactly 1.0 from a
     * theoretical inclusive RNG can never misfire a guaranteed step).
     *
     * @param chance the configured fire probability, expected in [0.0, 1.0]
     * @param roll a random draw, expected in [0.0, 1.0)
     * @return true when this fire should be silently skipped for missing the chance roll
     */
    public static boolean missesChance(float chance, float roll) {
        return chance < 1f && roll >= chance;
    }
}
