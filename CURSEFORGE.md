# Command Interactions

**Run commands from inside any interaction chain: a weapon proc, a consumable, a button, a block.**

Command Interactions adds one custom interaction Type, `RunCommand`, that you author inline anywhere a native Hytale interaction chain accepts a step. A weapon swing that fires a command on a lucky hit, a consumable that grants a bonus item on eat, a custom block that runs a permission-gated warp when pressed: if it's built out of the same interaction chains Hytale's own items and abilities use, you can drop a command into it. Zero dependencies, one jar.

> v1.1.0. The `RunCommand` interaction Type: server or player-authority execution, per-player
> cooldowns, chance-gated procs, permission gates, and full placeholder substitution
> (`{player}`, `{target}`, position, world, held item). Built for Hytale Update 6 (0.6.x).

**Source & full docs:** [github.com/arfemia/hytale-command-interactions](https://github.com/arfemia/hytale-command-interactions) -
**example pack (ready to drop in):** [InteractionCommandsExamples.zip](https://github.com/arfemia/hytale-command-interactions/raw/main/examples/InteractionCommandsExamples.zip)

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/5NFdZsUxHZ) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/ziggfreed)

![Command Interactions - run commands from any Hytale interaction chain](https://github.com/arfemia/hytale-command-interactions/raw/main/assets/banner.gif)

---

[![Host your own Hytale server with Kinetic Hosting](https://i.imgur.com/UHn3FzW.png)](https://billing.kinetichosting.com/aff.php?aff=1262)

---

## Features

- **Weapon procs that run commands.** Add a `RunCommand` step to any weapon's swing/hit chain
  (native or your own), gated by `Chance` and `Cooldown`, so a strike can fire a message, a
  reward, or anything else a command can do.
- Consumables too: chain a `RunCommand` step into a food or potion's consume effect alongside
  native steps like `ApplyEffect`, so eating something can also grant an item, announce a bonus,
  or trigger anything server-side.
- Buttons and blocks: author a standalone RootInteraction that a custom usable item or block
  references, gating the command behind a `Permission` node and running it as the player so their
  own permissions decide the outcome.
- Server or player authority, your choice. `RunAs: "Server"` runs with full console authority
  (the default); `RunAs: "Player"` runs as the interacting player themself, so the game's own
  permission checks apply on top.
- Placeholders built in: `{player}`, `{uuid}`, `{x}`/`{y}`/`{z}`, `{world}`, `{target}`,
  `{targetUuid}`, `{item}`, substituted at fire time. A command left with an unresolvable
  placeholder (like `{target}` with no target) is skipped on its own, never breaking the rest of
  the list.
- **Never breaks the chain.** A cooldown, a chance miss, or a missing permission is a silent
  skip, not a failure: the step no-ops and everything around it (the weapon swing, the item
  consumption) proceeds exactly as normal.
- **Zero dependencies.** No companion mod, no library jar, no content pack required. It ships no
  assets and no lang files of its own; it is a pure engine addition you author against in your own
  pack.

## Install

1. Drop `InteractionCommands-<version>.jar` into your server's `Mods/` folder.
2. Requires a Hytale Update 6 (0.6.x) server (`>=0.6.0-pre.13 <0.7.0`).
3. No other dependency. Restart the server and you're done; `RunCommand` is now available to
   author in your own asset pack (which needs `"IncludesAssetPack": true` in its `manifest.json`,
   as with any pack authoring native content).

## Worked examples

All three examples belong in **your own asset pack**; InteractionCommands ships no content of its
own.

### Try the example pack

Want to see `RunCommand` fire without writing any JSON yourself first? Download
[`InteractionCommandsExamples.zip`](https://github.com/arfemia/hytale-command-interactions/raw/main/examples/InteractionCommandsExamples.zip)
(source in the repository's
[`examples/` folder](https://github.com/arfemia/hytale-command-interactions/tree/main/examples)),
the reference implementation of the three worked examples below.

1. Drop the mod jar into `Mods/`, then drop `InteractionCommandsExamples.zip` in alongside it.
   Restart the server.
2. `/give Example_Command_Blade --quantity=1`, `/give Example_Command_Potion --quantity=1`,
   `/give Example_Command_Meal --quantity=1`.
3. Swing the blade (25% chance per landed hit, 5-second cooldown) for a free cooked wildmeat.
   Drink the potion for four wall torches on top of its native stamina buff. Eat the meal for a
   free Command Potion, chaining straight into the potion example.

### Where files go

A Hytale asset's id IS its file path key, but that key is the FILENAME alone (PascalCase, no
extension); the directory a file sits in is a tidiness convention, not part of its identity
(Hytale's `AssetStore` derives the key from `path.getFileName()`, ignoring the rest of the path).
What you name a file decides whether it's a new asset or an override of an existing one;
only the FILENAME has to match. Items live under `Server/Item/Items/**` (an override of a native
item needs only that item's own exact filename, anywhere under `Items/**`; mirroring vanilla's own
subdirectory is a convention, not a requirement); RootInteractions live under
`Server/Item/RootInteractions/**`; display text keys resolve from your
pack's own `.lang` file under `Server/Languages/<bcp47>/<filename>.lang` (Hytale's `I18nModule`
prepends the lang file's own name as the key prefix, e.g. a bare key `items.MyItem.name` inside
`mypack.lang` resolves as `mypack.items.MyItem.name`; an unauthored key just shows the raw key).

### A consumable that grants a bonus item

File: `Server/Item/Items/MyPack_LuckyMeal.json`.

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
        { "Type": "ApplyEffect", "EffectId": "Food_Instant_Heal_T1" },
        {
          "Type": "RunCommand",
          "Commands": ["give {player} Food_Wildmeat_Cooked --quantity=2"],
          "RunAs": "Server"
        }
      ]
    }
  }
}
```

The two `TranslationProperties` keys need matching lines in `Server/Languages/en-US/mypack.lang`
(`items.MyPack_LuckyMeal.name = Lucky Meal`, plus a `.description` line), or the item shows the raw
key as its name. (Note: Hytale's `/give` only honors the named `--quantity=N` form; a positional
quantity delivers just one item.)

### A weapon proc on a native weapon, via a pack override

A **native override** ships a pack file at the vanilla item's OWN filename with a top-level
`"Parent": "super"` (the engine's own "inherit from whatever loaded this key below me" value), so
it inherits everything and only the fields you write are replaced.

File: `Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Crude.json` (the vanilla item's exact
FILENAME is what matters, not the directory; any other filename creates a separate new item
instead and leaves the native weapon untouched).

**Restate the tuned var, don't just add to it.** An `InteractionVars` KEY is replaced WHOLESALE by
your override, never deep-merged with the vanilla item's own tuned entry for that key. Crude's own
`Longsword_Swing_Left_Damage` var already tunes `DamageCalculator`/`DamageEffects` above the
untuned base interaction (`Physical: 8`, `Type: "Absolute"`, `RandomPercentageModifier: 0.15`, vs.
the base's `Physical: 5`), so an override that writes only a bare
`{"Parent": "Longsword_Swing_Left_Damage"}` plus the new step silently reverts the sword's damage
to that untuned Physical 5. Restate Crude's own tuned fields verbatim, exactly as the shipped
example pack does
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
            "BaseDamage": { "Physical": 8 },
            "RandomPercentageModifier": 0.15
          },
          "DamageEffects": {
            "WorldSoundEventId": "SFX_Longsword_Special_Impact"
          }
        },
        {
          "Type": "RunCommand",
          "Commands": ["say {target} was struck by a cursed blade!"],
          "RunAs": "Server",
          "Chance": 0.2,
          "Cooldown": 8
        }
      ]
    }
  }
}
```

20% chance per hit, 8-second per-player cooldown, on top of Crude's own tuned damage (Physical 8,
restated above). Against a mob (no player target), `{target}` can't resolve, so that command is
silently skipped; a player target gets the message. (Want a brand new item instead of overriding
the native one? Use a different filename with `"Parent": "Weapon_Longsword_Crude"` instead of
`"super"`.)

