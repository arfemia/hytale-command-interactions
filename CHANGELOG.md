# Changelog

Developer changelog for InteractionCommands. User-facing release notes live in `patch-notes/`.

## 1.0.0

First release. Adds the `RunCommand` custom interaction Type, a single, dependency-free primitive
that runs one or more commands from inside any native Hytale interaction chain.

- **Adds the `RunCommand` interaction Type**, decoded on `Interaction.CODEC` alongside every
  native interaction Type, authorable inline (`{"Type": "RunCommand", ...}`) anywhere a chain
  accepts a step: weapon `InteractionVars`, a consumable's `Interactions`, a custom
  RootInteraction, a custom item or block's `Use`/`Secondary`.
- **Adds `RunAs` authority selection.** `"Server"` (default, omitted = Server) executes with full
  `ConsoleSender.INSTANCE` authority; `"Player"` executes as the interacting player's own
  `PlayerRef`, so the game's own permission checks on the command apply.
- **Adds `Cooldown`**, a per-player, per-authored-step cooldown in seconds (0 or omitted = none).
- **Adds `Chance`**, a 0.0-1.0 fire probability per trigger (omitted = 1.0, always fires).
- **Adds `Permission`**, a permission node the interacting player must hold for the step to fire
  (omitted = no gate).
- **Adds placeholder substitution**: `{player}`, `{uuid}`, `{x}`/`{y}`/`{z}` (floored,
  locale-independent), `{world}`, `{target}` (target entity name, player targets only),
  `{targetUuid}`, `{item}` (held item id), resolved into every command string at fire time.
- **Adds silent-skip gate semantics.** A cooldown still active, a `Chance` miss, an absent
  `Permission`, or no player on the firing entity completes the step as a no-op
  (`InteractionState.Finished`); the rest of the chain, and the native action carrying it, keeps
  running. `Failed` is reserved for genuine internal errors, never a gate miss.
- **Adds the unresolved-placeholder skip rule.** If, after substitution, a single command still
  contains a known-but-unresolvable token (most commonly `{target}` with no target present), that
  one command is skipped with a fine-level log line; the rest of the `Commands` list still runs.
- **Zero dependencies, no assets.** The jar ships no lang files, no assets, and no commands of its
  own; it registers exactly one interaction Type and does nothing further until a server owner's
  own asset pack authors a `RunCommand` step.
- **Adds the InteractionCommandsExamples asset pack** (`examples/`, a separate ready-to-drop-in
  zip, never bundled into the jar): three demo items showing `/give` commands fired live - a
  derived longsword with a chance-gated on-hit proc, a stamina potion, and a cooked meal whose
  consume chains each run a `RunCommand` step. Hard-deps this mod and doubles as the reference
  implementation of the docs' worked examples; source + `build-example-pack.ps1` sit beside the
  committed zip.

Technical: single-jar Gradle module, Java 25, no `ziggfreed-common` or MMO Skill Tree reference
anywhere in the build. Placeholder substitution and the chance/cooldown threshold predicates are
unit-testable in isolation; cooldown bookkeeping itself lives in the engine's own `CooldownHandler`
and RunAs sender selection is a one-line comparison in `RunCommandInteraction`, neither exercised
by a unit test here. The in-chain firing behavior (a real weapon swing, a real consumable eat, a
real block press) needs an in-game pass against a live server.
