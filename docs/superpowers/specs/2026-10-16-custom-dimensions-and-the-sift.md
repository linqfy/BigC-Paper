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

8. **Substitutes where a port is impossible.** At the user's request, mod features that a
   vanilla client or server cannot show are faked rather than left out: non-cube blocks are
   content items placing vanilla look-alikes (positions remembered per chunk so they drop the
   mod's items), the mod's music discs are vanilla discs with the mod's icons, custom block
   random ticks are sampled by a bounded task, and client glows are BetterModel bone
   brightness plus moving light blocks. Each substitute is labelled as such in the guide.
9. **Content items stay out of vanilla recipes.** Content items reuse vanilla materials, so a
   guard lets recipes, anvils, crafters, smithing tables and cooking blocks take them only
   through exact choices.

## Validation performed

JUnit tests for the compiler, installer, store, cycles, travel and portal
detection, every ported feature (determinism, region bounds, output on prepared
terrain), the loot tables, the ritual melody, the block rendering contract, the
creatures' Bukkit-free brains, spawners and timings, the main portal's site search and
build plan, recipes (all 76 translate), advancements (all 22 written) and the
substitutes; the generated pack was checked against 26.2 registries and block states.
Before the live runs, the suite ran offline with substituted dependencies (33 failures from the
substitution itself).

The Sift was then played on a live Paper 26.2 test server by a bot. Install, generation, creatures
and models, the ritual, all travel paths, rifts, look-alike blocks, a recipe, Ichor, Siftite
recovery and moving lights all worked. The official 26.2 client then joined on a virtual display. It showed the terrain, portals,
creatures and rifts, and played the Sift sounds (captured to a file). Eight bots in the Sift
for 85 minutes, and a paired comparison with the Overworld, showed the Sift's cost to be well
below vanilla terrain. The live runs and the client found these bugs, all fixed:
- a broken Dark Sniffer model cube;
- 26.3-only advancement conditions;
- the main portal's state key;
- Echo Golem navigation;
- roof-preferring arrivals;
- occupied rift spots and unheld chunks in the exit-rift search;
- sculk regions and Ichor snow reading a stale populator heightmap;
- 2x2 crafts in the recipe guard;
- invisible rifts;
- a 26.3-only sound event;
- the pack's missing format range;
- APIs newer than the pinned Paper API. The project's own `clean build` passes with every test green. What was and
was not verified is listed under Verification status in `docs/dimensions/the-sift.md`.
