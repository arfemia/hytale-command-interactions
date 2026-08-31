# CLAUDE.md - InteractionCommands

A **standalone, dependency-free Hytale mod** that adds one custom interaction Type, `RunCommand`,
usable inline anywhere a native interaction chain accepts a step: a weapon's swing/hit chain, a
consumable's Use/Secondary, a custom RootInteraction, a custom item or block. It runs one or more
commands with either console or player authority, gated by an optional per-player cooldown,
chance, and permission node, with placeholder substitution into each command string. It is a
supplemental mod under the **hyMMO monorepo**'s `additional-mods/` (a git submodule; development
is launched from hyMMO, like the content packs and every other sibling mod).

**Status: v1.1.0 (released 2026-08-31; the Update 6 rebuild + doc refresh over the
released 1.0.0).** The interaction Type, its five codec fields, placeholder substitution, and
the silent-skip gate semantics are the whole mod. It ships no assets, no lang files, and no
commands of its own; every worked example in `README.md`/`CURSEFORGE.md` lives in a server
owner's own asset pack.

## Build

Gradle runs via PowerShell (Java 25). Self-contained `build.ps1` builds + installs:
```powershell
cd 'D:\dev\business\hyMMO\additional-mods\command-interactions'; .\build.ps1
.\build.ps1 -Install:$false     # build only
.\build.ps1 -ModsDir <path>     # explicit install target (else $env:HYTALE_MODS_DIR)
```
Produces `build/libs/InteractionCommands-<version>.jar` and copies the runtime jar (never
`-sources`/`-javadoc`) into the Hytale `Mods/` folder when `HYTALE_MODS_DIR` is set.
`.\gradlew.bat build` works too.

**ZERO dependencies is deliberate.** This mod does not compile or link against `ziggfreed-common`,
the MMO Skill Tree jar, or any other sibling mod. `manifest.json` declares no `Dependencies` entry
at all. The maintainer chose this on purpose: InteractionCommands is meant to be a single-jar
CurseForge install a server owner drops in without pulling in any companion mod, unlike
`kweebec-nightmare` (hard-deps `ziggfreed-common` + Perfect Utils) or `rpg-stations` (hard-deps
`ziggfreed-common`). Do not add a dependency here without the maintainer's explicit sign-off; if a
generic Hytale primitive would help, it still stays out of scope for this mod (re-derive the small
amount of logic locally rather than pulling in a library for a one-jar tool).

## Layout

```
settings.gradle / gradle.properties / build.gradle    single Gradle module, Java 25 toolchain
build.ps1                                              build + auto-install (self-locating)
examples/                                              the runnable InteractionCommandsExamples asset
                                                        pack (source + build-example-pack.ps1 + the
                                                        committed zip); hard-deps this mod, ships no
                                                        Java of its own, see its own section below
src/main/resources/manifest.json                       Group Ziggfreed, ServerVersion ">=0.6.0-pre.13 <0.7.0",
                                                        declares no IncludesAssetPack key at all (no assets, no
                                                        lang), no Dependencies
src/main/java/com/ziggfreed/interactioncommands/
  InteractionCommandsPlugin.java                       JavaPlugin entry: registers the RunCommand
                                                        interaction Type against Interaction.CODEC at setup()
  interaction/
    RunCommandInteraction.java                         the SimpleInstantInteraction implementation: decodes
                                                        Commands/RunAs/Cooldown/Chance/Permission (all
                                                        appendInherited so a native Parent reference actually
                                                        inherits them, plus load-time validators), evaluates
                                                        the gate (player/permission/commands-guard/chance, then
                                                        placeholder resolution, then cooldown peeked-and-consumed
                                                        only on a real dispatch), resolves placeholders per
                                                        command, dispatches the resolved list IN ORDER via
                                                        CommandManager.handleCommands (a Deque, chained futures)
                                                        with ConsoleSender.INSTANCE or the player's PlayerRef,
                                                        returns InteractionState.Finished on every path (gate
                                                        miss or real fire alike) - never Failed for a gate miss
  util/
    Placeholders.java                                  {player}/{uuid}/{x}/{y}/{z}/{world}/{target}/{targetUuid}/
                                                        {item} substitution (single left-to-right regex pass,
                                                        order-independent); `substitute` returns a `Result(text,
                                                        hasUnresolvedToken)` that tracks the unresolved flag
                                                        DURING that same pass (never by re-scanning the
                                                        substituted output), driving the per-command skip
    GateLogic.java                                      pure chance/cooldown boundary predicates (Chance <1 miss
                                                        check, Cooldown <=0 disabled check), zero Hytale imports
                                                        so it unit-tests without a live server
    Log.java                                            this mod's own SafeLog-shaped facade over
                                                        InteractionCommandsPlugin.LOGGER (info/warn/severe/fine,
                                                        Throwable overloads, guarded try/catch so a unit-JVM
                                                        without a Hytale log manager never crashes a test) -
                                                        never a raw LOGGER fluent chain outside Log itself
src/test/java/com/ziggfreed/interactioncommands/util/
  GateLogicTest.java / PlaceholdersTest.java         15 engine-free unit tests (JUnit + java.util
                                                        imports only). The Hytale server jar is
                                                        deliberately OFF the test classpath (forcing
                                                        RunCommandInteraction.CODEC's class-init outside a
                                                        live server throws), so anything needing the engine
                                                        is not unit-testable here; PlaceholdersTest drives
                                                        its coverage off Placeholders.knownTokens() rather
                                                        than a hardcoded copy of the token list
```

