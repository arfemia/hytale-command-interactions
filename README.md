# InteractionCommands

A tiny, dependency-free Hytale mod that lets a server owner run console or player commands from
inside a native interaction chain: a weapon swing, a consumable's Use/Secondary, a block press-F,
or any custom RootInteraction. It adds exactly one thing to the game: a custom interaction Type,
`RunCommand`, authored the same way you already author `ApplyEffect` or `SendMessage` steps.

## Features

- **One interaction Type, `RunCommand`.** Drop it inline anywhere a native interaction chain
  accepts a step: a weapon's `InteractionVars`, a consumable's `Interactions`, a custom
  RootInteraction, a custom block's `Use`/`Secondary`.
- **Run as the server console or as the interacting player.** `RunAs: "Server"` (default) executes
  with full console authority; `RunAs: "Player"` executes as the interacting player's own
  `PlayerRef`, honoring their real permissions.
- **Per-player, per-step cooldowns.** `Cooldown` gates repeat fires without any extra scripting.
- **Chance-gated procs.** `Chance` turns any step into a probabilistic proc, ideal for weapon
  effects or lucky consumables.
- **Permission-gated commands.** `Permission` requires the interacting player to hold a node
  before the step fires.
- **Placeholder substitution.** `{player}`, `{uuid}`, `{x}`/`{y}`/`{z}`, `{world}`, `{target}`,
  `{targetUuid}`, and `{item}` are substituted into every command string at fire time.