### A permission-gated command from a custom block

Files: `Server/Item/RootInteractions/MyPack_Arena_Warp.json` (below) plus a placeable BLOCK that
references it, e.g. `Server/Item/Items/MyPack_Arena_Beacon.json` with
`BlockType.Interactions.Use: "MyPack_Arena_Warp"` (a RootInteraction nothing points at never
fires). On 0.6, `Interactions.Use` alone is what makes a placed block usable; the old
`Flags.IsUsable` flag is retired, and a file still carrying it just logs an unused-key warning in
the server log. A custom ITEM's own top-level `"Interactions": {"Use": ...}` decodes but has no
confirmed client dispatch path; every native `Use` hangs off a **block's**
`BlockType.Interactions.Use` instead, so model yours on the in-game-validated
`MMO_Bounty_Board.json` block in the MMOSkillBountyPack content pack (a wall-poster-shaped block
reusing a vanilla model/texture, its `Use` pointing at a RootInteraction).

```json
{
  "Interactions": [
    {
      "Type": "RunCommand",
      "Commands": ["warp arena"],
      "RunAs": "Player",
      "Permission": "mypack.arena.enter"
    }
  ]
}
```

Placing the beacon and pressing F runs `/warp arena` as the player themself, only if they hold
`mypack.arena.enter`; otherwise it's a silent skip. **An unset permission node resolves to a deny by
default**; being server OP/admin does not satisfy this gate on its own, the node must be explicitly
granted.