No `Server/` asset tree, no `Languages/`, no `.ui` under `src/main/resources/`. This mod has zero
player-facing text of its own; every string a server owner sees comes from the commands they
author. The one exception is `examples/`, a SEPARATE asset pack (its own `manifest.json`, its own
`Server/` tree, its own `.lang`) that hard-deps this mod and demonstrates it live; see "Example
asset pack" below.

## Example asset pack

`examples/InteractionCommandsExamples/` is a runnable Hytale asset pack (three custom items) that
is also the reference implementation of the three worked examples in `README.md`/`CURSEFORGE.md`.
It is a separate pack from this mod's own jar, with `"IncludesAssetPack": true` and a hard
`"Dependencies": {"Ziggfreed:InteractionCommands": ">=1.0.0"}` (the RunCommand Type is unknown at
asset decode without the mod installed).

```
examples/
├── build-example-pack.ps1                              zips the pack (forward-slash entries + dir
                                                          entries, never Compress-Archive), honors
                                                          $env:HYTALE_MODS_DIR like the mod's own build.ps1
├── InteractionCommandsExamples.zip                      the committed, ready-to-drop-in zip (built by
                                                          build-example-pack.ps1; kept in the repo, not
                                                          gitignored, so it is downloadable as-is)
└── InteractionCommandsExamples/                         the pack SOURCE
    ├── manifest.json
    └── Server/
        ├── Item/Items/
        │   ├── Example_Command_Blade.json               "Parent": "Weapon_Longsword_Crude" (a new
        │   │                                             derived weapon, not a native override); adds a
        │   │                                             RunCommand step into the
        │   │                                             InteractionVars.Longsword_Swing_Left_Damage var
        │   │                                             alongside a restated copy of Crude's own
        │   │                                             DamageCalculator/DamageEffects (fires on a
        │   │                                             landed left swing, Chance 0.25, Cooldown 5)
        │   ├── Example_Command_Potion.json               "Parent": "Potion_Stamina"; InteractionVars.Effect
        │   │                                             keeps the native Serial ApplyEffect chain and
        │   │                                             appends a RunCommand step (fires on consume)
        │   └── Example_Command_Meal.json                 "Parent": "Food_Wildmeat_Cooked"; InteractionVars.Effect
        │                                                 keeps the native ApplyEffect + string-ref chain and
        │                                                 appends a RunCommand step that gives the Command
        │                                                 Potion above (meal -> potion -> torches chain)
        └── Languages/en-US/interactioncommandsexamples.lang   the three items' name/description keys
                                                                (filename is the key namespace)
```

Rebuild after touching the source: `cd examples; .\build-example-pack.ps1` (`-Install:$false` to
build only, `-ModsDir <path>` or `$env:HYTALE_MODS_DIR` to auto-copy). It does not touch the mod's
own Gradle build; the two build normally in sequence (`.\build.ps1` for the jar, then
`examples\build-example-pack.ps1` for the pack) when validating both together.

## Architecture

- **One registered interaction Type, `RunCommand`, decoded on the engine's `Interaction.CODEC`**
  (the same codec every native interaction Type - `ApplyEffect`, `SendMessage`, `DamageCalculator`
  - decodes against), registered at plugin `setup()`. It authors like any other interaction step:
  an inline `{"Type": "RunCommand", ...}` object, or a named ref to a standalone JSON asset that is
  itself `{"Interactions": [{"Type": "RunCommand", ...}]}`.
- **Backed by a `SimpleInstantInteraction`** (the same base class the engine's own trivial,
  no-animation interaction steps use), so it fires and resolves within a single tick with no
  intermediate state.
