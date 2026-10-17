# Custom dimension framework and The Sift (2026-10-16)

Design record for the `dimension-framework`, `custom-content` and `the-sift`
modules. Current behaviour is documented in [the dimension guide](../../dimensions/README.md)
and [The Sift](../../dimensions/the-sift.md); verify claims against source/tests.

## Decisions

1. **Datapack-native dimensions, not a plugin chunk generator.** Paper 26.2 loads
   datapack `LevelStem`s as real worlds, so terrain, biomes, carvers, features,
   spawning, environment attributes and timelines use vanilla code and sync to
   unmodified clients. Cost: worldgen changes need a restart. `WorldCreator` only
   supports NORMAL/NETHER/END and would lose custom dimension types.
2. **Code-defined definitions compiled deterministically.** A Java DSL builds
   registry JSON; the compiler checks every internal reference and produces a
   reproducible pack and content hash. The installer only rewrites on hash change
   and keeps backups.
3. **Seed salt through noise offsets.** Dimensions share the level seed; salting the
   sampled noises varies terrain without forking biome or feature data.
4. **Java features as block populators.** Mod features with no data equivalent run
   in Paper's decoration step with a `LimitedRegion`. They must be stateless and
   deterministic; the mod's global, generation-order-dependent reservations
   (`SiftFeaturePlacementGuard`, `SiftLandmarkTracker`) were deliberately dropped.
5. **Custom blocks on mushroom-block states.** Reserved `down=true` states of the
   three mushroom blocks (93) carry full-cube custom blocks; generated multipart
   blockstates keep vanilla rendering for all other states. Requires Paper's
   `disable-mushroom-block-updates`. Non-cube blocks use vanilla stand-ins.
6. **World clocks for cycles.** Each dimension type can own a clock; phase logic
   reads the clock and timelines drive client visuals, so server state and visuals
   cannot drift.
7. **The Sift as a port.** The user supplied Mielon's mod (MIT, author's
   permission). Data and art are imported by script, 26.3 data is translated to
   26.2, GeckoLib models are converted for BetterModel, and feature code is ported
   line by line. Creatures are vanilla mobs with matching AI (the Blub is a wolf).
   The Ancient City ritual reuses the mod's centre templates via the datapack.

## Validation performed

JUnit tests for the compiler, installer, store, cycles, travel and portal
detection, every ported feature (determinism, region bounds, output on prepared
terrain), the loot table, the ritual melody and the block rendering contract; the
generated pack was checked against 26.2 registries and block states. No live
server was available in the development sandbox (Paper and Mojang hosts blocked);
live verification is still outstanding.
