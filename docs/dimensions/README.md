# Custom dimensions

The dimension framework (`dev.linqfy.bigCasares.dimension`, module
`dimension-framework`) lets code define complete dimensions — terrain, biomes,
features, day cycles, rules, portals and runtime behaviour — and runs them on an
unmodified Paper 26.2 server for unmodified Java clients. [The Sift](the-sift.md)
is the first dimension built on it.

## How it works

A dimension is a `DimensionDefinition`. Registering it compiles every registered
definition into **one generated datapack**, which Paper loads as a real dimension
(`LevelStem`) with its own dimension type, noise settings, biomes, world clock and
timelines. Generation, lighting, mob spawning, fog and sky colours are therefore
vanilla's native code paths, not a plugin chunk generator.

| Part | Where it runs | Notes |
| --- | --- | --- |
| Dimension type, noise router, surface rules, biomes, carvers, configured and placed features, structures, world clock, timelines, tags | Datapack, server world generation | Registry data is synced to clients, so biome colours, fog, particles and sky render without a client mod |
| Features vanilla data cannot express | `DimensionExtension#populators` (Paper `BlockPopulator`) | Run in the native decoration step with a `LimitedRegion`: the chunk plus a one-chunk buffer, on world-generation threads |
| Phases, effects, creatures, rituals | `DimensionExtension` hooks and listeners | Owned by a registration scope and cleaned up on disable |
| Custom full-cube blocks | `custom-content` module (`CustomBlockRegistry`) | Backed by reserved mushroom-block states and rendered through generated blockstate overrides |
| Textures, models, sounds | Resource pack (`resourcepack/java/`) | Built and delivered by the resource pack module |

Worldgen registries are read only at startup, so a **new or changed datapack takes
effect after a restart**. Runtime hooks activate as soon as the world exists.

### Lifecycle

1. A module calls `DimensionPlatform#register(definition)` during enable.
2. The compiler builds the datapack in memory, verifies every internal reference
   (density functions, noises, features, carvers, biomes, structures, tags) and
   fails with a precise error instead of leaving a broken pack for the server.
3. The pack is always written to `plugins/BigCasares/dimensions/generated-datapack`.
   With `install-mode: auto` it is also installed to `<level>/datapacks/<folder>`;
   with `manual` an operator copies it there. Both copies are rewritten only when
   the content hash changed, keeping the last three versions in
   `plugins/BigCasares/dimensions/datapack-backups/`.
4. If the dimension's world is not loaded yet, its status is `PENDING_RESTART`.
   After the restart Paper creates the world (named `<level>_<namespace>_<path>`)
   and the status becomes `ACTIVE`: game rules, world border and cycles are
   applied, populators are attached and extensions get `onActivate`.
5. Disabling or unregistering runs `onDeactivate` and releases every listener,
   task and populator the dimension registered. The world and its files stay.

Statuses are `REGISTERED`, `PENDING_RESTART`, `ACTIVE`, `FAILED` and
`UNREGISTERED` (`/dimension list`, `DimensionPlatform#status`).

## Public API

Obtain the platform from the module or Bukkit's services manager:

```java
DimensionPlatform platform = Bukkit.getServicesManager().load(DimensionPlatform.class);
platform.register(MyDimension.create());
```

