# Enchant Cracker + Fishing

A client-side **Minecraft 1.12.2 / Forge** mod for revealing enchanting-table outcomes, targeting enchantments through player RNG manipulation, and automatically fishing for a chosen item after one manual cast.

## Install

1. Use Minecraft **1.12.2**, **Java 8**, and **Forge 14.23.5.2859**.
2. Download `enchantcracker+fishing-forge-1.12.2-1.4.2.jar` from this repository's Releases.
3. Put it in your Minecraft profile's `mods` folder, remove any older copy of this mod, and restart the game.

**Server compatibility:** enchanting manipulation requires vanilla per-player RNG. Stock Paper's shared player RNG is incompatible. Targeted fishing was designed for the accompanying custom `paper-1.12.2-rng-transfer-fishing.jar` server build, which is **not included** in this repository or release. These settings alone do not replace that server patch:

```yaml
settings:
  use-shared-random-for-players: false
  use-alternative-luck-formula: false
world-settings:
  default:
    fishing-time-range:
      MinimumTicks: 100
      MaximumTicks: 600
```

## Enchanting

Open an enchanting table and insert an enchantable item. If the overlay needs more observations, try another item or change the bookshelf count. Once the seed is cracked, the overlay shows the enchantments for all three slots. A successful enchant changes the seed.

To target a particular result:

1. Run `/enchcracker rng`. Perform a cheap enchant, then provide table observations until the new seed is cracked. Repeat once, following the chat prompts.
2. Choose a target, for example:

   ```text
   /enchcracker target diamond_pickaxe efficiency 4 unbreaking 3 fortune 3
   ```

3. Wait for a plan. If it requires drops, select disposable items in your hotbar, run `/enchcracker execute`, and close all screens. This **drops real items** and can use matching stacks across your hotbar.
4. Follow the prompt for a cheap dummy enchant, then use the displayed bookshelf count and slot for your target. If the current seed already matches, use the displayed result directly.

Avoid manual drops and other actions that change player RNG while calibrating or executing. If the hotbar runs out, refill it with matching items and run `/enchcracker execute` again. Reset and recalibrate after a desync.

| Command | Usage |
| --- | --- |
| `/enchcracker` or `/enchcracker status` | Show the current seed, search, or execution state. |
| `/enchcracker rng` | Start player RNG calibration. |
| `/enchcracker target <item> <enchantment> [level] [<enchantment> <level> ...]` | Search for a result matching all requested enchantments. A single enchantment defaults to level 1; multiple enchantments need explicit levels. |
| `/enchcracker execute` | Start or resume the planned item drops. |
| `/enchcracker pause` | Pause item drops. |
| `/enchcracker cancel` | Cancel the search or plan. Cancelling execution also clears RNG calibration. |
| `/enchcracker reset` | Clear the seed, calibration, and plan. |

Extra enchantments are allowed; requested levels are minimums. Table-incompatible combinations and treasure enchantments such as Mending cannot be obtained through the enchanting target command. Use Tab completion for item and enchantment names.

## Automatic fishing

For a Mending book:

```text
/fishpredict target enchanted_book mending 1
```

Hold a fishing rod, aim into clear water, wait one second, and cast **once**. Keep your position, aim, rod, hand, and hotbar slot unchanged. Close menus and keep Minecraft focused. The mod reels and recasts automatically, then stops at **TARGET COLLECTED** after confirming pickup. Leave inventory space and enough rod durability.

A single source-water block can work; the cast must reach it without hitting a block or entity. Ordinary pool floors and walls are supported. For an unenchanted target, use a command such as `/fishpredict target name_tag`. Multiple enchantment/level pairs must match the same catch; extra enchantments are allowed.

| Command | Usage |
| --- | --- |
| `/fishpredict target <item> [<enchantment> <level> ...]` | Choose a target and wait for one manual cast. Use `enchanted_book` for books. |
| `/fishpredict` or `/fishpredict status` | Show progress and timing information. |
| `/fishpredict cancel` or `/fishpredict stop` | Stop automatic rod use. Does not retrieve an existing hook. |
| `/fishpredict resume` or `/fishpredict arm` | Restart the saved target. Reel in an existing hook first, then cast once. |
| `/fishpredict clear` | Stop and remove the target. |
| `/fishpredict speed <fast\|normal>` | Set recast speed. `fast` is the default; omit the mode to show it. |
| `/fishpredict on` | Enable prediction preview without automatic rod use. |
| `/fishpredict off` | Stop and hide the fishing panel. |
| `/fishpredict list` | List up to 12 matching predicted catches for the current cast. |
| `/fishpredict offset <milliseconds>` | Adjust retrieval timing from -500 to 500 ms; positive means later. Default: 0. |
| `/fishpredict ping <auto\|milliseconds>` | Use automatic ping or a round-trip override from 0 to 1000 ms. Default: `auto`. |

Manual rod use also cancels an active run. You can bind **Cancel fishing predictor** in Controls. Opening menus or losing focus blocks automatic rod use; reconnecting resets the target and fishing settings. If paused, check `/fishpredict status`, correct the reported issue, and resume with a fresh cast. Custom fishing loot, server settings, or unstable timing can invalidate predictions.

## Source and build

The original Java source, tests, resources, and Gradle wrapper are included in `src/` and the project root. The source and build configuration are unchanged from the existing 1.4.2 project. The release uses the original supplied JAR.

With Java 8 selected, run `gradlew.bat test build` on Windows or `./gradlew test build` on Linux/macOS. The build downloads its dependencies and writes the production JAR to `build/libs/`.

MIT licensed; see [LICENSE.txt](LICENSE.txt). Based on work by Earthcomputer; see [NOTICE.md](NOTICE.md) for attribution.
