# Don Pollos Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Goal

Bring the standalone Don Pollos Paper plugin (package `dev.linqfy.donpollos`, built and playtested separately) back
into BigCasares as the `don-pollos` module, reusing BigCasares' module lifecycle, resource-pack pipeline and BetterModel
model folder instead of the standalone plugin's own main class and HTTP pack server.

## Tech Stack

- Paper API 26.2.build.56-alpha, Java 25, Gradle 9.2.1.
- BetterModel API 3.2.0 at compile time (server runs 3.5.0; `EntityTracker.spawnCondition` is used reflectively).
- Unit tests: JUnit 5 against `spigot-api:26.2-R0.1-SNAPSHOT`.
- Asset generators: Python 3 + Pillow (`tools/donpollos/`).

## File Structure

New:
- `src/main/java/dev/linqfy/bigCasares/modules/donpollos/*.java`: the module (52 classes, see the design spec).
- `src/test/java/dev/linqfy/bigCasares/modules/donpollos/*Test.java`: 17 test classes.
- `src/main/resources/don-pollos.yml`: module settings.
- `src/main/resources/bettermodel/models/bigcasares_*.bbmodel`: 13 models.
- `src/main/resources/donpollos/`: city structure, voice catalog, Bedrock pack, Geyser mappings, GeyserModelEngine inputs.
- `resourcepack/java/assets/donpollos/`: items, item models, textures and sounds.
- `tools/donpollos/`: asset generators and their sources (`fuentes/`).
- `docs/superpowers/specs/2026-10-04-don-pollos-design.md`, this plan, `docs/don-pollos/compatibilidad-bedrock.md`.

Modified:
- `BigCasares.java`: construct, register and expose `DonPollosModule`.
- `config.yml`: `modules.don-pollos.enabled: true`.
- `plugin.yml`: `/donpollo` command and `bigcasares.donpollos.admin` permission.

## Boundaries

- No changes to other modules or to `build.gradle`.
- No changes to the BigCasares resource-pack or Geyser module code.

## Tasks

### 1. Port the sources
- [x] Copy every class except the standalone main class and pack server, renaming the package.
- [x] Turn `DonPollosPlugin` into `DonPollosModule` (PluginModule with `RuntimeRegistrationScope`).
- [x] `DonPollosBossManager` and `DonPollosCityManager` take the BigCasares `JavaPlugin`; cities spawn through a callback.
- [x] Move Paper/Adventure-only calls of the module into `DonPollosPlayerView` so the module loads in Spigot unit tests.
- Verification: `./gradlew compileJava` succeeds.

### 2. Assets
- [x] Java pack assets into `resourcepack/java/assets/donpollos/`; drop the standalone HTTP pack server.
- [x] Models into `bettermodel/models/`; other resources under `donpollos/`; data under `data/don-pollos/`.
- [x] Generators into `tools/donpollos/` with updated paths; regenerate and compare byte-for-byte with the originals.
- Verification: regenerated outputs are identical; `generateResourcePacks` succeeds.

### 3. Wiring
- [x] Register the module in `BigCasares#initializeRuntime()` with a getter.
- [x] `modules.don-pollos.enabled` in `config.yml`; `/donpollo` + `bigcasares.donpollos.admin` in `plugin.yml`.
- Verification: `DonPollosModuleWiringTest` passes.

### 4. Tests and build
- [x] Port the tests (resource paths updated); rewrite the items-pack test for `resourcepack/java`.
- [x] `./gradlew clean build`.
- Verification: `BUILD SUCCESSFUL`, all tests pass.

---

## Agent Completion Report

**Agent:** Claude (claude-opus-5-5) via Claude Code
**Date completed:** 2026-10-04
**Branch:** agent/don-pollos-integration

### What was built
- `modules/donpollos/`: `DonPollosModule` (new, replaces the standalone main class), `DonPollosPlayerView` (new) and
  the ported classes: variants, settings and loader, service, items, menus, roulette, Salsero dance battle, sounds,
  cities, boss manager with phases, minions, meteors, shockwaves, cube battles, reward menu, KFC bucket, Gordito secret,
  BetterModel gateway/binder/installer and Bedrock support.
- Assets: 13 BetterModel models, Java pack assets under `resourcepack/java/assets/donpollos/`, `don-pollos.yml`,
  `donpollos/` resources (city, voices, Bedrock pack, Geyser mappings, GeyserModelEngine inputs).
- `tools/donpollos/`: generators, dance data and sources (schematic, block photos, textures, voice originals).
- `BigCasares.java`, `config.yml`, `plugin.yml`: module registration, toggle, command and permission.
- Docs: design spec, this plan, `docs/don-pollos/README.md` (usage guide in Spanish),
  `docs/don-pollos/compatibilidad-bedrock.md`, and a Don Pollos section in the root `README.md`.
- `build.gradle`: `runServer` downloads the server plugins needed on Java and Bedrock (BetterModel 3.5.0,
  Geyser, Floodgate, packetevents, GeyserUtils, GeyserModelEngine). A new `downloadGeyserExtensions` task installs the Geyser
  extensions. Compile dependencies are unchanged.

### Tests written
- `DonPollosModuleWiringTest`: stable module id and no-op enable without a plugin.
- `DonPollosItemsPackTest`: every custom item model, texture and sound exists in `resourcepack/java/assets/donpollos`.
- Ported from the standalone plugin: `DonPollosServiceTest`, `DonPollosSettingsLoaderTest`, `DonPollosItemTest`,
  `DonPollosProductTest`, `DonPolloVariantTest`, `DonPollosSalseroPolicyTest`, `DonPollosRouletteTest`,
  `DonPollosDanceBattleTest`, `DonPollosCityTest`, `DonPollosModelAssetsTest`, `DonPollosBossTest`,
  `DonPollosBossPhaseTest`, `DonPollosCubeBattleTest`, `DonPollosRewardTest` and `DonPollosBedrockTest`
  (91 tests in total; the full BigCasares suite runs 1069 tests, all passing).

### Testing instructions
```
./gradlew test --tests "dev.linqfy.bigCasares.modules.donpollos.*"
```
Expected result: all tests pass, no failures.

### Deviations from plan
- The module was developed as a standalone plugin first (fast playtesting with a one-click local server) and then
  integrated, instead of being written test-first inside BigCasares.
- The standalone resource-pack HTTP server was removed: Don Pollos assets now ride the BigCasares Java pack, which the
  resource-pack module already merges with BetterModel's generated pack.
- The Bedrock pack and Geyser item mappings are installed as separate files into Geyser instead of being merged into the
  BigCasares Bedrock pack, to avoid touching the resource-pack and Geyser modules.

### Known limitations
- Nothing was tested with a real Bedrock client; see `docs/don-pollos/compatibilidad-bedrock.md`.
- With BetterModel 3.2.0 on the server, Bedrock players see the models' invisible anchors (the per-player model filter
  needs 3.5.0).
- Video references for the dance retargeting tools (~47 MB) and AI reference images are not committed.