| API | Purpose |
| --- | --- |
| `DimensionPlatform` | Register/unregister, status, world lookup, cycle snapshots, `enter`/`teleport`/`leave`, `findPortalFrame`/`lightPortal`/`extinguishPortal` |
| `DimensionDefinition.builder(key)` | Type, terrain, biomes, structures, cycles, datapack contributors, rules, travel, extensions, seed salt |
| `DimensionTypeSpec` | Height range, light, skylight/ceiling, skybox, infiniburn, monster spawn light, environment attributes, own world clock |
| `worldgen.*` | Density-function DSL (`DensityFunctions`, `CubicSpline`), noises, `NoiseRouter`, `SurfaceRule(s)`, `Climate`, `BiomeDefinition`, `FeatureDefinition`, `PlacedFeatureDefinition`, `Placements`, `CarverDefinition`, `StructureDefinition` |
| `EnvironmentCycle` | Named phases on the dimension's world clock, with timeline tracks for client visuals and time markers |
| `DimensionRules` | Game rules, world border, spawn search radius, weather |
| `TravelRules`, `PortalDefinition`, `CoordinateMapping` | Entry permission, cooldown, return points, arrival search radius, portal frames |
| `DimensionExtension` | `onActivate`, `onDeactivate`, `populators`, `onPlayerEnter`/`Exit`, `onPhaseChange`, `onPulse` |
| Events | `DimensionLifecycleEvent`, `DimensionTravelEvent` (cancellable), `DimensionEnterEvent`, `DimensionExitEvent`, `DimensionPhaseChangeEvent`, `DimensionPortalActivateEvent` (cancellable) |

### Determinism and seeds

