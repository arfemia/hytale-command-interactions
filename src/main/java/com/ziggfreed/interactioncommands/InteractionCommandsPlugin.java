package com.ziggfreed.interactioncommands;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.ziggfreed.interactioncommands.interaction.RunCommandInteraction;
import com.ziggfreed.interactioncommands.util.Log;

/**
 * Entry point for InteractionCommands - a zero-dependency, single-jar Hytale mod that
 * registers ONE custom interaction type, {@code RunCommand}, usable inline anywhere a
 * native interaction chain is authored (weapon ability chains, consumable Use/Secondary
 * chains, RootInteractions, custom items/blocks). See {@link RunCommandInteraction} for
 * the full field/gate/placeholder contract.
 *
 * <p>This mod ships no assets and no lang files - it is docs-only examples, with zero
 * player-facing text of its own.
 */
public class InteractionCommandsPlugin extends JavaPlugin {

    /** The plugin's single logger instance; use {@link Log}, not this field directly. */
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static InteractionCommandsPlugin instance;

    public static InteractionCommandsPlugin getInstance() {
        return instance;
    }

    public InteractionCommandsPlugin(@Nonnull final JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        try {
            getCodecRegistry(Interaction.CODEC).register(
                    RunCommandInteraction.TYPE_NAME,
                    RunCommandInteraction.class,
                    RunCommandInteraction.CODEC);
            Log.info("Registered interaction: " + RunCommandInteraction.TYPE_NAME);
        } catch (Exception e) {
            // Rethrow rather than swallow: a failed registration here means every pack
            // asset using RunCommand later dies inside the engine's own asset decoder
            // with an UnknownIdException pointing at the SERVER OWNER'S pack, not at
            // this mod. Fail loudly and visibly at plugin-load time instead, where the
            // real cause is attributable.
            Log.severe("Failed to register RunCommand interaction", e);
            throw new IllegalStateException(
                    "InteractionCommands failed to register the RunCommand interaction type", e);
        }
    }
}
