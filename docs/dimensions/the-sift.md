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
| Singer | Adapted | At the Ancient City, the converted model performs the ritual song. In the Sift she is a silent wandering trader (1.48 x 4.8) with the mod's model, sounds and stats that rises from the ground when an Echo Golem brings a Soul Block, roams for 30 seconds, receives the golem's soul and trades: right-click or drop a Soul Block and she throws a gift (the mod's table; her Music Disc Wellspring is the stand-in disc, see Music discs). A name tag or the spawn egg makes her stay. Her glowmask and the light at her head are substituted (head, antlers, arms, hands and torso drawn at full brightness; a level-15 light block that follows her head). See `SiftSingers` for every difference |
| Echo Golem | Adapted | A silent wolf scaled to the mod's 1.15 x 1.65 with the mod's model, sounds and stats (34 health, 0.23 speed). One spawns at every Soul Canyon and one near each player where none is within 165 blocks. It explores, walks into a canyon, picks up a Soul Block, carries it out and delivers it to a Singer (calling one if none is near) or, once given an Echo Shard, to that player; it bows, sits for 30 seconds if hurt or right-clicked while carrying, and keeps its state in the entity data. The carried Soul Block is drawn at full brightness and lit by a level-14 light block that follows the golem. Canyons carved before this version are not known to the golems. See `SiftEchoGolems` and `EchoGolemBrain` for every difference |
| Sniffers | From the mod | Placed by sniffer caves and surface sculk regions. The biomes also list sniffers, but vanilla sniffer spawn rules need grass, so, as in the mod, they do not spawn naturally on Sift ground. Sniffers dig sculk (the datapack adds it to `#minecraft:sniffer_diggable_block`, in every world) and are infected by it, see Dark Sniffer |
| Dark Sniffer | Adapted | The surface sculk regions place one or two. A real, vanilla sniffer (the mod's hitbox, brain, digging and sounds) hidden under a BetterModel model of the vanilla sniffer with the mod's `dark_sniffer.png` (a vanilla client has no second sniffer texture). The mod's `DarkSnifferEntity#tick` is ported whole: 40 health, 7 attack, it hunts players (not in Creative or Spectator, never on Peaceful) and ordinary sniffers within 32 blocks, bites every 24 ticks, and from 5 blocks away in sight pulls its prey in with a sonic sniff (10-tick wind-up, a wave at 1.5 blocks per tick led by the prey's velocity, 100-tick cooldown); otherwise it wanders around its home. It digs Sculkflower Seeds out of sculk, drops one sculk, cannot be fed or bred, and is never targeted or angered by Wardens, which pet it now and then. Sniffers that dig sculk are infected, shake for 600 ticks and become Dark Sniffers; ordinary sniffers within 20 blocks of one run away. A tamed Blub that kills one awards Food Chain. See `SiftDarkSniffers` for every difference (Sift world only, no sound-wave particle or Warden petting animation; the spawn egg is a stand-in item, see Spawn eggs) |
| Sculkflower | Adapted | Seeds (torchflower seeds underneath) are planted on sculk, healthy sculk, dry healthy sculk or farmland; the crop is a torchflower crop whose two stages grow with the mod's rules (light 9, growth speed with healthy sculk as moist farmland, one age per bone meal) into the Sculkflower (a warped fungus), which drops itself, makes light blue dye and is compostable at the mod's chances. Placed ones are remembered per chunk; the datapack lets crops and fungi stand on sculk and red mushroom blocks. See `SiftSculkflowers` |
| Ancient City ritual | From the mod | See above; console and replay state is not saved across restarts |
| Rifts | From the mod | Timing, size, placement search and linking as in the mod; models converted from the mod; rifts are not saved across restarts and the exit search covers 48 blocks (the mod searches farther) |
| Main portal | From the mod | The `main_portal` template (9 x 19 x 40) placed once at the flattest site near 0, 0 with the mod's terrain blending, foundation, covered-growth clean-up and planting; every Ancient City portal arrives at its anchor and its portal returns players. Differences below |
| `/locate structure the_sift:main_portal` | Adapted | `/dimension locate the_sift:the_sift main_portal` (vanilla `/locate` cannot name structures that are not in a datapack registry) |
| `/locate structure the_sift:abandoned_main_portal` | Adapted | `/dimension locate the_sift:the_sift abandoned_main_portal`: the nearest ruin the generator has recorded in `landmarks.bin`, plus the mod's search for nearer ones, see Locating abandoned portals |
| Items | From the mod / adapted | Block items, Charoite, Siftite nugget/ingot/gear/template, Ichor shard, bottle, bucket and snowball, Sifter meat, willow sapling, Sculkflower and seeds, the five spawn eggs and the two music discs (the last seven are stand-ins, see below): content items on vanilla materials with the mod's models and names. Recipes, anvils, crafters, smithing tables and cooking blocks take a content item only where a recipe names it exactly (`ContentRecipeGuard`), so a Siftite nugget never crafts an iron ingot and a Siftite ingot never upgrades gear to netherite |
| Recipes | From the mod | All 76 of the mod's recipes, with content items as exact ingredients and results (`recipe/SiftRecipeCatalog`, `SiftRecipeRegistrar`); a test checks that the registered items cover every recipe |
| Slabs, stairs, walls, doors, trapdoors, fences, gates, buttons, pressure plates, signs, shelves, boats, Ichor glass and panes, Ichor snow layers, plant items | Substitute | Vanilla clients cannot show new non-cube blocks, so these are content items with the mod's icons and names that place close vanilla look-alikes: cobbled deepslate and deepslate brick shapes for Siftslate, warped wood for Overgrown Willow (the nearest colour), oak boats, light blue glass, and the world generator's plant stand-ins. Vanilla places and connects them; their positions are remembered per chunk so they drop the mod's item again (vanilla's tool, silk-touch and double-slab rules, which the mod's tables copy), plants in the Sift drop the mod's plant items (shears or silk touch where the mod asks for them) and willow boats break into willow boats. Broken by water or a piston, a look-alike drops the vanilla item (`SiftStandIns`) |
| Advancements | From the mod | The mod's story and Sift tabs: all 22 of its advancements, with native criteria where the mod has them and the code-awarded ones granted at the mod's moments (horn near an Ancient City frame, taming a Blub, rift travel, ore mining, setting wood on fire, Siftite recovery, Charoite enchanting, an Ichor snowball near a Warden, Echo Golem and Singer events, Sculkflower planting, a Blub killing a Dark Sniffer). Rift Resonance is awarded for the stand-in Rift disc (see Music discs); an advancement whose feature is switched off is left out and its children move up |
| Ichor items | From the mod / adapted | A bucket or glass bottle filled from Ichor in the Sift holds Ichor; an Ichor bucket poured in another world leaves an Ichor source there (remembered per chunk) that gives the Ichor effects and fills an Ichor bucket again, though the water it spreads is plain water; the bottle is a drink with the mod's colour and three seconds of Regeneration, the bucket empties as water (Ichor in the Sift). Cauldrons filled with Ichor (remembered per chunk; every water cauldron in the Sift) give Ichor bottles and buckets and take Ichor bottles back, with the mod's sounds |
| Ichor snow and snowballs | From the mod / adapted | Snow layers are the Sift's Ichor snow and drop Ichor snowballs; the Ichor snow block drops four. While it rains in the Sift, Ichor snow settles on Ichor Snowy Peaks only (block light below 10, at most two layers), as the mod's weather mixin does; Paper already keeps weather per world. An Ichor snowball landing within 16 blocks of a Warden awards False Alarm |
| Charoite enchanting | From the mod | Charoite in the enchanting table's lapis slot (it is a lapis lazuli underneath) adds 20 levels to each requirement and one level beyond the vanilla maximum to multi-level enchantments; the item is marked and can no longer go in an anvil. Paper rolls from the displayed cost, so the port rolls again from the base cost with the table's seed (`SiftCharoite`). The anvil repairs Siftite gear only with Siftite ingots |
| Willow saplings | From the mod / adapted | A mangrove propagule on the client, remembered per chunk; where vanilla would grow a mangrove the mod's willow grows (same feature code as the world generator), or the sapling stays when it does not fit. Saplings come from willow vines broken with Fortune III (no shears, no silk touch); willow foliage drops itself |
| Soils and bone meal | From the mod / adapted | Siftslate growth turns into siftslate, dry sculk growth into dry healthy sculk, under full blocks or fluids; siftslate growth turns snowy under Ichor snow. Bone meal spreads growth onto siftslate next to it, grows patches of overgrown plants on growth and spreads Sift plants (their stand-ins, in the Sift). Growth also dies where the light above it is below 4, through the random-tick sampler below |
| Random ticks | Substitute | Custom blocks are mushroom-block states and get no random ticks, so a bounded sampler draws them (see Server-side substitutes): growth dying in the dark and willow foliage decaying |
| Willow foliage decay | Substitute | Foliage that no player placed and that has no willow log or wood (or vanilla log) within six steps through leaves breaks on a sampled tick and drops the foliage itself, as the mod's loot table says. Foliage a player places is persistent |
| Healthy Sculk trampling | Substitute | A player landing from a fall of more than half a block on Healthy Sculk or Dry Healthy Sculk destroys the crop, stem or Sculkflower on it, with drops |
| Siftite gear | Adapted | Sword, shovel, pickaxe, axe, hoe, spear, four armour pieces and the upgrade template are netherite items with the mod's stats (durability, damage, speed, mining speed, armour, knockback resistance, fire resistance), models and armour texture. See `SiftiteGear` for the numbers and the differences (netherite ingots also repair them; the template keeps the client's netherite template text) |
| Siftite death recovery | Adapted | Siftite items are taken out of a player's death drops and returned through a small particle rift 2 seconds after the respawn (or join); the stacks are kept in `data/the-sift/siftite-recovery/<uuid>.bin` until they are in the inventory. |
| Shifting tides (Flow, Endure, Thrive) | Original | A three-day cycle: Endure hardens hostile mobs and stops natural regeneration; Thrive speeds regeneration and grants Haste |
| Crafted portal | Original | See above |

