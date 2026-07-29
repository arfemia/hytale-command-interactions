package com.ziggfreed.interactioncommands.interaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.schema.config.StringSchema;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validator;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.interactioncommands.util.GateLogic;
import com.ziggfreed.interactioncommands.util.Log;
import com.ziggfreed.interactioncommands.util.Placeholders;

/**
 * Custom interaction step, registered as {@code "RunCommand"}, that runs one or more
 * configured server or player commands from anywhere a native Hytale interaction chain
 * is authored (weapon ability chains, consumable Use/Secondary chains, RootInteractions,
 * custom items/blocks).
 *
 * <p><b>Gate semantics (SPEC).</b> A cooldown still active, a chance-roll miss, an
 * absent permission, or no player on the interacting entity are ALL silent skips: this
 * step completes as a no-op ({@link InteractionState#Finished}) and the rest of the
 * chain continues (a weapon still swings, a consumable still consumes).
 * {@link InteractionState#Failed} is reserved for hard internal errors only (a null
 * {@link CommandBuffer}, an uncaught error) - never for a gate miss.
 *
 * <p><b>Gate order.</b> Player presence, then {@code Permission}, then a defensive
 * commands-non-empty guard (load-time validation should already have rejected an empty
 * or omitted {@code Commands}, but a step must never NPE the world thread even if a
 * broken asset slipped through), then the {@code Chance} roll (evaluated on every
 * trigger, so a proc's odds match its documented per-hit probability), then placeholder
 * resolution (so a command list that resolves to nothing real - every entry blank or
 * carrying an unresolved token - never touches the cooldown), and only then {@code
 * Cooldown}: peeked without consuming, and consumed with {@link
 * CooldownHandler.Cooldown#deductCharge()} only once this fire is committed to actually
 * dispatching at least one command. A chance miss or an all-skipped command list can
 * never burn the cooldown window.
 *
 * <p><b>Cooldown mechanism.</b> The {@code CooldownHandler} passed into {@link
 * #firstRun} is a per-INTERACTING-ENTITY component (see {@code InteractionManager},
 * which {@code implements Component<EntityStore>} and owns exactly one {@code
 * CooldownHandler} per entity, ticked automatically every interaction tick). For a
 * player-driven chain, {@code context.getEntity()} IS the player entity, so this
 * handler is already a native per-player cooldown store - no bespoke {@code
 * ConcurrentHashMap} is needed. Each decoded {@code RunCommand} step (one instance per
 * authored JSON entry, reused for every fire) generates a random cooldown key ONCE at
 * construction ({@link #cooldownKey}), so the handler's internal id-keyed map separates
 * this step's cooldown from any other {@code RunCommand} step on the same chain/entity
 * ("per player per authored step", per SPEC). Charge times use the engine's own {@link
 * InteractionManager#DEFAULT_CHARGE_TIMES} (a single always-recharged charge slot), so
 * {@code Cooldown} alone gates re-fire, matching the native default-cooldown shape used
 * throughout the shared source (e.g. {@code TriggerCooldownInteraction}). Like every
 * native interaction cooldown, this resets on server restart (the handler itself is not
 * persisted) - an accepted limitation, not specific to this mod.
 */
public final class RunCommandInteraction extends SimpleInstantInteraction {

    /** The codec type name referenced from an interaction chain / RootInteraction JSON. */
    public static final String TYPE_NAME = "RunCommand";

    private static final String RUN_AS_SERVER = "Server";
    private static final String RUN_AS_PLAYER = "Player";

    /**
     * Restricts {@code RunAs} to the two SPEC-accepted values, case-insensitively (the
     * same comparison {@link #firstRun} itself uses), so a typo such as {@code "Playre"}
     * is a load-time validation error instead of a silent upgrade to {@link
     * ConsoleSender#INSTANCE}'s full authority.
     */
    @Nonnull
    private static final Validator<String> RUN_AS_VALIDATOR = new Validator<>() {
        @Override
        public void accept(@Nullable String value, @Nonnull ValidationResults results) {
            if (value != null && !RUN_AS_SERVER.equalsIgnoreCase(value) && !RUN_AS_PLAYER.equalsIgnoreCase(value)) {
                results.fail("RunAs must be \"Server\" or \"Player\" (got \"" + value + "\")");
            }
        }

        @Override
        public void updateSchema(SchemaContext context, Schema target) {
            if (target instanceof StringSchema stringSchema) {
                stringSchema.setEnum(new String[] {RUN_AS_SERVER, RUN_AS_PLAYER});
            }
        }
    };

