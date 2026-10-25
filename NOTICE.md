# References and attribution

This project is an independent Forge 1.12.2 client mod.

Its workflow and Java-RNG recovery method are based on Earthcomputer's
[EnchantmentCracker](https://github.com/Earthcomputer/EnchantmentCracker) and
[clientcommands](https://github.com/Earthcomputer/clientcommands), both
published under the MIT License. The reference application explicitly uses
one ruleset for Minecraft 1.11.1 through 1.12.2.

Enchant generation is not maintained as a hand-written table here. Predictions
call Minecraft 1.12.2's own `EnchantmentHelper` implementation, matching the
vanilla `ContainerEnchantment` call order.

The fishing feature follows the UUID-recovery and timed-retrieval idea from
Earthcomputer's original clientcommands fishing work. Its simulation here
models the supplied Paper 1.12.2 source, including the older water-splash
float draws and bite-window tug draws. It does not copy the 1.16.4 simulator.
Fishing loot is evaluated with Minecraft 1.12.2's native loot/enchantment
classes and explicit vanilla fishing table entries. The research handoff is
`Fishing-RNG-Paper-1620-Research.md` (28 September 2026).

Reference: https://github.com/Earthcomputer/clientcommands/blob/3a0807b8386f700f8180573b85ecfa6d4a53cd27/src/main/java/net/earthcomputer/clientcommands/features/FishingCracker.java