Minecraft gives every dimension of a level the same seed. A definition's
`seedSalt` offsets all noises it samples through `GenerationContext#noise`, so a
salt produces different terrain while biomes, features and the rest of the pack
stay byte-identical. Compilation is deterministic: the same definitions always
produce the same files and content hash (`gradlew generateSiftDatapack` writes the
Sift's pack to `build/generated-datapacks/` for inspection).

Populators receive a random derived from Paper's decoration seed. Features must be
stateless and thread-safe, may write only where the region allows, and must not
depend on what neighbouring chunks generated first (no global reservations).

### Environment cycles

A cycle is a list of phases on the dimension's own world clock (`ownClock`), so
`/time` and sleeping in other worlds do not move it. Timeline tracks change client
visuals (fog, sky, light) smoothly; `onPhaseChange` and `DimensionPhaseChangeEvent`
fire on the server when the clock crosses a phase boundary (checked once per
second, not per tick). `/dimension phase <key> <cycle> <phase>` moves the clock.

### Travel and portals

`PortalDefinition` describes a frame: frame blocks, interior size limits, an
activation item (or none for scripted portals), whether arriving builds a return
frame, the interior light level, an optional surface item model and an ambient
particle. Players light item portals by using the item on the frame. Scripted
portals (rituals, quests) are lit with `DimensionPlatform#lightPortal`, which fires
the same cancellable activation event. Lit portals are persisted and re-indexed on
start; breaking any frame or interior block extinguishes them.

Entering through a portal or `enter` maps coordinates with the definition's
`CoordinateMapping`, clamps them inside the target border, loads chunks
asynchronously, and searches for a safe spot (solid floor, two free blocks, no
hazards nearby). If none exists within the search radius, a small platform is
built. The departure location is stored as the player's return point. Travel is
denied during the cooldown, without the entry permission, or when the
`DimensionTravelEvent` is cancelled.

## Building another dimension

[`ExampleDimension`](../../src/test/java/dev/linqfy/bigCasares/dimension/ExampleDimension.java)
is a complete dimension built only with the public API; tests compile it, so it
always matches the current API:

```java
DimensionDefinition.builder(DimensionKey.of("example", "basalt_sea"))
    .displayName("Basalt Sea")
    .type(DimensionTypeSpec.builder().heightRange(0, 128).logicalHeight(128).skylight(true).build())
    .terrain(context -> {
        var hills = context.defineNoise("hills", -6, 1.0, 1.0, 0.5);
        DensityFunction density = DensityFunctions.add(
            DensityFunctions.yGradient(32, 96, 1.0, -1.0),
            DensityFunctions.mul(context.noise(hills, 1.0, 0.0), 0.45));
        NoiseRouter router = NoiseRouter.builder().finalDensity(DensityFunctions.interpolated(density)).build();
        SurfaceRule surface = new SurfaceRule.Sequence(List.of(
            new SurfaceRule.Conditional(SurfaceRules.onFloor(0),
                new SurfaceRule.Block(BlockStateSpec.of("minecraft:basalt", Map.of("axis", "y")))),
            new SurfaceRule.Block(BlockStateSpec.of("minecraft:blackstone"))));
        return new TerrainSpec(0, 128, 1, 2, 48, BlockStateSpec.of("minecraft:blackstone"),
            BlockStateSpec.of("minecraft:water", Map.of("level", "0")), false, false, false,
            router, surface, List.of(), Map.of());
    })
    .biomes(context -> List.of(BiomeDefinition.builder(context.id("ash_shore"))
        .climate(0.9F, 0.0F, false).waterColor(0x3F76E4)
        .placement(Climate.ParameterPoint.builder().build()).build()))
    .cycle(EnvironmentCycle.builder("embers").phase("calm", 12_000).phase("flare", 2_000).build())
    .build();
```

Steps for a new dimension:

1. Create a module (see `AGENTS.md`) with an explicit `modules.<id>.enabled`
   default, ordered after `dimension-framework` and `custom-content` in
   `BigCasares.initializeRuntime()`.
2. Build the definition. Use `context.id(...)` for ids so they live under the
   dimension's namespace, and `context.noise(...)` for salted noise.
3. Add a `DatapackContributor` for extra files (tags, processor lists, templates).
   Appending to `minecraft:` tags affects every dimension; keep such entries to
   blocks the dimension owns and document them.
4. Put features vanilla cannot express in `DimensionExtension#populators`, and
   runtime behaviour in the other hooks, registering listeners and tasks through
   `ActiveDimension#registrations()` so they are released on deactivation.
5. For custom blocks, register `CustomBlockDefinition`s in the custom block
   registry and run `gradlew generateCustomBlockstates`; a contract test fails if
   the committed blockstates drift from the registry.
6. Test with JUnit: compile the definition with `DatapackCompiler`, and test
   features with an in-memory region (see the Sift feature tests).
7. Restart the server once after the first install, then check `/dimension list`.

## Configuration, commands and permissions

```yaml
modules:
  dimension-framework:
    enabled: true
dimension-framework:
  datapack:
    install-mode: auto          # auto | manual
    folder: bigcasares-dimensions
  autosave-minutes: 5
```

| Command | Permission | Purpose |
| --- | --- | --- |
| `/dimension list`, `info <key>` | `bigcasares.dimension.admin` | Status, world, active phases |
| `/dimension tp <key> [player]` | `bigcasares.dimension.admin` | Send a player in |
| `/dimension leave` | `bigcasares.dimension.leave` | Return to the stored return point |
| `/dimension phase <key> <cycle> <phase>` | `bigcasares.dimension.admin` | Move a dimension's clock to a phase |
| `/dimension pack`, `export` | `bigcasares.dimension.admin` | Show the installed pack hash / write the pack for manual install |

State (return points, first visits, lit portals, the installed pack hash) is kept
in `plugins/BigCasares/dimensions/state.yml` with a schema version. An unreadable
file is moved aside as `state.yml.corrupt-<time>` and the store starts empty; a
file from a newer plugin version is never overwritten.

## Limitations

- Unmodified clients cannot receive new block types, fluids, entity types or
  particles. Custom full cubes reuse mushroom-block states (93 available across
  the three mushroom blocks); other blocks need vanilla stand-ins; new fluids are
  water or lava; creatures are vanilla mobs dressed with BetterModel models.
- Set `block-updates.disable-mushroom-block-updates: true` in
  `config/paper-global.yml`, or Paper may recompute mushroom faces and turn custom
  blocks back into vanilla ones. The custom-content module warns when it is unset.
- Changes to worldgen take effect after a restart and only in chunks generated
  afterwards; existing chunks never change.
- Populators run once per chunk with a one-chunk buffer: features larger than that
  must be planned per chunk (as the Sift's landmarks are).
- `/reload` and plugin reloads keep the installed datapack; they cannot add or
  remove dimensions without a restart.
- Live Paper behaviour (world creation, generation threads, client rendering) is
  not covered by unit tests; verify on a test server before deploying.