## Troubleshooting

Nothing happening when your `RunCommand` step fires? Every gate miss is a silent skip by design
(no chat message, no error), checked in this order: (1) no player on the firing entity, (2) a
`Permission` the player doesn't hold (an unset node **defaults to deny**; server OP/admin status
does NOT satisfy it on its own), (3) a `Chance` roll that missed, (4) every command resolving
with an unresolved placeholder (most commonly `{target}` with no target entity, or `{x}`/`{y}`/
`{z}` with no position), (5) `Cooldown` still active for that player on that step, (6) an earlier
command in the list threw a genuine error, which aborts the rest of that fire's list (logged as a
`warn`; a merely-unknown command does NOT abort). Commands in `Commands` always dispatch strictly
in the order you wrote them, one starting only after the previous finishes. Raise your server's
log level to `FINE` to see the unresolved-placeholder skip line. Note that `RunAs: "Server"` (the
default) sends all command output and errors to the server console, never to the player; use
`RunAs: "Player"` if you need player-visible feedback. Full detail: the Troubleshooting section of
[`README.md`](https://github.com/arfemia/hytale-command-interactions/blob/main/README.md) in the
mod's repository.

Full field-by-field schema reference, gate semantics, and placeholder table:
[`README.md`](https://github.com/arfemia/hytale-command-interactions/blob/main/README.md) in the
mod's repository.

## Versions

| Version | Notes                                                                                                                                                                                                                         |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.1.0   | Hytale Update 6 (0.6.x) build (`>=0.6.0-pre.13 <0.7.0`), no behavior change. The block example drops the retired usable-block flag: a block's `Interactions.Use` alone drives usability on 0.6, and a leftover flag just logs an unused-key warning. |
| 1.0.0   | First release. The `RunCommand` interaction Type: `Commands`/`RunAs`/`Cooldown`/`Chance`/`Permission`, full placeholder substitution, silent-skip gate semantics, unresolved-placeholder per-command skip. Zero dependencies. |

---

_InteractionCommands is not affiliated with Hypixel Studios or Hytale. Licensed under the MIT
License; see [LICENSE](https://github.com/arfemia/hytale-command-interactions/blob/main/LICENSE)._
