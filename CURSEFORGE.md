# Command Interactions

**Adds a way to run server commands from Hytale's own interactions: a weapon hit, a meal, a potion, or pressing F on a block.**

It adds one new interaction step, `RunCommand`, that you drop into any item or block your asset pack defines. Give a sword a 20% chance to run a command on hit, have a meal hand out a bonus item, or let a block warp players who hold a permission. One jar, with no other mods needed.

It's a tool for server owners and pack authors, built for Hytale Update 7 (0.7.x). A server still on Update 6 stays on 1.1.0.

**Source & full docs:** [github.com/arfemia/hytale-command-interactions](https://github.com/arfemia/hytale-command-interactions)

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/5NFdZsUxHZ) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/ziggfreed)

![Command Interactions - run commands from any Hytale interaction chain](https://github.com/arfemia/hytale-command-interactions/raw/main/assets/banner.gif)

---

[![Host your own Hytale server with Kinetic Hosting](https://i.imgur.com/UHn3FzW.png)](https://billing.kinetichosting.com/aff.php?aff=1262)

---

## Try the example pack

1. Drop the mod jar and [InteractionCommandsExamples.zip](https://github.com/arfemia/hytale-command-interactions/raw/main/examples/InteractionCommandsExamples.zip) into your server's `Mods/` folder, then restart.
2. Give yourself the three example items: `/give Example_Command_Blade --quantity=1`, `/give Example_Command_Potion --quantity=1` and `/give Example_Command_Meal --quantity=1`.
3. Hit something with the blade. A quarter of your hits hand you cooked wildmeat, at most once every 5 seconds. Drink the potion for four wall torches, and eat the meal for another potion.

The pack's files are in the repository's [`examples/` folder](https://github.com/arfemia/hytale-command-interactions/tree/main/examples), ready to copy from.

## How it works

Add a `RunCommand` step to any interaction list in your own asset pack:

```json
{
  "Type": "RunCommand",
  "Commands": ["give {player} Food_Wildmeat_Cooked --quantity=2"],
  "RunAs": "Server",
  "Chance": 0.2,
  "Cooldown": 8,
  "Permission": "mypack.bonus"
}
```

- `Commands` run in the order you write them.
- `RunAs` is `"Server"` (the default, with full console rights) or `"Player"`, which runs the command as the player, with only their permissions.
- `Chance` (0 to 1), `Cooldown` (seconds, per player) and `Permission` are optional.
- Placeholders: `{player}`, `{uuid}`, `{x}`, `{y}`, `{z}`, `{world}`, `{target}`, `{targetUuid}` and `{item}`.

Your pack needs `"IncludesAssetPack": true` in its `manifest.json`, like any pack that adds items.

## Good to know

- A miss never breaks anything. A failed chance roll, a cooldown or a missing permission skips the step quietly, and the swing or the meal goes on as normal. So does a command whose placeholder can't be filled, like `{target}` when you hit a mob.
- An unset permission node means no, even for OPs. Grant the node.
- With `RunAs: "Server"`, a command's output and errors go to the server console, not to the player.
- On Update 7, a held item's `Ability2` and `Ability3` go to your runes, so a `RunCommand` step there never runs. Put it on `Primary` or `Secondary`.
- A placed block runs your step when pressed if its `BlockType.Interactions.Use` names the interaction.

The [README](https://github.com/arfemia/hytale-command-interactions/blob/main/README.md) has the full field reference, more examples (a proc on a vanilla weapon among them) and troubleshooting.

## Install

1. Drop `InteractionCommands-<version>.jar` into your server's `Mods/` folder.
2. Restart the server. `RunCommand` is ready to use in your own asset pack.

Patch notes for every version are in [`patch-notes/`](https://github.com/arfemia/hytale-command-interactions/tree/main/patch-notes).

---

_InteractionCommands is not affiliated with Hypixel Studios or Hytale. Licensed under the MIT
License; see [LICENSE](https://github.com/arfemia/hytale-command-interactions/blob/main/LICENSE)._
