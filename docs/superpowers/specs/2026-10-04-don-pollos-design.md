# Don Pollos Module Design

## Objective

Don Pollos adds five Don Pollo NPC variants with their own shops and minigames, Don Pollo cities generated in the
world, and the Don Pollo Boss: a four-phase boss fight with a reward menu. It was built and playtested as a standalone
Paper plugin and is now integrated back into BigCasares as the `don-pollos` module.

## Scope

In scope:
- Five NPC variants (villager anchors with BetterModel models): Comun (wanders, voice clips), Gordito (bread trades and
  the "secret" menu), Salsero (commanded dances and a dance battle), Aura 67 (fried chicken shop: pollo frito, salsa,
  picante) and Fino (roulette casino).
- Don Pollo cities pasted from a schematic during world generation and populated with Don Pollos.
- The Don Pollo Boss (`/donpollo boss` or the Gordito secret block) with four phases, minions, a defeat sequence and
  a reward menu.
- Custom items (pollo frito, salsa, picante, Balde de KFC dado vuelta, secret block), voice clips and texts.
- Bedrock players through Geyser + Floodgate (fallback visuals, Bedrock pack, Geyser item mappings and optional
  GeyserModelEngine models).

Out of scope:
- Changing BigCasares core dependency versions (the module compiles against BetterModel 3.2.0 and uses 3.5.0-only
  API reflectively).
- Other modules' behaviour.

## Confirmed rules

- Comun and Gordito play a random Don Pollo voice clip when a player comes within 15 blocks.
- Gordito: right click says "Yo se cosas..." / "Queres saberlas..." / "Dame Salsa y Picante"; a second right click opens
  the secret menu (one salsa + one picante gives the secret block) with a button to the bread trades.
- Aura 67 sells 4 pollo frito for 5 bread, salsa (Strength I 10s) for 2 and picante (Speed III 10s) for 3.
- Fino roulette: red 24/50, black 24/50, green 2/50; winning color doubles the bet.
- Salsero dances only by command; its PVP button starts a dance battle (jump, sneak, directions) with a boss bar.
- Cities: one per 1400-block cell, at least 500 blocks from spawn, never on water; foundations are filled.
- Boss phases by health: FASE 1 (100%) chases at baby-zombie speed with eye lasers; FASE 2 (75%) keeps ~10 blocks away,
  throws WhatsApp meteors and spawns WhatsApp chickens; FASE 3 (50%) levitates 20 blocks inside an invulnerable blue
  sphere, digs a walled 6-deep cube under each player and runs the 3-round "Batallas del Cubo" (10/30/50 Labubus plus a
  colonel per round); FASE FINAL (25%) grows, hops, fires a warden beam at the locked position, mega-jumps with a
  shockwave and summons Sanders Fase Final.
- Winning does not kill the boss: it stays at 1 HP, cries, says five phrases and opens the reward menu (Balde de KFC
  dado vuelta plus four player-chosen stacks).
- Creative and spectator players are ignored by the boss and minions.

## Architecture

- `DonPollosModule`: PluginModule, wires listeners, tasks and the `/donpollo` command through `RuntimeRegistrationScope`.
- `DonPollosService`, `DonPollosSalseroPolicy`, `DonPollosDanceBattle`, `DonPollosCubeBattle`, `DonPollosBossPhase`,
  `DonPollosReward`, `DonPollosCityLayout`: Bukkit-free domain rules.
- `DonPollosSettings` + `DonPollosSettingsLoader` (+ boss/phase/city/battle settings records): `don-pollos.yml`.
- `DonPollosEntityFactory`, `DonPollosListener`, `DonPollosMenuController`, `DonPollosRouletteMenu`,
  `DonPollosBattleManager`, `DonPollosSecret`: NPC behaviour and menus.
- `DonPollosCityManager`, `DonPollosCityTemplate`: city generation, stored in `data/don-pollos/ciudades.yml`.
- `DonPollosBossManager`, `DonPollosBossMinions`, `DonPollosBossMeteors`, `DonPollosBossShockwaves`,
  `DonPollosRewardMenu`, `DonPollosKfcBucket`: the boss fight.
- `DonPollosModelBinder`, `DonPollosModelGateway`, `BetterModelDonPollosModelGateway`, `DonPollosModelInstaller`:
  BetterModel models (installed from `bettermodel/models/`).
- `DonPollosBedrock`: Bedrock detection (Floodgate/Geyser/UUID) and installation of the Bedrock pack, Geyser item
  mappings and GeyserModelEngine inputs.

## Config shape

- `config.yml`: `modules.don-pollos.enabled`.
- `don-pollos.yml` (saved to the BigCasares data folder): `fino.roulette`, `salsero` (+ `pvp`, `dances`), `ciudades`,
  `boss` (+ `fase-2`, `fase-3.batallas-del-cubo`, `fase-final`), `bedrock.modelos-3d` (`auto`/`true`/`false`) and
  `variants`.

## Assets

- Java items, textures and sounds: `resourcepack/java/assets/donpollos/` (shipped in the BigCasares pack, which is merged
  with BetterModel's pack by the resource-pack module).
- BetterModel models: `src/main/resources/bettermodel/models/bigcasares_*.bbmodel`.
- City structure, voice catalog and Bedrock files: `src/main/resources/donpollos/`.
- Generators and their sources: `tools/donpollos/`.

## Boundaries

- Does not touch other modules or BigCasares core classes beyond module registration, `config.yml` and `plugin.yml`.
- Does not change `build.gradle` dependency versions.
- Its Bedrock pack is installed next to (not merged into) the BigCasares Bedrock pack.