    /**
     * The codec used to parse {@link RunCommandInteraction} instances. {@code Commands}
     * is the only required field; every other field is optional with the SPEC-defined
     * default baked into its Java field initializer below. Every field uses {@code
     * appendInherited} (not the plain 3-arg {@code append}), so a native {@code Parent}
     * reference actually inherits these fields instead of silently dropping them (the
     * base {@code SimpleInteraction} fields already inherit; without this, {@code
     * Parent} would appear to work while quietly losing Commands/RunAs/Chance/etc).
     *
     * <p><b>Not unit-JVM-testable.</b> Forcing this field's class-init outside a live
     * server throws: {@code Interaction}'s own static initializer transitively reaches
     * {@code InteractionEffects} -&gt; {@code ModelParticle} -&gt; ... -&gt; a {@code
     * RangeValidator} whose {@code <clinit>} touches {@code HytaleLogger}, which throws
     * {@code IllegalStateException} ("Log manager wasn't set!") unless the Hytale
     * server has already installed {@code HytaleLogManager} as the JVM's log manager
     * (verified by actually running a codec-init test here; it was removed rather than
     * shipped flaky). This is a property of the engine's {@code Interaction} codec
     * tree, not of this class; it initializes normally once the server boots it.
     */
    @Nonnull
    public static final BuilderCodec<RunCommandInteraction> CODEC = BuilderCodec.builder(
            RunCommandInteraction.class, RunCommandInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Runs one or more server or player commands from inside an interaction chain.")
            .appendInherited(new KeyedCodec<>("Commands", Codec.STRING_ARRAY),
                    (interaction, value, info) -> interaction.commands = value,
                    (interaction, info) -> interaction.commands,
                    (interaction, parent, info) -> interaction.commands = parent.commands)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonEmptyArray())
            .documentation("Command strings to run, in order. A leading \"/\" is tolerated and stripped.")
            .add()
            .appendInherited(new KeyedCodec<>("RunAs", Codec.STRING),
                    (interaction, value, info) -> interaction.runAs = value,
                    (interaction, info) -> interaction.runAs,
                    (interaction, parent, info) -> interaction.runAs = parent.runAs)
            .addValidator(Validators.nonNull())
            .addValidator(RUN_AS_VALIDATOR)
            .documentation("\"Server\" (default) runs with full console authority; \"Player\" runs as the "
                    + "interacting player's own PlayerRef, subject to their real permissions.")
            .add()
            .appendInherited(new KeyedCodec<>("Cooldown", Codec.FLOAT),
                    (interaction, value, info) -> interaction.cooldownSeconds = value,
                    (interaction, info) -> interaction.cooldownSeconds,
                    (interaction, parent, info) -> interaction.cooldownSeconds = parent.cooldownSeconds)
            .addValidator(Validators.min(0.0f))
            .documentation("Per-player, per-authored-step cooldown in seconds. 0 or omitted = none.")
            .add()
            .appendInherited(new KeyedCodec<>("Chance", Codec.FLOAT),
                    (interaction, value, info) -> interaction.chance = value,
                    (interaction, info) -> interaction.chance,
                    (interaction, parent, info) -> interaction.chance = parent.chance)
            .addValidator(Validators.range(0.0f, 1.0f))
            .documentation("Probability (0.0-1.0) the step fires at all on a given trigger. Omitted = 1.0.")
            .add()
            .appendInherited(new KeyedCodec<>("Permission", Codec.STRING),
                    (interaction, value, info) -> interaction.permission = value,
                    (interaction, info) -> interaction.permission,
                    (interaction, parent, info) -> interaction.permission = parent.permission)
            .documentation("A permission node the interacting player must hold. Absent = no gate.")
            .add()
            .build();

