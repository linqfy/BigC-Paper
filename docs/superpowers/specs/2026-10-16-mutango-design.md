# Mutango Boss (Don Pollos, la secuela): design

## Goal

Bring the standalone Mutango Paper plugin (package `dev.linqfy.mutango`, built and playtested separately) into
BigCasares as the `mutango` module: a three-phase boss with its shrine, rewards and full Bedrock support, using
BigCasares' module lifecycle, resource-pack pipeline and BetterModel model folder instead of the standalone plugin's own
main class and HTTP pack servers.

## Scope

In scope:

- The boss: Lef, Lihuen and Lican (phases 1 and 2), Neguechen with the Sun and the Moon (phase 3), hands and animals
  attacks, armor tiers, protected-zone countdown and mega ray, logarithmic difficulty, death messages.
- The shrine in generated ruins (`structures/ruinas.json.gz`), the awakening sequence, the screen poem.
- The rewards (Botas de Neguechen, Cruz Andina), victories and rewards that survive a crash (`MutangoPending`).
- Block safety: the rift and the hand waves ask protection plugins first (`EntityChangeBlockEvent`), relocate to the
  nearest allowed spot and journal every block (`MutangoBlockJournal`) so a crash cannot leave holes.
- Bedrock: GeyserModelEngine models and Geyser custom items for the two rewards.
- Cheat attempts with the boots are sent to the existing `moderation-system` as `AbuseSignal`s.

Out of scope: changes to other modules, `build.gradle`, the resource-pack module or the Geyser module. No dependency
version changes.

## Decisions

- **Module id** `mutango`, package `modules/mutango`, class prefix `Mutango` (like `DonPollos`). `MutangoModule` is wiring
  only; the rest is split by job (`MutangoFights`, `MutangoCombat`, `MutangoRewards`, `MutangoRecovery`, the phases).
- **Independent fights.** Each summoning is its own `MutangoFight` (own difficulty, members, battle, phase three,
  cinematic). Every entity stores its fight id, so a fight is rebuilt after a restart.
- **No own pack server.** The standalone plugin served two HTTP packs on ports 8164/8165. Here the reward textures live in
  `resourcepack/java/assets/mutango/` and the models travel in the BetterModel pack that `ResourcePackModule` already
  merges and serves.
- **BetterModel API 3.2.0 at compile time.** `EntityTracker.spawnCondition` (3.5.0) is called reflectively, as
  Don Pollos does, to hide the 3D model from Bedrock players without GeyserModelEngine.
- **Moderation.** `MutangoModeration` takes a supplier of `ModerationModule::acceptSignal`, resolved at report time (the
  moderation module is created after this one); no reflection.
- **Configuration** in `mutango.yml` (copied on first run, only the `bedrock` section); persistent data in
  `plugins/BigCasares/data/mutango/`. PDC keys use the BigCasares namespace.
- **Floors.** `MutangoGround` replaces `getHighestBlockYAt`/`getHighestBlockAt`: in worlds with a ceiling (Nether) they
  return the roof, so the floor under the fight's height is searched instead.
- **Ruins** are skipped when any column would need more than 12 blocks of fill (caves and ravines) or sit on water;
  `/mutango ruinas aqui` refuses in worlds with a ceiling. `ruinas.yml` is versioned and the old format still loads.
- **Bedrock shared with Don Pollos.** Both modules write their own models into the GeyserModelEngine `input/` folder;
  each leaves files that already contain what it wants (the extension rewrites `config.json`).

## Commands and permissions

`/mutango [quitar|atacar|fase|ruinas|santuario|premios]`, permission `bigcasares.mutango.admin` (default op, child of
`bigcasares.*`). Module switch: `modules.mutango.enabled: true`.

## Affected paths

- New: `modules/mutango/` (main and test), `src/main/resources/mutango.yml`, `src/main/resources/mutango/` (Bedrock
  models, relic icons pack, ruins structure), `src/main/resources/bettermodel/models/` (28 models),
  `resourcepack/java/assets/mutango/`, `docs/mutango/README.md`, this spec and its plan.
- Modified: `BigCasares.java` (construct, register, expose the module), `config.yml`, `plugin.yml` (command, permission,
  `GeyserModelEngine` softdepend so it loads first).

## Validation

`gradlew clean build` (all tests), plus a live Paper 26.2 boot with BetterModel and BigCasares to confirm the module
enables and the models install. Not verified: fights with players, Bedrock clients, behavior in the Nether.