### Not ported

- Client-side features of the mod that have no substitute: custom particles (vanilla particles stand
  in), the procedural sky, the Ichor fluid texture, the weather texture swap and the glowing outline layer.
  The Singer's glowmask and dynamic light and the Echo Golem's emissive Soul Block and dynamic light are
  substituted, see Server-side substitutes.
- The look of the non-cube blocks: they are vanilla look-alikes (see above). Ichor is
  water with the Sift's tint, not its own fluid with the mod's texture and glow.
- The mod's two music recordings ("Rift" and the "Wellspring" remix are third-party works,
  see below); vanilla songs stand in for them, see Music discs.
- Vanilla `/locate structure the_sift:abandoned_main_portal` (the command cannot name a
  structure that is not in a datapack registry); `/dimension locate` answers it instead.
- Changing a spawner with a spawn egg of the mod's creatures (see Spawn eggs).

Every class that ports mod code lists its own differences in its javadoc.

## Spawn eggs, music discs and locating ruins (stand-ins)

These replace three things the mod does with entity types, sound events and a global tracker that a
vanilla client and server cannot register. They are substitutes, not copies. The eggs, the Rift disc and
`/dimension locate` have been used on a live test server; see Verification status for what was not.

### Spawn eggs

`the_sift:blub_spawn_egg` (a wolf egg), `the_sift:sifter_spawn_egg` (a silverfish egg) and
`the_sift:dark_sniffer_spawn_egg` (a sniffer egg) are content items with the mod's item models and names,
beside the Singer's and Echo Golem's. `SiftSpawnEggs` handles every use of them and always cancels the
vanilla spawn:

- Right-clicking a block puts the real creature on the clicked face; right-clicking water (Ichor) spawns it on
  the surface; survival players spend the egg. A Blub is spawned like a natural one (a marked, dressed wolf), a
  Sifter with the spawner's own stats and marks, a Dark Sniffer marked as the generator marks its sniffers.
- **Spawners are not changed.** A vanilla egg on a spawner makes it spawn the egg's mob, but the base mobs here
  are plain wolves, silverfish and sniffers, so a spawner set that way would spawn the wrong thing. The
  egg is refused with a message and kept.
- Only in the Sift world, and only while the creature's switch (`blubs`, `sifters`, `dark-sniffers`) is on; elsewhere the egg
  is refused with a message, never turned into the base mob. Using the egg on a mob (vanilla spawns a baby) and
  dispensers (vanilla spawns the base mob) do nothing.
- The `food_chain` advancement icon is now the real Dark Sniffer egg content item.

### Music discs

`the_sift:music_disc_rift` and `the_sift:music_disc_wellspring` are content items on **vanilla discs**: the
Rift disc on Music Disc 5 (Warden and ancient city, about as long) and the Wellspring disc on Music Disc
Otherside (cave ambience). The jukebox plays the vanilla song; the mod's recordings are not imported. The
items have the mod's model, the name "Music Disc" and the lore line "The Sift - Rift" or "The Sift - Wellspring"
(no artist is invented); the base disc's own "artist - title" tooltip line is hidden so the vanilla song is not
credited to them. What the client shows that this does not change: the "Now playing" text above the hotbar and the
jukebox subtitle still name the vanilla song. Where the mod gives the discs, the stand-ins are given: the
abandoned portal chests (the Rift disc, as the mod's loot table says; it used to be Otherside) and the Singer's
barter (the Wellspring disc, which is no longer re-rolled).

Rift Resonance (`the_sift/rift_resonance`) is awarded as in the mod: a player puts the Rift disc into an empty
jukebox within 16 blocks of an open rift (`SiftRifts#isNearOpenRift`). It is written with the other
advancements when rifts are enabled.

### Locating abandoned portals

The generator reports every ruin it raises (centre column and base height) to `SiftLandmarks`, which keeps
them in `landmarks.bin` with the Soul Canyon landmarks (older files still load). Reporting happens after the
ruin is placed and cannot change generation. `/dimension locate the_sift:the_sift abandoned_main_portal`
answers with the nearest recorded ruin and, as the mod does, looks at the candidate chunks of the 15 x 15
cells (20 chunks each, seed salt 4702392765337521733) around the sender: the nearest ones that are closer than the
recorded ruin, or all when none is recorded, up to 32 per call, are generated one after the other so their ruins
get recorded. Differences from the mod: the command answers at once, so the ruins that generation finds show up
when the command is repeated (the mod blocks the server while it generates and answers once); ruins
generated before this registry existed are not recorded (their chunks already exist), so in an old world the
answer may be a farther ruin or "not yet"; candidates outside the world border are skipped.

## Server-side substitutes

The mod relies on random ticks and fall callbacks of its own block classes and on client rendering. A vanilla
client and a custom block that is a mushroom state cannot do these, so the port fakes them on the server. Each
can be switched off under `the-sift` (see Configuration), and none of them changes the world file format.

**Random-tick sampler** (`SiftRandomTicks`, `RandomTickSampler`). Every 5 ticks, for the chunks within the world's
simulation distance (at most 10 chunks) of a player, it draws 8 random blocks per 16x16x16 section per second
(`samples-per-section-per-second`; two a run). Vanilla ticks 60 per section per second, so a block is
ticked about once in 8.5 minutes instead of once in 68 seconds. The cost is capped: a run takes at most 8000 samples
(1600 a tick on average, each a material lookup in a loaded chunk), 256 soil checks and 24 foliage searches; with more
chunks than the budget covers the sampler rotates through them and each section is sampled less often, so the
server never does more work. Sampled red mushroom blocks go to `SiftSoils`, sampled mushroom stems to `SiftFoliage`.

- *Siftslate Growth, Dry Healthy Sculk Growth.* The mod's `randomTick`: they turn into Siftslate and Dry Healthy Sculk
  when the light at the block above (`getMaxLocalRawBrightness`, which Bukkit's `Block#getLightLevel` returns) is below
  4, or a full block or fluid covers them; snow above keeps the snowy face. The cover rules also run on block changes, as before.
- *Willow foliage decay.* The foliage is an oak-leaves copy in the mod. A sampled foliage block that is not marked as
  placed searches (`LeafDecay`, at most 377 reads) for a willow log, willow wood, stripped variant or vanilla log within six steps
  through leaves, as vanilla's leaf distance does; with none it breaks (a `LeavesDecayEvent` is called first) and drops
  its loot table's entry, the foliage itself. Foliage placed by a player is persistent: its position is marked in the chunk's
  persistent data and the mark goes when the player breaks it and, if the block is gone, when the chunk loads.
  Foliage placed by world generation, grown from a sapling or put in a structure is not persistent, like the mod's.
  Foliage put in by commands or world-editing tools raises no placement event, so it is not marked and decays like
  generated foliage.
  A search that would read an unloaded chunk keeps the foliage.

**Crop trampling** (`SiftTrampling`). The mod's `HealthySculkSoilBlock#fallOn`. Paper's `GenericGameEvent` for
`HIT_GROUND` is emitted by the server right after `fallOn` and before the fall distance is reset, so the handler reads
the player's fall distance, and if it is above 0.5 and the block under the feet is Healthy Sculk or Dry Healthy Sculk,
it breaks the crop, stem, attached stem, pitcher crop or tracked Sculkflower above with its drops (the mod's loot for the
Sculkflower). An `EntityChangeBlockEvent` by the player is called first, so protection plugins can veto it.
Vanilla gives `fallOn` the supporting block; the event has a position only, so the block under the feet's centre is used.

**Glow and dynamic light for the Singer and the Echo Golem.**

- *Glow.* BetterModel offers a brightness per bone (`TrackerUpdateAction.brightness(block, sky)`, applied to the bones
  chosen by a predicate); it cannot mask pixels, so a bone glows whole or not at all. The Singer's glowmask covers the whole
  head and both antlers, 84 percent of the arms, 58 percent of the hands, 59 percent of the torso and
  38 percent of the legs: head, antlers, arms, hands and torso are drawn at brightness 15/15, the legs are not. The golem's
  `soul_block` bone (drawn at full brightness by the mod's `EchoGolemSoulGlowLayer`; hidden unless the golem carries a
  Soul Block) is drawn at 15/15. The Soul Block the Singer holds was already a block display at full brightness.
- *Light.* The mod's LambDynamicLights behaviours give the Singer's head a luminance of 15 at her `head_light` locator and
  a carried Soul Block 14 at the golem's `soul_light` locator (2.125 blocks up). `SiftDynamicLights` places a single
  `minecraft:light` block of that level in the cell of the point, or in the next free cell below, above or two below it,
  every 2 ticks, and moves it when the cell changes (the new block first, then the old one removed). Only air is replaced and
  only a light block is removed. Lights are removed when the source despawns, unloads, dies, stops carrying, is more than 64
  blocks from every player or is not one of the 24 nearest; before a chunk saves (`ChunkUnloadEvent`); and when the dimension
  deactivates or the plugin is disabled. The only thing persisted is a mark of each placed light in its chunk's data, so that
  if the server dies with lights placed, the next load of that chunk (or the next activation) removes the leftover blocks. The
  light blocks light the server too (growth survives near the Singer, snow does not settle in her light), and a player holding
  a light item sees the marker. A Singer rising out of the ground lights nothing until her head is in the open.

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
  random-ticks:
    enabled: true               # sampler behind growth dying in the dark and willow foliage decay
    samples-per-section-per-second: 8   # 1-60; vanilla's random tick speed is 60
  crop-trampling:
    enabled: true               # Healthy Sculk destroys the crop a player lands on
  dynamic-lights:
    enabled: true               # light blocks that follow the Singer and a carried Soul Block
```

Echo Golems and Singers can be switched off separately. Without Singers a golem
still collects Soul Blocks and delivers them to a player it is bonded to; without
golems Singers appear only from their spawn egg. Soul Canyon landmarks (entrance,
midpoint, soul cluster, Soul Blocks and canyons still waiting for their golem) and abandoned portal ruins are kept in
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
disc tracks are third-party works credited by the mod; they are left out (vanilla songs stand in,
see above) unless
`--include-music` is passed, after their authors' permission for this server is
confirmed.

## Verification status

Unit tests cover the datapack translation and determinism, the Ancient City
templates, the ritual melody, rift placement, the Sifter spawner's search, loot and bite timeline and its converted model, the Singer's soul event, barter table and the Echo Golem's mission and route state machine, spawner and landmark registry, the Dark Sniffer's brain, sonic sniff, Warden petting and infection (all against small fakes), the converted Singer, Echo Golem and generated Dark Sniffer models, the Sculkflower's growth rules, saved positions and loot tables, the spawn eggs' use decisions and the stand-in discs' items, the abandoned portal ruins' reports and locate candidates, the random-tick sampler (budget, rate, rotation), the leaf-decay distance search over a fake grid, the soil darkness rule, the trampling conditions, the moving-light ledger (placement, movement, restoration, release over a fake world) and the BetterModel brightness gateway, every ported feature (determinism, staying inside the
populator region, expected output on prepared terrain, thread safety for several
families), the loot table, block rendering contracts and the framework. The main portal
is tested without a server: the site search and scoring over fake height fields, the
terrain blending, template placement, clearing, planting and determinism over an in-memory
terrain, the overlay region, the framework's frame detection on the built structure, the travel
listener's decisions and the stored anchor. The
generated datapack has been checked against Minecraft 26.2's registries and block
states.

### Live test server (2026-10-18)

Paper 26.2 build 134 with BetterModel 3.2.0, offline mode, a fresh world (seed 20261018), and the
Vault-dependent modules switched off. A Mineflayer bot (26.1 protocol through ViaVersion and
ViaBackwards 5.12.2, test server only) played through it in creative and survival mode. Verified:

- **Install and world.** The datapack installs on the first start (`PENDING_RESTART`), and the Sift is
  `ACTIVE` after the restart. All 22 advancements and 76 recipes load. Chunks generate without errors:
  custom blocks as their mushroom-block states, plants as their vanilla look-alikes.
- **Creatures.** Natural Sifters, Blubs, Echo Golems and plain sniffers spawn. Sifters, Blubs, Singers,
  Echo Golems and Dark Sniffers wear their BetterModel models: the bot sees the model displays riding
  each body. A Sifter attacks. An Echo Golem walks around without errors (about 40 blocks in 80 seconds).
- **Spawn eggs.** All five create the real, dressed creature. The Echo Golem's egg refuses a spot where
  the golem would collide.
- **Main portal.** It is built in the background on the first activation: 323 chunks, 51,212 blocks,
  about 30 seconds. `/dimension locate` finds it and an abandoned portal.
- **Ancient City ritual.** A city generated after the install has its eight Sonorous Deepslate. The full
  ritual works: the goat horn awards Song of the Past, the Singer rises and turns the blocks to note
  mode, and the bot places and tunes note blocks. Striking 1 3 7 6 5 2 4 8 lights the frame, and the
  reverse melody closes it. Fallen Civilization is awarded on arrival in the city.
- **Travel.** Entering through the city portal puts the player at the main portal's anchor; walking
  into the main portal leads back to the city portal.
  - The crafted portal works both ways: an echo shard lights it, the return frame is built, and the
    player comes back to the return point without bouncing.
  - Natural rifts open 20–64 blocks away and lead to the Overworld.
  - When there is no entrance to return to, the main portal opens an exit rift at the respawn point.
    That rift leads back to the main portal's anchor. Rifter is awarded.
- **Blocks and items.**
  - Siftslate breaks into the Cobbled Siftslate content item.
  - Fronds drop only with shears, and then as the content item.
  - Siftslate Bricks craft from Cobbled Siftslate; the same recipe with vanilla mushroom blocks
    crafts nothing.
  - Siftite gear is kept through death and comes back after respawn (Keep Inventory).
  - Sift water gives Ichor's slowness, regeneration and haste.
  - The Rift disc in a jukebox near an open rift awards Rift Resonance.
- **Lights.** A Singer carries one level-15 light block at her head and leaves none behind. A server
  killed with lights placed removes them on the next start.
- **Echo Golems and Singers.** In natural terrain a golem finds a Soul Block in a canyon, picks it up and
  carries it out with a level-14 light block above it. It delivers the block to a Singer (Like Father and
  Son), bows and searches again. Right-clicking a roaming Singer with a Soul Block takes the block and
  throws a gift from her table (Pitch Perfect).
- **Sculkflower.** Seeds plant on sculk. Each bone meal advances one stage, from the torchflower crop
  look-alikes to the Sculkflower (Planting the Dark), which drops its content item.
- **Charoite.** In the enchanting table's lapis slot it raises the costs from 2/5/8 to 22/25/28 levels
  and the offered levels by one (1/2/1 to 2/3/2). The enchanted item carries the Charoite mark, and Better
  Enchanter is awarded.
- **Generated sculk, snow and Dark Sniffers.** Surface sculk regions (sculk, veins, sensors) and the
  Ichor snow on Ichor Snowy Peaks generate. A sculk region places a dressed Dark Sniffer.
- **Spawn eggs on blocks with their own use.** An egg used on a spawner leaves its spawn data unchanged.
  A powered dispenser with a Sift egg spawns nothing and keeps the egg.
- **Client inputs.** The resource pack the server sends was checked file by file:
  - every content item resolves to a defined model (166 models) with existing textures;
  - the 50 Sift block-state models exist;
  - all 47 sound events have their files, and every Sift sound name the code plays is defined;
  - the BetterModel pack is built from the loaded models.

  The models animate: a Sifter's 18 bone displays got 621 transform updates in 10 seconds.
- **Cost.** Average tick times stayed between 1.6 and 10 ms. A `/tp` into unexplored terrain stalls one
  tick while chunks generate synchronously: 1.2 s in the Sift against 2.2 s for the same jumps in the
  Overworld.

- **A real 26.2 client.** The official 26.2 Java client ran on a virtual display (Xvfb with Mesa
  software rendering, offline account) and joined the test server, accepting its resource packs. It
  showed:
  - the Sift terrain with the mod's textures (Siftslate and growth match the mod's images, not the
    mushroom blocks underneath);
  - the main portal with its surface;
  - all five creature models (Blub, Sifter, Dark Sniffer, Singer and Echo Golem);
  - an exit rift with its view of the other side.

  After the fixes below, its log has no model, texture or sound errors for the Sift. All 47 Sift
  sound events played; only a deliberately unknown control event was reported. OpenAL wrote the
  output to a file: with every other sound category muted, the Sift sounds rise from silence (the
  Singer, Sifter, Echo Golem pickup and a Sonorous note). The real client also found the remaining
  bugs in the list below.
- **Many players.** Eight bots stayed in the Sift for about 85 minutes, each jumping to a random spot
  every 20 seconds. That is far harsher than walking players, because every jump makes the server
  generate fresh terrain at once. (The real client also ran on the same 4 CPUs during this run.)
  - Five-second tick averages stayed between 6 and 26 ms, and heap use rose and fell with garbage
    collection.
  - The loaded Sift population went up and down with the area loaded around the bots (Sifters,
    sniffers, Blubs, golems, Singers, rifts), with no growth that stayed.
  - The only exceptions were the offline-mode key fetch; there were no Sift errors.

  A paired run without the client compared the same 12-minute pattern in the Sift and in the
  Overworld. The one-minute tick average was 18.9 ms in the Sift and 89.7 ms in the Overworld, where
  ticks stalled for several seconds. Sift code appeared in 8% of the server-thread samples, mostly
  Echo Golem path searches (the mod's 128-block follow range, under a shared per-tick budget).

The live runs and the real client found and fixed these bugs. Each has a test, except the golem
navigation and the exit rift's chunk hold, which were confirmed on the server:

- a Dark Sniffer model cube without all six faces broke every BetterModel reload;
- location advancements used the mod's 26.3 condition form;
- the main portal's state key was rejected by the dimension store;
- Echo Golem navigation had no body;
- the arrival search preferred a roof over a safe requested spot;
- an entity on the best rift spot cancelled the exit rift;
- exit rifts at a world spawn nobody stands in failed, because the searched chunks unloaded first (they
  are now held with plugin tickets);
- surface sculk regions (and so generated Dark Sniffers) and the Ichor snow read a populator heightmap
  that decoration does not update, so none of them generated;
- a 2x2 inventory craft made the recipe guard throw;
- rifts were invisible on clients: the mod's placeholder UVs left their cubes without faces (the
  rift image is now projected across the model, see `--project-texture`);
- the Echo Golem's pickup sound named a 26.3-only vanilla event;
- the resource pack metadata lacked the `min_format`/`max_format` 26.x requires;
- two Sift calls were newer than the pinned `paper-api 26.2.build.56-alpha`, one was Paper-only, and the
  Spigot test classpath lacked Adventure. The project's own `clean build` now passes with every test green.

Servers that already installed the editable pack keep their copies of `pack.mcmeta` and the Sift's
`sounds.json` (a new jar does not overwrite them); copy the two files from `resourcepack/` over.

**Not inspected:** the Singer's per-bone brightness and the hidden disc tooltip line, which need a person
looking at a screen and hovering over the item.

**Out of scope:** the Sift has no Bedrock support. The Geyser integration covers other modules, so
Bedrock players get no Sift models or textures.
