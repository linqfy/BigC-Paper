# The Sift

The Sift is a port of **Mielon's The Sift 1.1.1** (Fabric, Minecraft 26.3, MIT
licence), used with the author's permission, onto the
[dimension framework](README.md). It runs on an unmodified Paper 26.2 server for
unmodified Java clients with the BigCasares resource pack. Module id:
`the-sift`; dimension `the_sift:the_sift`.

Credits: Mielon (mod, art, data); contributors credited by the mod — ImpSteve
(Dark Sniffer texture), Fuzja Jądrowa ("Rift" music disc), StrawberrySushi_
(Korean localisation), Echo_Dev ("Wellspring" remix). The licence and credits ship
next to the imported files (`LICENSE_the_sift`, `CREDITS.txt`).

## Getting there

**Ancient City ritual (the mod's entrance).** Ancient Cities generated after the
plugin is installed use the mod's centre pieces, which hold eight Sonorous
Deepslate blocks beside the reinforced deepslate frame.

1. Blow a goat horn within 40 blocks of the frame. Three seconds later the Singer
   rises in the frame and sings; a sound wave turns each Sonorous Deepslate within
   20 blocks from horn mode into note mode.
2. Place note blocks on the note-mode Sonorous Deepslate. Right-click a note block
   to choose one of the eight Sift notes; striking it plays the note.
3. Strike the notes in the order **1 3 7 6 5 2 4 8**. The console replays the
   melody with coloured beams and the frame lights as a portal to the Sift.
   The reverse melody (8 4 2 5 6 7 3 1) closes it again.

Ancient Cities that existed before the install keep vanilla centres; use the
crafted portal there, or place Sonorous Deepslate by hand (`/content give`).

**Crafted portal (an addition of this port).** A frame of crying obsidian
(interior 2–21 wide, 3–21 tall) lit with an echo shard. Configurable or
removable under `the-sift.crafted-portal`.

Arriving builds a small return frame next to the arrival point; walking through
it returns players to where they entered. Coordinates map 1:1, as in the mod.

**Rifts (the mod's way out).** Every 270–330 seconds a rift opens 20–64 blocks from
each player in the Sift and stays for five minutes. Walking into it leads to the
player's Overworld respawn point (or the world spawn), next to an Overworld rift
that stays open as long as the original; that rift leads back to where the player
left the Sift. Players arriving through a rift are not sent back until they step
out of it.

## What is ported

Every item below is either carried over from the mod, adapted because vanilla
clients cannot show new content types, or original to this port.

| Area | Status | Notes |
| --- | --- | --- |
| Terrain, noises, 10 biomes, surface rules, carvers | From the mod | The mod's 26.3 worldgen JSON translated to 26.2 by `SiftDataTranslator` and compiled into the dimension datapack |
| Biomes | From the mod | Sift Wastes, Overgrown Clearing, Overgrown Forest, Siftslate Slopes, Overgrown Slopes, Overgrown Forest Slopes, Ichor Snowy Peaks, Siftslate Peaks, Overgrown Peaks, Sift Deep Dark; colours, fog, particles and music sync to clients |
| Ores, blobs, vegetation and other data-driven features | From the mod | Native placed features |
| Willow trees, monoliths, dry sculk spikes, cliff arches, soul canyons, sniffer caves, sniffer and plant patches, surface sculk regions, flower patches, covered-growth cleanup, abandoned portals, Ichor lakes, springs and snow, lava floors | From the mod, Java populators | Ported line by line from the mod's feature code with the same constants and random draw order; differences are listed in each class's javadoc |
| Abandoned portal ruins | From the mod | The mod's ten templates and chest loot table, filled at generation time |
| 30 full-cube blocks (Siftslate family, ores, soils, Soul Block, Ichor Snow Block, Sonorous Deepslate, willow logs/wood/planks/foliage) | Adapted | Real blocks with the mod's textures, backed by mushroom-block states; hardness, tools, drops and sounds emulated |
| Plants, hanging roots, vines, Ichor Snow layers, portal mist | Adapted | Vanilla stand-ins (for example Overgrown Fronds as short grass, Overgrown Lotus as a cornflower, pitcher bulbs as pitcher plants), tinted by the biomes |
| Ichor | Adapted | Water inside the Sift. Touching it grants Regeneration I (and Slowness), and new sources do not form, as in the mod |
| Day/night cycle | From the mod | The Sift's own clock with the mod's 24,120-tick day |
| Blub | Adapted | A silent wolf with the mod's model, textures, sounds and stats; tempted and tamed (one in three) with Soul Blocks, heals with Soul Blocks or food, sits, follows and defends its owner |
| Sifter | Adapted | A silent, scaled silverfish (hostile, melee, no daylight burning, no climbing; hitbox 0.9 x 0.675 against the mod's 0.9 x 0.95) with the mod's model, sounds and stats (40 health, 10 attack, 28 follow range). The spawner is the mod's: every 80 ticks near each player, never on Peaceful, in groups of up to five on Siftslate, growth and healthy-sculk floors, with the mod's biome chances and area caps. It bites after a 14-tick wind-up, drops raw Sifter meat (cooked when it dies on fire, plus Looting) and 7 experience. See `SiftSifters` for every difference |
| Singer | Adapted | The converted model performs the ritual song; she does not roam or barter |
| Sniffers | From the mod | Placed by sniffer caves and surface sculk regions (the mod's dark sniffers are ordinary sniffers). The biomes also list sniffers, but vanilla sniffer spawn rules need grass, so, as in the mod, they do not spawn naturally on Sift ground |
| Ancient City ritual | From the mod | See above; console and replay state is not saved across restarts |
| Rifts | From the mod | Timing, size, placement search and linking as in the mod; models converted from the mod; rifts are not saved across restarts and the exit search covers 48 blocks (the mod searches farther) |
| Items | Partial | Block items, Charoite, Siftite nugget/ingot, Ichor shard, Sifter meat (food), Ichor bottle/bucket and willow sapling exist; the bottle, bucket and sapling have no Ichor- or tree-specific behaviour yet |
| Siftite gear | Adapted | Sword, shovel, pickaxe, axe, hoe, spear, four armour pieces and the upgrade template are netherite items with the mod's stats (durability, damage, speed, mining speed, armour, knockback resistance, fire resistance), models and armour texture. See `SiftiteGear` for the numbers and the differences (netherite ingots also repair them; the template keeps the client's netherite template text) |
| Siftite death recovery | Adapted | Siftite items are taken out of a player's death drops and returned through a small particle rift 2 seconds after the respawn (or join); the stacks are kept in `data/the-sift/siftite-recovery/<uuid>.bin` until they are in the inventory. |
| Shifting tides (Flow, Endure, Thrive) | Original | A three-day cycle: Endure hardens hostile mobs and stops natural regeneration; Thrive speeds regeneration and grants Haste |
| Crafted portal | Original | See above |

### Not ported yet

Echo Golem and Dark Sniffer creatures; the Sifter spawn egg;
Charoite enchanting; the Sculkflower crop and seeds; Ichor snowballs, buckets,
bottles and cauldrons; the Singer's roaming, bartering and soul events;
advancements; the mod's `/locate` and weather commands. The two music discs are
not imported (see below).

## Configuration

```yaml
modules:
  the-sift:
    enabled: true
the-sift:
  seed-salt: 0                 # terrain variation on top of the world seed
  world-border-diameter: 60000
  entry-permission: ""          # empty: anyone may enter
  travel-cooldown-seconds: 5
  crafted-portal:
    enabled: true
    frame-block: minecraft:crying_obsidian
    activation-item: minecraft:echo_shard
  ancient-city-ritual:
    enabled: true               # Ancient City centre templates + ritual
  tides:
    enabled: true
    flow-ticks: 24120
    endure-ticks: 24120
    thrive-ticks: 24120
  ichor:
    regeneration: true
    slowness: true
  blubs:
    enabled: true
  rifts:
    enabled: true
  sifters:
    enabled: true               # the mod's Sifter spawner (the hostile creature)
```

Changing `seed-salt`, toggling the ritual or editing worldgen changes the datapack:
restart once, and only chunks generated afterwards change.

## Installing and operating

1. Install the plugin with BetterModel. Set
   `block-updates.disable-mushroom-block-updates: true` in
   `config/paper-global.yml` (custom blocks rely on it).
2. Start the server once: the dimension datapack is written and the log asks for a
   restart. Restart; `/dimension list` then shows `the_sift:the_sift ACTIVE`.
3. Serve the BigCasares resource pack (it carries the Sift textures, models,
   blockstates and sounds); without it Sift blocks look like mushroom blocks.
4. Operators can enter with `/dimension tp the_sift:the_sift` and inspect the tides
   with `/dimension info the_sift:the_sift`.

The ritual's template override changes Ancient City generation in every world that
generates new Ancient Cities. The datapack also lets vegetation and wolves treat
mushroom blocks as ground (`#minecraft:supports_vegetation` and related tags,
`#minecraft:wolves_spawnable_on`), which also applies to vanilla huge mushrooms.

## Updating the imported content

All mod data and art come from the mod jar through scripts; nothing is edited by
hand:

```bash
python3 -I tools/port_mielon_sift.py path/to/mielons-the-sift-1.1.1-mc26.3-fabric.jar
python3 -I tools/convert_geckolib_bbmodel.py <jar> blub blub the_sift_blub --rename hopping=walk --rename flapping=idle --loop idle=3.0
python3 -I tools/convert_geckolib_bbmodel.py <jar> blub tamed_blub the_sift_blub_tamed --rename hopping=walk --rename flapping=idle --loop idle=3.0
python3 -I tools/convert_geckolib_bbmodel.py <jar> singer singer the_sift_singer
python3 -I tools/convert_geckolib_bbmodel.py <jar> rift rift/sift the_sift_rift
python3 -I tools/convert_geckolib_bbmodel.py <jar> rift rift/overworld the_sift_rift_overworld
python3 -I tools/convert_geckolib_bbmodel.py <jar> sifter sifter the_sift_sifter --rename running=walk --speed walk=2.5
bash gradlew generateCustomBlockstates   # after changing custom blocks
bash gradlew generateSiftDatapack        # inspect build/generated-datapacks/
```

The import writes `src/main/resources/the_sift/mielon/` (worldgen data, templates,
loot tables) and `resourcepack/java/assets/the_sift/`, replacing both. The music
disc tracks are third-party works credited by the mod; they are left out unless
`--include-music` is passed, after their authors' permission for this server is
confirmed.

## Verification status

Unit tests cover the datapack translation and determinism, the Ancient City
templates, the ritual melody, rift placement, the Sifter spawner's search, loot and bite timeline and its converted model, every ported feature (determinism, staying inside the
populator region, expected output on prepared terrain, thread safety for several
families), the loot table, block rendering contracts and the framework. The
generated datapack has been checked against Minecraft 26.2's registries and block
states. **Nothing has been run on a live Paper server yet**: world creation,
generation in game, client rendering, BetterModel animation playback and the
ritual in an Ancient City still need a test server.
