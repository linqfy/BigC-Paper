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

Arriving through the crafted portal builds a small return frame next to the arrival
point (unless a lit portal is within 24 blocks); walking through it returns players to
where they entered. Its coordinates map 1:1.

**The main portal (where every Ancient City portal leads).** The mod sends every
Ancient City portal to one structure in the Sift, not to the same coordinates. The port
does the same. Entering through the Ancient City portal puts the player at the main
portal's arrival point, in the middle of its portal facing the way they faced; no return
frame is built. Walking into the main portal returns the player to where they entered. If
that portal no longer stands, or the player came in through a rift, an exit rift opens at
their respawn point instead (five minutes, leading back to the main portal) and the
player is told when the portal was closed. `/dimension locate the_sift:the_sift
main_portal` gives its coordinates. If a player enters before the main portal is ready
(it is built in the background the first time the dimension is active, which takes
a few minutes) they arrive like through the crafted portal: same coordinates, a return
frame; the next entries use the main portal.

The site is chosen like the mod does: the flattest spot within 96 blocks of 0, 0 (a
coarse search every 16 blocks, then every 4 blocks around the best), nearer the centre
preferred; the terrain is blended into the structure and its foundation sealed. The
chunks involved (about 400) are generated in the background, the plan is computed on
an asynchronous thread over snapshots and the blocks (tens of thousands) are placed two
thousand per tick, so the server does not stall; the coordinates are saved in `state.yml` and the
structure is never rebuilt or moved. It can be switched off with
`the-sift.main-portal.enabled: false`.

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
| Ichor | Adapted | Water inside the Sift. Touching it grants Regeneration I (and Slowness), and new sources do not form, as in the mod. See Ichor items below |
| Day/night cycle | From the mod | The Sift's own clock with the mod's 24,120-tick day |
| Blub | Adapted | A silent wolf with the mod's model, textures, sounds and stats; tempted and tamed (one in three) with Soul Blocks, heals with Soul Blocks or food, sits, follows and defends its owner |
| Sifter | Adapted | A silent, scaled silverfish (hostile, melee, no daylight burning, no climbing; hitbox 0.9 x 0.675 against the mod's 0.9 x 0.95) with the mod's model, sounds and stats (40 health, 10 attack, 28 follow range). The spawner is the mod's: every 80 ticks near each player, never on Peaceful, in groups of up to five on Siftslate, growth and healthy-sculk floors, with the mod's biome chances and area caps. It bites after a 14-tick wind-up, drops raw Sifter meat (cooked when it dies on fire, plus Looting) and 7 experience. See `SiftSifters` for every difference |
| Singer | Adapted | At the Ancient City, the converted model performs the ritual song. In the Sift she is a silent wandering trader (1.48 x 4.8) with the mod's model, sounds and stats that rises from the ground when an Echo Golem brings a Soul Block, roams for 30 seconds, receives the golem's soul and trades: right-click or drop a Soul Block and she throws a gift (the mod's table; the Music Disc Wellspring is re-rolled because the discs are not imported). A name tag or the spawn egg makes her stay. See `SiftSingers` for every difference |
| Echo Golem | Adapted | A silent wolf scaled to the mod's 1.15 x 1.65 with the mod's model, sounds and stats (34 health, 0.23 speed). One spawns at every Soul Canyon and one near each player where none is within 165 blocks. It explores, walks into a canyon, picks up a Soul Block, carries it out and delivers it to a Singer (calling one if none is near) or, once given an Echo Shard, to that player; it bows, sits for 30 seconds if hurt or right-clicked while carrying, and keeps its state in the entity data. Canyons carved before this version are not known to the golems. See `SiftEchoGolems` and `EchoGolemBrain` for every difference |
| Sniffers | From the mod | Placed by sniffer caves and surface sculk regions. The biomes also list sniffers, but vanilla sniffer spawn rules need grass, so, as in the mod, they do not spawn naturally on Sift ground. Sniffers dig sculk (the datapack adds it to `#minecraft:sniffer_diggable_block`, in every world) and are infected by it, see Dark Sniffer |
| Dark Sniffer | Adapted | The surface sculk regions place one or two. A real, vanilla sniffer (the mod's hitbox, brain, digging and sounds) hidden under a BetterModel model of the vanilla sniffer with the mod's `dark_sniffer.png` (a vanilla client has no second sniffer texture). The mod's `DarkSnifferEntity#tick` is ported whole: 40 health, 7 attack, it hunts players (not in Creative or Spectator, never on Peaceful) and ordinary sniffers within 32 blocks, bites every 24 ticks, and from 5 blocks away in sight pulls its prey in with a sonic sniff (10-tick wind-up, a wave at 1.5 blocks per tick led by the prey's velocity, 100-tick cooldown); otherwise it wanders around its home. It digs Sculkflower Seeds out of sculk, drops one sculk, cannot be fed or bred, and is never targeted or angered by Wardens, which pet it now and then. Sniffers that dig sculk are infected, shake for 600 ticks and become Dark Sniffers; ordinary sniffers within 20 blocks of one run away. A tamed Blub that kills one awards Food Chain. See `SiftDarkSniffers` for every difference (Sift world only, no spawn egg, no sound-wave particle or Warden petting animation) |
| Sculkflower | Adapted | Seeds (torchflower seeds underneath) are planted on sculk, healthy sculk, dry healthy sculk or farmland; the crop is a torchflower crop whose two stages grow with the mod's rules (light 9, growth speed with healthy sculk as moist farmland, one age per bone meal) into the Sculkflower (a warped fungus), which drops itself, makes light blue dye and is compostable at the mod's chances. Placed ones are remembered per chunk; the datapack lets crops and fungi stand on sculk and red mushroom blocks. See `SiftSculkflowers` |
| Ancient City ritual | From the mod | See above; console and replay state is not saved across restarts |
| Rifts | From the mod | Timing, size, placement search and linking as in the mod; models converted from the mod; rifts are not saved across restarts and the exit search covers 48 blocks (the mod searches farther) |
| Main portal | From the mod | The `main_portal` template (9 x 19 x 40) placed once at the flattest site near 0, 0 with the mod's terrain blending, foundation, covered-growth clean-up and planting; every Ancient City portal arrives at its anchor and its portal returns players. Differences below |
| `/locate structure the_sift:main_portal` | Adapted | `/dimension locate the_sift:the_sift main_portal` (vanilla `/locate` cannot name structures that are not in a datapack registry). `the_sift:abandoned_main_portal` is not ported: the mod answers it from a global landmark tracker that the port dropped, see `AbandonedMainPortalFeature` |
| Items | From the mod / adapted | Block items, Charoite, Siftite nugget/ingot/gear/template, Ichor shard, bottle, bucket and snowball, Sifter meat, willow sapling, Sculkflower and seeds, Singer and Echo Golem spawn eggs: content items on vanilla materials with the mod's models and names. Recipes, anvils, crafters, smithing tables and cooking blocks take a content item only where a recipe names it exactly (`ContentRecipeGuard`), so a Siftite nugget never crafts an iron ingot and a Siftite ingot never upgrades gear to netherite |
| Recipes | From the mod | All 76 of the mod's recipes, with content items as exact ingredients and results (`recipe/SiftRecipeCatalog`, `SiftRecipeRegistrar`); a test checks that the registered items cover every recipe |
| Slabs, stairs, walls, doors, trapdoors, fences, gates, buttons, pressure plates, signs, shelves, boats, Ichor glass and panes, Ichor snow layers, plant items | Substitute | Vanilla clients cannot show new non-cube blocks, so these are content items with the mod's icons and names that place close vanilla look-alikes: cobbled deepslate and deepslate brick shapes for Siftslate, warped wood for Overgrown Willow (the nearest colour), oak boats, light blue glass, and the world generator's plant stand-ins. Vanilla places and connects them; their positions are remembered per chunk so they drop the mod's item again (vanilla's tool, silk-touch and double-slab rules, which the mod's tables copy), plants in the Sift drop the mod's plant items (shears or silk touch where the mod asks for them) and willow boats break into willow boats. Broken by water or a piston, a look-alike drops the vanilla item (`SiftStandIns`) |
| Advancements | From the mod | The mod's story and Sift tabs: 21 of its 22 advancements, with native criteria where the mod has them and the code-awarded ones granted at the mod's moments (horn near an Ancient City frame, taming a Blub, rift travel, ore mining, setting wood on fire, Siftite recovery, Charoite enchanting, an Ichor snowball near a Warden, Echo Golem and Singer events, Sculkflower planting, a Blub killing a Dark Sniffer). Rift Resonance needs the music disc that is not imported, so it is left out; an advancement whose feature is switched off is left out and its children move up |
| Ichor items | From the mod / adapted | A bucket or glass bottle filled from Ichor in the Sift holds Ichor; an Ichor bucket poured in another world leaves an Ichor source there (remembered per chunk) that gives the Ichor effects and fills an Ichor bucket again, though the water it spreads is plain water; the bottle is a drink with the mod's colour and three seconds of Regeneration, the bucket empties as water (Ichor in the Sift). Cauldrons filled with Ichor (remembered per chunk; every water cauldron in the Sift) give Ichor bottles and buckets and take Ichor bottles back, with the mod's sounds |
| Ichor snow and snowballs | From the mod / adapted | Snow layers are the Sift's Ichor snow and drop Ichor snowballs; the Ichor snow block drops four. While it rains in the Sift, Ichor snow settles on Ichor Snowy Peaks only (block light below 10, at most two layers), as the mod's weather mixin does; Paper already keeps weather per world. An Ichor snowball landing within 16 blocks of a Warden awards False Alarm |
| Charoite enchanting | From the mod | Charoite in the enchanting table's lapis slot (it is a lapis lazuli underneath) adds 20 levels to each requirement and one level beyond the vanilla maximum to multi-level enchantments; the item is marked and can no longer go in an anvil. Paper rolls from the displayed cost, so the port rolls again from the base cost with the table's seed (`SiftCharoite`). The anvil repairs Siftite gear only with Siftite ingots |
| Willow saplings | From the mod / adapted | A mangrove propagule on the client, remembered per chunk; where vanilla would grow a mangrove the mod's willow grows (same feature code as the world generator), or the sapling stays when it does not fit. Saplings come from willow vines broken with Fortune III (no shears, no silk touch); willow foliage drops itself |
| Soils and bone meal | From the mod / adapted | Siftslate growth turns into siftslate, dry sculk growth into dry healthy sculk, under full blocks or fluids; siftslate growth turns snowy under Ichor snow. Bone meal spreads growth onto siftslate next to it, grows patches of overgrown plants on growth and spreads Sift plants (their stand-ins, in the Sift). Custom blocks have no random ticks, so darkness alone does not kill growth |
| Siftite gear | Adapted | Sword, shovel, pickaxe, axe, hoe, spear, four armour pieces and the upgrade template are netherite items with the mod's stats (durability, damage, speed, mining speed, armour, knockback resistance, fire resistance), models and armour texture. See `SiftiteGear` for the numbers and the differences (netherite ingots also repair them; the template keeps the client's netherite template text) |
| Siftite death recovery | Adapted | Siftite items are taken out of a player's death drops and returned through a small particle rift 2 seconds after the respawn (or join); the stacks are kept in `data/the-sift/siftite-recovery/<uuid>.bin` until they are in the inventory. |
| Shifting tides (Flow, Endure, Thrive) | Original | A three-day cycle: Endure hardens hostile mobs and stops natural regeneration; Thrive speeds regeneration and grants Haste |
| Crafted portal | Original | See above |

### Not ported

- Client-side features of the mod: the Singer's glowmask and the Echo Golem's emissive
  layer, dynamic lights, custom particles (vanilla particles stand in), the procedural
  sky, the Ichor fluid texture and the weather texture swap.
- The look of the non-cube blocks: they are vanilla look-alikes (see above). Ichor is
  water with the Sift's tint, not its own fluid with the mod's texture and glow.
- The Blub, Sifter and Dark Sniffer spawn eggs (the Singer's and Echo Golem's exist).
- The two music discs ("Rift" and the "Wellspring" remix are third-party works, see below)
  and with them the Rift Resonance advancement.
- `/locate structure the_sift:abandoned_main_portal` (it needs the mod's global landmark
  tracker, which the port dropped).
- Smaller block behaviours that need random ticks or fall callbacks on custom blocks:
  growth dying in the dark, willow foliage decaying like leaves, healthy sculk trampling
  crops on it.

Every class that ports mod code lists its own differences in its javadoc.

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
  singers:
    enabled: true               # the Singer's soul event, roaming and bartering
  echo-golems:
    enabled: true               # Echo Golem spawner and behaviour
  dark-sniffers:
    enabled: true               # Dark Sniffers: hunting, sonic sniff, digging, infection (needs BetterModel)
  sculkflowers:
    enabled: true               # Sculkflower growth, harvest and bone meal
  main-portal:
    enabled: true               # build the main portal; Ancient City portals lead to it
```

Echo Golems and Singers can be switched off separately. Without Singers a golem
still collects Soul Blocks and delivers them to a player it is bonded to; without
golems Singers appear only from their spawn egg. Soul Canyon landmarks (entrance,
midpoint, soul cluster, Soul Blocks and canyons still waiting for their golem) are kept in
`plugins/BigCasares/data/the-sift/landmarks.bin`; deleting it makes golems forget
the canyons of already generated chunks.

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
`#minecraft:wolves_spawnable_on`), which also applies to vanilla huge mushrooms. For the Sculkflower and
the Dark Sniffer it also adds sculk and red mushroom blocks to `#minecraft:supports_crops` and
`#minecraft:supports_warped_fungus` (plus farmland for the fungus) and sculk to
`#minecraft:sniffer_diggable_block`: in every world, crops and warped fungi can stand on sculk and huge red
mushroom blocks and sniffers dig sculk.

## Main portal: differences from the mod

- **Generated surface.** The mod asks the chunk generator for the height of the untouched
  noise terrain. Paper cannot do that without generating the chunk, so the chunks are
  generated and the surface is the top Sift terrain block of each column, found by scanning
  down past plants, trees and water. Monoliths, arches and spikes are made of terrain
  blocks, so they count as ground: the roughness score steers the search away from them,
  and the blending cuts or buries them where they stand next to the portal, instead of
  removing them first. Ichor lakes read as their lake bed.
- **When.** The mod builds on the first visit, synchronously. The port builds when the
  dimension first activates (ten seconds after start-up), in the background; visitors who
  arrive before it is ready get the crafted portal's behaviour once. Stopping the server
  mid-build builds again on the next start. If the build fails it is logged and retried
  every five minutes (the next entry through the portal also retries once that has passed).
- **Where it is stored.** The dimension's persistent values (`state.yml`), not the world's
  saved data. Deleting the entry makes the portal be built again, over the old one.
- **Portal.** The mod's `sift_portal` block is the light block that stands in for it
  everywhere in the port (level 12 here, the mod's; the template's stand-in says 13 until the
  framework lights the portal). The framework's portal service lights it, so breaking its frame
  or interior extinguishes it for good (the mod's portal is unbreakable); a restart relights
  an intact one. Explosions destroy the frame's custom blocks without extinguishing the portal,
  which keeps working while its light blocks stand.
- **Entrance check.** The mod stores the portal block it entered through and sends players
  to an exit rift if that block is no longer a Sift portal. The port stores the entry
  location (the framework's return point) and looks for a lit portal's light block within
  two blocks of it. The exit rift is the same exit as a rift's: it opens at the player's
  respawn point (the mod's `returnFromPortal`). It needs `the-sift.rifts.enabled`; without
  rifts the framework returns the player to the stored point whether or not the entrance stands.
- **Rift arrivals.** Players who entered through a rift have no entrance to return to (the
  mod clears their return point); the port remembers them in memory until they leave or
  enter another way, so a server restart forgets it.
- **Ruins.** An abandoned portal ruin generated near the chosen site is not avoided (see
  `AbandonedMainPortalFeature`); its rough ground counts against a site in the search, and what
  the build overlaps is cut or built over like any terrain.
- **Not ported:** the mod's feature placement guard (the chunks around the site are
  generated before it is built, so nothing is placed over it afterwards), the random
  source of the planting (the exact plants differ), and the mod's block update flags (Paper offers
  none; blocks are set without physics).
- **Arrival search.** The framework loads and searches around the 1:1 coordinates of the
  entry before the main portal replaces the destination, so entering generates up to 25
  chunks of the Sift there even though the player does not arrive.

## Updating the imported content

All mod data and art come from the mod jar through scripts; nothing is edited by
hand:

```bash
python3 -I tools/port_mielon_sift.py path/to/mielons-the-sift-1.1.1-mc26.3-fabric.jar
python3 -I tools/convert_geckolib_bbmodel.py <jar> blub blub the_sift_blub --rename hopping=walk --rename flapping=idle --loop idle=3.0
python3 -I tools/convert_geckolib_bbmodel.py <jar> blub tamed_blub the_sift_blub_tamed --rename hopping=walk --rename flapping=idle --loop idle=3.0
python3 -I tools/convert_geckolib_bbmodel.py <jar> singer singer the_sift_singer --rename idle=stand --rename walk=stride
python3 -I tools/convert_geckolib_bbmodel.py <jar> echo_golem echo_golem the_sift_echo_golem --rename idle=search_idle --rename walk=search_walk --skip search_idle --skip carry_idle --skip soul_visible --skip look_left --skip look_left_far --skip look_right --skip look_right_far --loop soul_hidden=0.05
python3 -I tools/convert_geckolib_bbmodel.py <jar> rift rift/sift the_sift_rift
python3 -I tools/convert_geckolib_bbmodel.py <jar> rift rift/overworld the_sift_rift_overworld
python3 -I tools/convert_geckolib_bbmodel.py <jar> sifter sifter the_sift_sifter --rename running=walk --speed walk=2.5
python3 -I tools/generate_dark_sniffer_bbmodel.py <jar>   # vanilla sniffer parts + the mod's texture
bash gradlew generateCustomBlockstates   # after changing custom blocks
bash gradlew generateSiftDatapack        # inspect build/generated-datapacks/
```

The Singer and Echo Golem models rename the mod's idle and walk animations so that
BetterModel's own idle/walk handling does not compete with the poses the plugin plays.

The Dark Sniffer model is not a GeckoLib model: `tools/generate_dark_sniffer_bbmodel.py` builds it from
`tools/sift/sniffer_parts.json` (the vanilla sniffer's parts and texture offsets) and
`tools/sift/dark_sniffer_animations.json` (animations written for this port), reusing the converter's functions.

The import writes `src/main/resources/the_sift/mielon/` (worldgen data, templates,
loot tables) and `resourcepack/java/assets/the_sift/`, replacing both. The music
disc tracks are third-party works credited by the mod; they are left out unless
`--include-music` is passed, after their authors' permission for this server is
confirmed.

## Verification status

Unit tests cover the datapack translation and determinism, the Ancient City
templates, the ritual melody, rift placement, the Sifter spawner's search, loot and bite timeline and its converted model, the Singer's soul event, barter table and the Echo Golem's mission and route state machine, spawner and landmark registry, the Dark Sniffer's brain, sonic sniff, Warden petting and infection (all against small fakes), the converted Singer, Echo Golem and generated Dark Sniffer models, the Sculkflower's growth rules, saved positions and loot tables, every ported feature (determinism, staying inside the
populator region, expected output on prepared terrain, thread safety for several
families), the loot table, block rendering contracts and the framework. The main portal
is tested without a server: the site search and scoring over fake height fields, the
terrain blending, template placement, clearing, planting and determinism over an in-memory
terrain, the overlay region, the framework's frame detection on the built structure, the travel
listener's decisions and the stored anchor. The
generated datapack has been checked against Minecraft 26.2's registries and block
states. **Nothing has been run on a live Paper server yet**: world creation,
generation in game, client rendering, BetterModel animation playback, the
ritual in an Ancient City, the Echo Golem's pathfinding through a real canyon,
the Singer's appearance and the barter, and the main portal (asynchronous chunk generation, the
snapshot-based plan and its tick-sliced application, lighting, the arrival and return
through real portals, the exit rift when the entrance is gone, `/dimension locate`) still
need a test server.
