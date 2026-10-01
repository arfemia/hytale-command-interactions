# InteractionCommands

Standalone Hytale mod: one custom interaction Type, `RunCommand`, usable inline anywhere a native chain accepts a step. `RunCommandInteraction`'s javadoc is the behavior reference; this file holds only what it does not say. The family-wide rules apply here.

- Zero dependencies by maintainer decision: no `ziggfreed-common`, no MMO jar, no manifest `Dependencies`. Never add one or lift code into `ziggfreed-common` without the maintainer's sign-off, which overrides the family lift-to-zc rule.
- Build with `.\build.ps1` (`-Install:$false` builds only; `-ModsDir <path>` or `HYTALE_MODS_DIR` installs). The jar ships no assets, lang or commands. Its `gradle/deprecation-gate.gradle` (run by `check`) is hyMMO's, copied byte for byte: it changes only by copying hyMMO's.
- `examples/` is a separate asset pack that hard-deps this mod. After editing its source run `examples\build-example-pack.ps1` (forward-slash zip entries, never `Compress-Archive`) and commit the regenerated `InteractionCommandsExamples.zip`, which is tracked on purpose.
- A gate miss (no player, no permission, chance miss, cooldown, every command skipped) resolves `InteractionState.Finished`, never `Failed` or a throw, so the carrying chain runs as if the step were absent.
- Dispatch stays `CommandManager.handleCommands` over a `Deque` so entries run in order; never a per-entry `handleCommand` loop, and do not change the engine's abort-on-exception behaviour.
- Tests stay engine-free: the server jar is deliberately off the test classpath because `RunCommandInteraction.CODEC` class-init throws outside a live server.
- Log through `util.Log`; this mod has no `SafeLog`.