- **Dispatch goes through `CommandManager.handleCommands` (plural, over a `Deque`)**, the engine's
  own sequential command executor (the same `thenCompose`-chain shape `CommandMacro`'s
  `MacroCommandBase` uses), so a multi-entry `Commands` list runs strictly IN ORDER: each entry
  starts only after the previous one's future completes. A per-entry `handleCommand` loop that
  discards each returned future would instead race every entry in parallel on the common pool; this
  mod deliberately never does that. **Abort semantics are inherited from `CommandManager`, not
  chosen by this mod**: `handleCommands0`'s recursive `thenCompose` (see
  `CommandManager.java:475-477`) means a command that throws a genuine, uncaught error completes
  its future EXCEPTIONALLY, so `thenCompose` never runs the next recursive call and every remaining
  entry in that fire's `Commands` list is silently dropped (never even attempted); this mod's own
  `.exceptionally` hook on the returned future logs that as a `Log.warn`. A command that is merely
  **unknown** (no matching registration) completes its future NORMALLY instead
  (`CommandManager.java:376-382`), so it does NOT abort the rest of the list. Do not change this
  dispatch mechanism to "fix" the abort behavior; it is the engine's own sequential-executor
  contract, only documented here (README/CURSEFORGE Troubleshooting cover the user-facing version).
  The sender is selected once per fire by `RunAs`:
  `ConsoleSender.INSTANCE` for `"Server"` (default, full authority, bypasses permission checks
  entirely), or the interacting entity's own `PlayerRef` component for `"Player"` (so the engine's
  normal permission enforcement applies to the command exactly as if the player typed it).
- **The gate check runs before dispatch, per command list, per player.** In order: is there a
  player on the firing entity at all; does `Permission` (if set) resolve true for them; a
  defensive commands-non-empty guard (load-time validation should already reject this); does
  `Chance` (if set) roll a hit, checked on EVERY trigger so a proc's odds match its documented
  per-hit probability. Only after that does placeholder resolution run (see below); if it leaves
  at least one command actually resolved, THEN `Cooldown` (if set) is checked - peeked without
  consuming it, and consumed only once this fire is committed to dispatching. A chance miss or an
  all-skipped command list therefore never burns the cooldown window. Any gate miss short-circuits
  to `InteractionState.Finished` with no command run and no log noise beyond fine-level detail;
  `Failed` never fires for a gate miss, only for a genuine internal error or thrown `Throwable`
  inside `firstRun` itself.
- **Placeholder resolution happens per command, after the earlier gates pass but before the
  cooldown check.** Each command string in `Commands` is substituted independently (a single
  left-to-right regex pass, order-independent); if a known placeholder token remains unresolved
  after substitution (the interaction has no target entity so `{target}`/`{targetUuid}` cannot
  resolve), that single command is skipped with a fine log line and the rest of the list still
  runs. The unresolved-token flag is decided DURING the single substitution pass (not by
  re-scanning the substituted output), so an injected value that happens to contain literal
  `{x}`-shaped text can never itself trip a false skip; see `Placeholders.Result`.
  `CommandManager.handleCommands` schedules the whole (ordered) chain onto the common pool and
  returns immediately, so `firstRun` always resolves `Finished` once dispatch is scheduled;
  execution and any command failure happen asynchronously, are logged by the engine's own command
  system PLUS a `Log.warn` from this mod's own `.exceptionally` hook, and are never surfaced back
  as interaction `Failed`.

## Conventions

- No em-dashes anywhere (code comments, javadoc, docs, chat). Use a comma, semicolon, parens, or
  " - ".
- PascalCase codec keys (`Commands`, `RunAs`, `Cooldown`, `Chance`, `Permission`), matching every
  other Hytale asset codec in this monorepo.
- Never call a deprecated engine API and never `@SuppressWarnings("deprecation")`. Read the shared
  source's deprecation javadoc for the current replacement before touching an API that looks
  legacy; if there is genuinely no replacement, ask before proceeding. BUILD-ENFORCED: `compileJava`
  carries `-Xlint:removal -Werror` (`build.gradle`), so calling any Hytale API marked
  `@Deprecated(forRemoval = true)` is a hard compile failure (compileJava only, not
  compileTestJava).
- `@Nonnull`/`@Nullable` on every parameter.
- Log through `util.Log` (info/warn/severe/fine, `Throwable` overloads), never a raw
  `InteractionCommandsPlugin.LOGGER` fluent chain outside `Log` itself, mirroring every sibling
  mod's own logging facade.
- **Silent-skip gate semantics are load-bearing, not a shortcut.** A cooldown, a chance miss, a
  missing permission, or no player present must resolve `InteractionState.Finished`, never
  `Failed` and never throw. A native interaction chain this step is embedded in (a weapon swing, a
  consumable's Use) must keep running exactly as if the gated step were absent.
- Package root `com.ziggfreed.interactioncommands`. As a submodule, the order is fixed: commit +
  push HERE first, verify the SHA is on the remote, THEN bump the gitlink in the parent hyMMO
  repo.