    /**
     * The commands to run (leading "/" tolerated and stripped at fire time). Required;
     * {@code null} until decoded, so {@link Validators#nonNull()} can actually fire at
     * asset-load time (an initializer default of an empty array would never be null,
     * silently defeating that validator - see the class javadoc).
     */
    private String[] commands;

    /** "Server" (default) = {@link ConsoleSender#INSTANCE}; "Player" = the interacting player. */
    private String runAs = RUN_AS_SERVER;

    /** Seconds per player per authored step; 0/omitted = no cooldown. */
    private float cooldownSeconds = 0f;

    /** 0.0-1.0 probability per fire; omitted = 1.0 (always fires). */
    private float chance = 1f;

    /** Permission node the interacting player must hold; omitted = no gate. */
    @Nullable
    private String permission;

    /**
     * A stable per-authored-step cooldown key, generated once when this instance is
     * decoded (see the class javadoc's cooldown-mechanism note).
     */
    private final String cooldownKey = "interactioncommands:runcommand:" + UUID.randomUUID();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        try {
            CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
            if (commandBuffer == null) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            Ref<EntityStore> entity = context.getEntity();
            PlayerRef playerRef = commandBuffer.getComponent(entity, PlayerRef.getComponentType());
            if (playerRef == null) {
                // No player on the interacting entity: silent skip per SPEC.
                context.getState().state = InteractionState.Finished;
                return;
            }

            if (permission != null && !permission.isBlank() && !playerRef.hasPermission(permission)) {
                context.getState().state = InteractionState.Finished;
                return;
            }

            if (commands == null || commands.length == 0) {
                // Load-time validation should already reject this; defend anyway so a
                // broken/Parent-shortcut asset still resolves as a clean no-op rather
                // than looping zero commands with no diagnostic, or NPEing the world
                // thread.
                context.getState().state = InteractionState.Finished;
                return;
            }

            if (GateLogic.missesChance(chance, ThreadLocalRandom.current().nextFloat())) {
                context.getState().state = InteractionState.Finished;
                return;
            }

            boolean asPlayer = RUN_AS_PLAYER.equalsIgnoreCase(runAs);
            if (!asPlayer && !RUN_AS_SERVER.equalsIgnoreCase(runAs)) {
                // Defensive only: the codec's Validators.nonNull() + RUN_AS_VALIDATOR
                // combination should make this branch unreachable post-decode (see the
                // CODEC javadoc), so this stays at fine level rather than warn to avoid
                // spamming the log on every fire if it is somehow ever reached.
                Log.fine("RunCommand: RunAs \"" + runAs
                        + "\" is neither \"Server\" nor \"Player\"; treating as \"Server\".");
            }

            Map<String, String> values = buildPlaceholderValues(context, commandBuffer, entity, playerRef);
            List<String> resolvedCommands = new ArrayList<>(commands.length);
            for (String rawCommand : commands) {
                if (rawCommand == null) {
                    continue;
                }

                // Strip a leading "/" FIRST, then check blank: a raw entry of just "/"
                // (or "/" followed only by whitespace) must never survive as an empty
                // dispatched command (console "notFound" noise) or count toward
                // resolvedCommands for the cooldown-charge decision below.
                String withoutSlash = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
                if (withoutSlash.isBlank()) {
                    continue;
                }

                Placeholders.Result substitutionResult = Placeholders.substitute(withoutSlash, values);
                if (substitutionResult.hasUnresolvedToken()) {
                    Log.fine("RunCommand: skipping an entry with an unresolved placeholder: " + rawCommand);
                    continue;
                }

                resolvedCommands.add(substitutionResult.text());
            }

            if (resolvedCommands.isEmpty()) {
                // Nothing survived resolution: this fire dispatches nothing real, so
                // the cooldown window (if any) must not be started.
                context.getState().state = InteractionState.Finished;
                return;
            }

            if (!GateLogic.cooldownDisabled(cooldownSeconds)) {
                CooldownHandler.Cooldown cooldown = cooldownHandler.getCooldown(
                        cooldownKey, cooldownSeconds, InteractionManager.DEFAULT_CHARGE_TIMES, true, false);
                if (cooldown != null) {
                    if (cooldown.hasCooldown(false)) {
                        context.getState().state = InteractionState.Finished;
                        return;
                    }
                    // Commit the charge only now, right before we actually dispatch.
                    cooldown.deductCharge();
                }
            }

            // Dispatch the whole list through the engine's OWN sequential command
            // executor (CommandManager.handleCommands over a Deque), the same
            // thenCompose-chain shape CommandMacro's MacroCommandBase uses to run a
            // command list "in order": each entry starts only after the previous one's
            // future completes. A plain per-entry handleCommand loop (the prior
            // implementation) discards each returned future, so N entries become N
            // independent ForkJoinPool.commonPool() tasks racing in parallel - this
            // dispatch is scheduled here, not awaited (firstRun still resolves
            // Finished immediately below), so the fire-and-forget shape is preserved.
            CommandSender sender = asPlayer ? playerRef : ConsoleSender.INSTANCE;
            CommandManager.get().handleCommands(sender, new ArrayDeque<>(resolvedCommands))
                    .exceptionally(commandError -> {
                        Log.warn("RunCommand: a command in the list failed to dispatch", commandError);
                        return null;
                    });

            context.getState().state = InteractionState.Finished;
        } catch (Throwable t) {
            Log.severe("RunCommand: hard error running interaction", t);
            context.getState().state = InteractionState.Failed;
        }
    }

    /**
     * Assembles this fire's placeholder value map by reading the live Hytale context
     * (position, world, target, held item) and handing plain primitives/Strings to the
     * engine-free {@link Placeholders#buildValues}.
     */
    @Nonnull
    private Map<String, String> buildPlaceholderValues(@Nonnull InteractionContext context,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Ref<EntityStore> entity,
            @Nonnull PlayerRef playerRef) {
        Double x = null;
        Double y = null;
        Double z = null;
        var transform = commandBuffer.getComponent(entity, TransformComponent.getComponentType());
        if (transform != null) {
            Vector3d position = transform.getPosition();
            x = position.x;
            y = position.y;
            z = position.z;
        } else {
            // No TransformComponent on the firing entity: leave x/y/z out of the value
            // map entirely (never fall back to 0/0/0) so {x}/{y}/{z} are caught as
            // unresolved placeholders instead of silently resolving to the world origin.
            Log.fine("RunCommand: no TransformComponent on the firing entity; {x}/{y}/{z} left unresolved.");
        }

        String worldName = commandBuffer.getExternalData().getWorld().getName();

        String targetName = null;
        UUID targetUuid = null;
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef != null && targetRef.isValid()) {
            PlayerRef targetPlayerRef = commandBuffer.getComponent(targetRef, PlayerRef.getComponentType());
            if (targetPlayerRef != null) {
                targetName = targetPlayerRef.getUsername();
            }
            var targetUuidComponent = commandBuffer.getComponent(targetRef, UUIDComponent.getComponentType());
            if (targetUuidComponent != null) {
                targetUuid = targetUuidComponent.getUuid();
            }
        }

        String heldItemId = null;
        ItemStack heldItem = context.getHeldItem();
        if (heldItem != null) {
            heldItemId = heldItem.getItemId();
        }

        return Placeholders.buildValues(
                playerRef.getUsername(), playerRef.getUuid(),
                x, y, z, worldName, targetName, targetUuid, heldItemId);
    }

    /**
     * {@code Server} is mandatory here, not a tuning choice: {@code SimpleInteraction}'s
     * own class contract requires it for any implementation whose {@code firstRun} can
     * set {@link InteractionState#Failed} (this one does, for a null {@link
     * CommandBuffer} or an uncaught error), and {@code configurePacket} propagates this
     * value into the client packet accordingly. The cost is a client-server round trip
     * AT this step, in the middle of whatever chain hosts it, paid on every ordinary
     * fire even though the {@code Failed} branch this buys is purely defensive and
     * should never actually execute. Do not "optimize" this to {@code None} without also
     * removing every {@code Failed} path in {@link #firstRun}.
     */
    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    public String toString() {
        return "RunCommandInteraction{commands=" + Arrays.toString(commands)
                + ", runAs='" + runAs + '\''
                + ", cooldown=" + cooldownSeconds
                + ", chance=" + chance
                + ", permission='" + permission + '\''
                + "} " + super.toString();
    }
}