- **Silent-skip gate semantics.** A cooldown still active, a chance miss, a missing permission, or
  no player on the entity never fails the step; it completes as a no-op and the rest of the chain
  (and the native action it's attached to) keeps running.

## Install

1. Drop `InteractionCommands-<version>.jar` into your Hytale server's `Mods/` folder.
2. Requires a Hytale Update 6 (0.6.x) server (`>=0.6.0-pre.13 <0.7.0`).
3. **Zero dependencies.** No other mod, library, or content pack is required; this is a
   single-jar install.
4. Restart the server. InteractionCommands ships no assets, no lang files, and no commands of its
   own; it registers the `RunCommand` interaction Type and does nothing further until your own
   asset pack references it.

## Schema reference

InteractionCommands ships **no assets**. You author `RunCommand` steps yourself, inline, inside
your own asset pack's items, blocks, or RootInteractions, wherever a native interaction chain
accepts a step (an inline `{"Type": "...", ...}` object or a string ref to another interaction
asset). Your pack needs `"IncludesAssetPack": true` in its `manifest.json` for the engine to load
its JSON content at all; that requirement is native to Hytale packs, not something this mod adds.

### Fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `Commands` | list of strings | yes | - | Command strings to run, **in order**: each command is dispatched only after the previous one finishes (see "Dispatch order" below). A leading `/` is tolerated and stripped. |
| `RunAs` | `"Server"` \| `"Player"` | no | `"Server"` | `"Server"` runs with full console authority (`ConsoleSender.INSTANCE`). `"Player"` runs as the interacting player's own `PlayerRef`, subject to their real permissions. |
| `Cooldown` | number (seconds) | no | `0` (none) | Per-player, per-authored-step cooldown. A player who fires this step again before the cooldown elapses gets a silent skip. |
| `Chance` | number (0.0-1.0) | no | `1.0` | Probability the step fires at all on a given trigger. |
| `Permission` | string | no | none | A permission node the interacting player must hold. Absent = no gate; **an unset node resolves to a deny by default** (Hytale's own permission resolution), and server OP/admin status does NOT satisfy this gate on its own, see "Permission defaults to deny" below. |

### Dispatch order

`Commands` runs through Hytale's own sequential executor: each entry starts only after the
previous one's future completes, strictly in the order you wrote them. A command that throws a
genuine error **aborts every remaining command in that fire** (logged as a `warn` by this mod,
`"a command in the list failed to dispatch"`); a command that is merely **unknown** does NOT
abort (Hytale sends its own "command not found" message and the chain continues to the next
entry). If a multi-entry `Commands` list only ever runs its first N entries, check the log for
that warn line before assuming a placeholder or gate issue.

### Permission defaults to deny

`Permission` is checked against Hytale's own permission resolution (user grant, then group
grants, then a default), and **an unset/unknown node resolves to `false` (deny)**. Being the
server owner, an OP, or holding an admin role does **not** satisfy this gate by itself, even with
`RunAs: "Server"` (that only changes who RUNS the command, not who is allowed to trigger the
step) - the node must be explicitly granted, to the player directly or to a group they belong to,
through your permission setup.

### Placeholders

Substituted into every command string at fire time:

| Placeholder | Resolves to |
|---|---|
| `{player}` | The interacting player's name |
| `{uuid}` | The interacting player's UUID |
| `{x}` `{y}` `{z}` | The player's position, floored to an integer (locale-independent); unresolved if the firing entity has no position component (rare) |
| `{world}` | The current world's name |
| `{target}` | The chain's target entity's name, when that target is a player |
| `{targetUuid}` | The chain's target entity's UUID, when obtainable |
| `{item}` | The held item's id |

### Gate semantics

A cooldown still active, a `Chance` miss, a missing `Permission`, or no player present on the
firing entity is a **silent skip**: the step completes as a no-op (`InteractionState.Finished`)
and the rest of the chain continues exactly as if the step were absent. The weapon still swings,
the consumable still consumes. `Failed` is reserved for genuine internal errors, never a gate
miss.

### Unresolved placeholders

If, after substitution, a single command string still contains a known-but-unresolvable token
(the most common case: `{target}` when the interaction chain has no target entity; also
`{x}`/`{y}`/`{z}` when the firing entity has no position component), **that one command is
skipped** with a fine-level log line; every other command in the `Commands` list still runs.

## Troubleshooting

Every gate miss is a **silent skip by design** (see Gate semantics above): a `RunCommand` step
that never seems to do anything gives you no chat message and no error, only a log line at
`FINE`, which most servers never see at their default log level. If a step you authored isn't
firing, check these six causes IN THE ORDER the step evaluates them:

1. **No player on the firing entity.** `RunCommand` only ever runs for a player-driven interaction
   (a mob, an NPC, or any non-player entity firing the chain is always a silent skip, no exception).
2. **`Permission` is set and the player doesn't hold it.** Confirm the exact node string and that
   the player's permission setup actually grants it. **An unset node defaults to deny**, and being
   OP/admin does not satisfy the gate on its own (see "Permission defaults to deny" above) - a
   server owner testing as their own sole admin account gets a silent skip forever unless the node
   is explicitly granted.
3. **`Chance` rolled a miss.** A `Chance` below `1.0` is expected to no-op most of the time by
   design; test with `Chance: 1.0` first to rule this out before tuning the real value.
4. **Every command in `Commands` resolved with an unresolved placeholder.** The most common case
   is `{target}`/`{targetUuid}` on a chain with no target entity (a mob is not a target; only a
   player target resolves `{target}`), or `{x}`/`{y}`/`{z}` when the firing entity has no resolvable
   position. Each affected command is skipped individually; the rest of the list still runs.
5. **`Cooldown` is still active for this player on this authored step.** Cooldowns are per player,
   per authored `RunCommand` instance in your JSON, tracked independently of any other cooldown on
   the same chain.
6. **An earlier command in the list threw a genuine error.** Dispatch runs strictly in order and a
   thrown error aborts the rest of that fire's `Commands` list (see "Dispatch order" above); check
   the log for this mod's own `warn` line before assuming the later commands' placeholders or gates
   are the problem. A merely-unknown command does NOT abort.

**To see which one it is:** raise your Hytale server's log level to `FINE` (or `ALL`) so the
unresolved-placeholder skip line and the plugin's own `info`/`warn`/`severe` lines are visible;
at the default level these are invisible. Exactly how to change the log level is a server-wide
setting, not something this mod configures.

**`RunAs: "Server"` sends command output and errors to the SERVER CONSOLE, never to the player.**
A step authored with `RunAs: "Server"` (the default) runs as `ConsoleSender`, so anything the
dispatched command itself prints or errors with lands in the server terminal, not in the firing
player's chat. If you need player-visible feedback from the command itself, use `RunAs: "Player"`
instead (subject to the player's own permissions), or have the command explicitly message the
player.

## Worked examples

All three examples live in **your own asset pack** (server owner or content-pack author), not in
this mod, which ships no assets of its own. Your pack's `manifest.json` needs
`"IncludesAssetPack": true`.

### Try the example pack

The fastest way to see `RunCommand` fire live is `examples/InteractionCommandsExamples.zip` in
this repository: a small, runnable asset pack that is also the reference implementation of the
three worked examples below.

1. Drop `InteractionCommands-<version>.jar` into `Mods/` (see Install above).
2. Drop `InteractionCommandsExamples.zip` into the same `Mods/` folder. Restart the server.
3. Give yourself the three demo items (named `--quantity=N` form; a positional quantity delivers
   only one item):
   ```
   /give Example_Command_Blade --quantity=1
   /give Example_Command_Potion --quantity=1
   /give Example_Command_Meal --quantity=1
   ```
4. Swing the Command Blade at something. It has a 25% chance per landed hit, on a 5-second
   cooldown, to fire a `RunCommand` step that gives you a cooked wildmeat. Drink the Command
   Potion and watch four wall torches arrive alongside its native stamina effect. Eat the Command
   Meal and watch it fire a `RunCommand` step that gives you a Command Potion, chaining straight
   into the potion example.

The pack's own layout (three items under `Server/Item/Items/`, one `.lang` file) is documented in
"Example asset pack" in [CLAUDE.md](CLAUDE.md); the source is a good starting point to copy from
for your own pack.

### Where files go

A Hytale asset's id IS its file path key: the filename (PascalCase, no extension) is what
everything else references it by, and its filename decides whether it's a brand new
asset or an override of an existing one (see Example 2). For a pack laid out as a folder or zip
under your server's `Mods/` directory:

- **Items** live under `Server/Item/Items/**` (a new custom item can sit directly in `Items/`). An
  override of a native item needs only the SAME FILENAME as the vanilla item, plus `"Parent":
  "super"` (see Example 2); the directory is a tidiness convention, not part of the asset's
  identity. Hytale's `AssetStore` derives an asset's key from the filename alone
  (`decodeFilePathKey` strips only the path down to `path.getFileName()`, then the extension), so
  `Weapon_Longsword_Crude.json` overrides the native Longsword whether it sits at
  `Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Crude.json` (mirroring vanilla's own layout,
  the usual convention) or flat in `Server/Item/Items/Weapon_Longsword_Crude.json`. This mod's own
  example pack proves the directory is inert even for a `Parent` reference: `Example_Command_Blade.json`
  sits flat in `Server/Item/Items/` and still resolves `"Parent": "Weapon_Longsword_Crude"` even
  though that vanilla item itself lives nested under `Weapon/Longsword/`.
- **RootInteractions** (standalone, named interaction assets a JSON string ref can point at) live
  under `Server/Item/RootInteractions/**`.
- **Display text** (`TranslationProperties.Name`/`.Description`, `InteractionHint`, ...) is a key
  you invent, resolved from your OWN pack's `.lang` file under
  `Server/Languages/<bcp47>/<filename>.lang`. Hytale's `I18nModule` prepends the lang file's own
  name (minus `.lang`) as the key's prefix, so a bare key `items.MyItem.name` written inside
  `Server/Languages/en-US/mypack.lang` resolves as the full key `mypack.items.MyItem.name` (which
  is exactly what an item's `TranslationProperties.Name` must equal). An unauthored key just shows
  the raw key string as the in-game name.

### Example 1: a consumable that grants an item on eat

File: `Server/Item/Items/MyPack_LuckyMeal.json`. A custom consumable item modeled on the vanilla
food shape, with an inline `RunCommand` step in its consume chain:

```json
{
  "TranslationProperties": {
    "Name": "mypack.items.MyPack_LuckyMeal.name",
    "Description": "mypack.items.MyPack_LuckyMeal.description"
  },
  "Parent": "Template_Food",
  "InteractionVars": {
    "Effect": {
      "Interactions": [
        {
          "Type": "ApplyEffect",
          "EffectId": "Food_Instant_Heal_T1"
        },
        {
          "Type": "RunCommand",
          "Commands": [
            "give {player} Food_Wildmeat_Cooked --quantity=2"
          ],
          "RunAs": "Server"
        }
      ]
    }
  },
  "MaxStack": 25
}
```

Note: the two `TranslationProperties` keys above must be authored in your pack's own lang file,
`Server/Languages/en-US/mypack.lang` (bare keys, `key = value`; see "Where files go" above for why
the `mypack.` prefix comes from this exact filename):

```
items.MyPack_LuckyMeal.name = Lucky Meal
items.MyPack_LuckyMeal.description = A hearty meal that always leaves you feeling lucky.
```

Without those two lines, the item shows the raw key `mypack.items.MyPack_LuckyMeal.name` as its
in-game name instead of "Lucky Meal".

Note: this item has no `Interactions` block of its own, so `Secondary` INHERITS `Template_Food`'s
own `"Interactions": {"Secondary": "Root_Secondary_Consume_Food_T1"}` unchanged (the same
inherit-on-omit shape `Food_Wildmeat_Cooked.json` itself uses over that same template). Overriding
`Secondary` here with an id your pack never defines resolves to the engine's unknown-root stub, so
the item becomes uneatable and the `InteractionVars.Effect` override (and this example's whole
`RunCommand` step) is never reached; only override `Secondary` if you are pointing it at a real,
authored RootInteraction.

Note: Hytale's `/give` only honors the **named** `--quantity=N` form; a positional quantity is
ignored and delivers a single item, so always write it as `--quantity=N` in a `RunCommand` step.

### Example 2: a proc added to a native weapon via a pack override

A **native override** replaces part of an existing vanilla asset by shipping a pack file at the
EXACT SAME filename/key the vanilla asset uses, with a top-level `"Parent": "super"`: the special
value Hytale's asset loader (`AssetStore`) resolves as "the version of this same key from a pack
loaded below yours" (the vanilla base, when nothing else overrides it first). Your file then
inherits everything from that base and only the fields you actually write are replaced.

File: `Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Crude.json` (the SAME filename the
native Longsword item itself uses; this exact filename is what makes the file an override instead
of a new item, mirroring vanilla's own directory here purely by convention, see "Where files go"
above):

Note: an `InteractionVars` KEY is replaced WHOLESALE by your override, not deep-merged with the
vanilla item's own tuned entry for that same key. Crude's native `Weapon_Longsword_Crude.json`
already tunes its own `Longsword_Swing_Left_Damage` var (`DamageCalculator.Type: "Absolute"`,
`BaseDamage.Physical: 8`, `RandomPercentageModifier: 0.15`) above the untuned base interaction
(`Physical: 5`, no `Type`/modifier). If your override's `Longsword_Swing_Left_Damage` entry only
adds the `RunCommand` step and a bare `{"Parent": "Longsword_Swing_Left_Damage"}` for the first
entry, it silently REVERTS the Crude longsword's left-swing damage to that untuned base (Physical
5) instead of keeping the tuned Physical 8; you must restate Crude's own `DamageCalculator`/
`DamageEffects` verbatim alongside the added step, exactly as the shipped example pack does
(`examples/InteractionCommandsExamples/Server/Item/Items/Example_Command_Blade.json`):

```json
{
  "Parent": "super",
  "InteractionVars": {
    "Longsword_Swing_Left_Damage": {
      "Interactions": [
        {
          "Parent": "Longsword_Swing_Left_Damage",
          "DamageCalculator": {
            "Type": "Absolute",
            "BaseDamage": {
              "Physical": 8
            },
            "RandomPercentageModifier": 0.15
          },
          "DamageEffects": {
            "WorldSoundEventId": "SFX_Longsword_Special_Impact"
          }
        },
        {
          "Type": "RunCommand",
          "Commands": [
            "say {target} was struck by a cursed blade!"
          ],
          "RunAs": "Server",
          "Chance": 0.2,
          "Cooldown": 8
        }
      ]
    }
  }
}
```

This proc has a 20% chance to fire per hit, at most once every 8 seconds per player, on top of
Crude's own tuned left-swing damage (Physical 8, restated above so the override doesn't quietly
weaken the weapon). `{target}` resolves to the struck entity's name only when it's a player;
against a mob, that command is silently skipped (leaving any other commands in the list to run
normally) because `{target}` would otherwise remain unresolved.

**Variant-item alternative:** if you want a brand new, separately-obtainable weapon instead of
changing the one every player already has, ship the file under a DIFFERENT filename (e.g.
`Server/Item/Items/MyPack_Cursed_Longsword.json`) with `"Parent": "Weapon_Longsword_Crude"` (the
vanilla item's real id, not `"super"`); that derives a new item rather than overriding the native
one.

**Getting this wrong:** any filename other than `Weapon_Longsword_Crude.json` (wherever it sits
under `Server/Item/Items/**`) creates a SEPARATE derived item and leaves the native Longsword
completely untouched (a Hytale asset's id is its FILENAME, not its directory). And writing
`"Parent": "Weapon_Longsword_Crude"` inside a file that is ITSELF named
`Weapon_Longsword_Crude.json` makes the asset wait on its own key as its parent, which can never
resolve; the server logs a `SEVERE` "Failed to find parent" error for it at load and the override
silently fails to load at all.

### Example 3: a permission-gated command from a custom block

Files: `Server/Item/RootInteractions/MyPack_Arena_Warp.json` (the standalone RootInteraction) plus
`Server/Item/Items/MyPack_Arena_Beacon.json` (a placeable BLOCK the player places and presses F on;
nothing fires a RootInteraction that no block or item points at). A custom ITEM's own top-level
`"Interactions": {"Use": ...}` decodes, but has zero vanilla precedent and no confirmed client
dispatch path; every one of the shared source's native `Use` authors hangs it off a **block's**
`BlockType.Interactions.Use` instead, so this example is modeled on the monorepo's
in-game-validated Bounty Board block
(`bounty-contracts-pack/Server/Item/Items/MMO_Bounty_Board.json` in the MMOSkillBountyPack content
pack): a wall-poster-shaped block reusing a vanilla model and texture (so this example needs no
custom art of its own), with `BlockType.Interactions.Use` pointing at the RootInteraction. On 0.6,
`Interactions.Use` alone is what makes a placed block usable; the old `Flags.IsUsable` flag is
retired, and a file still carrying it just logs an unused-key warning per asset in the server log.
Both run a command as the player themself so Hytale's own permission system enforces the gate:

`Server/Item/RootInteractions/MyPack_Arena_Warp.json`:

```json
{
  "Interactions": [
    {
      "Type": "RunCommand",
      "Commands": [
        "warp arena"
      ],
      "RunAs": "Player",
      "Permission": "mypack.arena.enter"
    }
  ]
}
```

`Server/Item/Items/MyPack_Arena_Beacon.json` (a placeable block; `Interactions.Primary`/
`Secondary` point at the native block-break/-place interactions every placeable block needs,
`BlockType.Interactions.Use` is what makes a press-F on the placed block fire the RootInteraction
above):

```json
{
  "TranslationProperties": {
    "Name": "mypack.items.MyPack_Arena_Beacon.name",
    "Description": "mypack.items.MyPack_Arena_Beacon.description"
  },
  "PlayerAnimationsId": "Block",
  "Categories": ["Furniture.Signs"],
  "Icon": "Icons/ItemsGenerated/Lobby_Wall_Poster01.png",
  "MaxStack": 25,
  "IconProperties": {
    "Rotation": [22.5, 45, 22.5],
    "Scale": 1.1,
    "Translation": [11.0, -19.0]
  },
  "Interactions": {
    "Primary": "Block_Primary",
    "Secondary": "Block_Secondary"
  },
  "BlockType": {
    "BlockParticleSetId": "Dust",
    "BlockSoundSetId": "Cloth",
    "PhysicalMaterialId": "Wool",
    "CustomModel": "Blocks/Hypixel/Lobby/Poster_Wall.blockymodel",
    "CustomModelTexture": [
      { "Texture": "Blocks/Hypixel/Lobby/Poster_Wall_01.png", "Weight": 1 }
    ],
    "DrawType": "Model",
    "Gathering": {
      "Soft": { "IsWeaponBreakable": false }
    },
    "HitboxType": "Sign_Wall",
    "Material": "Solid",
    "Opacity": "Transparent",
    "ParticleColor": "#b58b4c",
    "Support": {
      "North": [ { "FaceType": "Full" } ]
    },
    "VariantRotation": "NESW",
    "InteractionHint": "mypack.items.MyPack_Arena_Beacon.hint",
    "Interactions": {
      "Use": "MyPack_Arena_Warp"
    },
    "TextureComputedColor": "#b58b4c"
  },
  "Tags": {
    "Type": ["Furniture"],
    "Family": ["MyPack"]
  },
  "ItemSoundSetId": "ISS_Items_Paper"
}
```

Note: the two `TranslationProperties` keys plus the `InteractionHint` key above need matching
lines in your pack's `Server/Languages/en-US/mypack.lang` (see Example 1 for the pattern).

A player without `mypack.arena.enter` who places the beacon and presses F on it gets a silent skip
(nothing happens, no error); a player who holds the node runs `/warp arena` as themself, so any
permission or cooldown logic the `warp` command itself enforces still applies on top.

Note: `mypack.arena.enter` is checked against Hytale's own permission resolution, and **an unset
node resolves to a deny by default**; being the server OP/admin does not satisfy this gate on its
own (see "Permission defaults to deny" above) - grant the node explicitly to the player or a group
they belong to.

## Build from source

Java 25, built via Gradle. Set `HYTALE_HOME` to your Hytale install directory (the folder
containing `<patchline>/package/game/...`), or edit `hytaleHome` in `gradle.properties`; the build
fails fast with a clear message if it can't find `HytaleServer.jar` either way.

```powershell
cd hytale-command-interactions   # this repo's root (additional-mods/command-interactions in hyMMO)
.\build.ps1                  # build the jar, install it if a Mods folder is known
.\build.ps1 -Install:$false  # build only
```

Or directly: `.\gradlew.bat build`. See [CLAUDE.md](CLAUDE.md) for the developer guide.

## License

MIT. See [LICENSE](LICENSE). Not affiliated with Hypixel Studios or Hytale.
