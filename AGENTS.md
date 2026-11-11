# Repository instructions

BigCasares is a single Paper plugin owned by `linqfy`. Source, build files and
tests define current behavior; prose docs only explain it. Keep changes within
the request and see [GUIDELINES.MD](GUIDELINES.MD) for code conventions.

## Hard rules

1. **One plugin.** Never add a second `JavaPlugin`, `plugin.yml`, nested plugin
   build, sidecar JAR or dynamically loaded extension. New features are
   `PluginModule`s registered in `BigCasares.initializeRuntime()`. New
   third-party server-plugin dependencies need the owner's approval.
2. **No agent paperwork in Git.** No plans, specs, audits, reviews, TODO lists,
   status reports, handoffs or AI-attribution files, in any folder or under any
   extension. Plan in context or in the ignored `work/` folder.
3. **No generated output in Git.** No `.jar`/`.zip`/`.mcpack`, build output,
   Gradle caches, BetterModel inspection dumps or server data. Editable sources
   under `resourcepack/` and `src/main/resources/bettermodel/` are fine.
4. **Don't touch `README.md`** or other docs to advertise your work. Update a
   guide only when your change makes it wrong.
5. **Keep valuable originals.** Do not delete `agent_uploads/` (tests read the
   Sahur originals), `tmp/sahur-uv-backup/` or other uploaded sources.

Before committing, review `git status --short` and the full diff against
`origin/main`, and stage paths explicitly (no blind `git add -A`). CI runs
`tools/check_pr_hygiene.py` on pull requests to enforce rules 1–3.

## Commands

Use JDK 25. The wrapper is not executable in Git, so call it through `bash`.

```bash
bash gradlew test
bash gradlew test --tests 'dev.linqfy.bigCasares.modules.<package>.*'
bash gradlew --no-daemon clean build
bash gradlew generateResourcePacks
bash gradlew runServer
```

`runServer` uses `build/run-server` and downloads pinned, checksum-verified
BetterModel, Geyser, Floodgate and GeyserModelEngine builds. Install Vault plus
an economy provider for the economy modules. The Node test client has its own
checks: `npm --prefix tools/minecraft-test-client ci && npm --prefix
tools/minecraft-test-client test` (Node 22+).

## Architecture

- Paper 26.2, Java 25, Gradle 9.2.1; exact dependencies live in `build.gradle`.
  BetterModel is required; optional integrations are in `plugin.yml`.
- `ModuleManager` enables modules in registration order, rolls back a failed
  startup and disables in reverse. Prefer `onEnable(RuntimeRegistrationScope)`
  with `BukkitRuntimeRegistrations` so listeners, tasks and closeables are owned
  by the module generation.
- Module switches: `modules.<id>.enabled` (legacy booleans and the shop alias
  still work); missing switches default to enabled, so add explicit defaults.
  Module IDs, config prefixes and package names are not interchangeable.
- Items: `items/catalog/` + `modules/items/`, defaults in
  `src/main/resources/content/items/`. Packs: `modules/resourcepack/`, editable
  sources in `resourcepack/`.
- Plugin reload, item reload and pack reload differ in scope and failure
  handling; see [operations](docs/operations.md). A full reload has no rollback,
  so any GUI holding real items must close its viewers on disable.

## Gotchas found in this codebase

- `PlayerInteractEvent` for `RIGHT_CLICK_AIR` arrives already "cancelled", so
  `ignoreCancelled = true` silently drops air clicks. Check
  `event.useItemInHand() == Event.Result.DENY` instead.
- Menus must cancel both `InventoryClickEvent` and `InventoryDragEvent`, and
  identify themselves with an `InventoryHolder`, never the title.
- Shop/economy item matching must use the custom-item ID, never the base
  material: a vanilla compass must not sell as a `tracker_compass`.
- Keep YAML writes out of per-tick or per-click paths; debounce them.
- Bedrock players (Geyser) cannot see display-entity text or item custom model
  data without the matching Geyser mappings; test both clients.
