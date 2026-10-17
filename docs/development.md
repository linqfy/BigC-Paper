# Development

## Build and dependencies

Use JDK 25 for the Gradle launcher, compiler and tests. `build.gradle` sets
`--release 25`; selecting a compiler toolchain from an older launcher does not
configure every Java execution task. Java 25 is supported by Gradle 9.1+ and is
required by Paper 26.1+. See the [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
and [Paper requirements](https://docs.papermc.io/paper/getting-started/).

| Dependency/tool | Repository configuration | Role |
| --- | --- | --- |
| Gradle / run-paper / Foojay resolver | 9.2.1 / 3.0.2 / 1.0.0 | Wrapper, local Paper task, compiler toolchain resolution |
| Paper API | `26.2.build.56-alpha` | Compile-only API; `plugin.yml` targets `26.2` |
| BetterModel API | 3.2.0 | Compile-only; required server plugin |
| VaultAPI | 1.7 | Compile-only; enabled economy modules require a provider |
| PlaceholderAPI | 2.12.3 | Optional server plugin |
| Geyser API | `2.11.0-SNAPSHOT` | Optional bridge loaded reflectively |
| OpenSkill / JDA | 1.1 / 6.4.1 | Implementation dependencies, also declared in `plugin.yml` libraries |
| Spigot test API / JUnit BOM | `26.2-R0.1-SNAPSHOT` / 5.11.4 | Unit-test classpath and JUnit 5 |

The plugin uses the ordinary Gradle JAR, not a shaded JAR. Paper loads OpenSkill
and JDA through `plugin.yml` libraries; preserve alignment with `build.gradle`.
Alpha/SNAPSHOT coordinates and unpinned `runServer` downloads prevent a claim of
fully reproducible dependency/server resolution. No dependency locks or wrapper
distribution checksum are configured.

```bash
bash gradlew test
bash gradlew test --tests 'dev.linqfy.bigCasares.modules.teams.*'
bash gradlew --no-daemon clean build
bash gradlew generateResourcePacks
bash gradlew generateCustomBlockstates   # after changing custom blocks
bash gradlew generateSiftDatapack        # writes build/generated-datapacks/ for inspection
bash gradlew runServer
```

On Windows use `gradlew.bat`. `runServer` resolves Minecraft 26.2 into
`build/run-server`; it does not install required third-party plugins. `clean`
removes that directory, including local worlds/config/data: back up valuable
server state or use a separate server for persistent testing.

## Source map

All Java packages are under `src/main/java/dev/linqfy/bigCasares/`.

| Path | Responsibility |
| --- | --- |
| `BigCasares.java`, `command/` | Bootstrap, dependency wiring and shared command routing |
| `module/`, `module/runtime/`, `reload/` | Ordered lifecycle, resource ownership, reload results/generation guards |
| `items/`, `items/catalog/`, `modules/items/` | Custom item identity, declarative catalog, recipes and reconciliation |
| `dimension/`, `modules/dimension/` | [Dimension framework](dimensions/README.md): definitions, worldgen DSL, datapack compiler/installer, travel and portals |
| `blocks/`, `content/`, `modules/content/` | Custom full-cube blocks on mushroom states and code-defined content items |
| `modules/sift/` | [The Sift](dimensions/the-sift.md): imported mod data, feature ports, ritual and creatures |
| `modules/resourcepack/`, `modules/model/`, `platform/`, `modules/geyser/` | Pack validation/publication, BetterModel, client platform/Bedrock bridge |
| `modules/` | Economy, missions, teams/Nexus, bosses, items, events, moderation, server control and Discord |
| `src/main/resources/` | Plugin descriptor, config/default stores, boss YAML, item catalog and packaged models/media |
| `resourcepack/` | Java/Bedrock assets, shared registry and authoring sources |
| `src/test/java/` | JUnit tests, fake gateways and asset/source contracts |
| `tools/` | Manual Python generators (including the Sift import and GeckoLib model converter) and a separate Node protocol client |

`PluginModule` supports both `onEnable()` and `onEnable(RuntimeRegistrationScope)`.
The manager enables in registration order, stops/cleans a failing activation,
unwinds prior modules and disables in reverse order. Scopes retire callbacks and
close owned resources. Some direct Bukkit registration remains; bootstrap still
performs plugin-wide listener/task cleanup. Root commands are rebound separately.

Configuration switches support nested `.enabled`, flat booleans and the legacy
`shop-system` alias. Missing switches default to enabled; `danger-system`,
`death-tombstone-system`, `special-items`, `warp-system` and `mob-scaling` currently
have no explicit module switches in the shipped config. Settings prefixes can
differ from IDs. Consult each module's settings loader rather than inventing keys.

Persistence is feature-specific: missions/bounties/ratings use player files;
teams, Nexus, tombstones and warps use shared YAML; Jeremy and boss rewards have
plugin-root files. Preserve layouts and migration behavior.

## Verification and automation

`test` prepares packaged BetterModel assets under `build/run-server` before JUnit.
Tests use Spigot's API because Paper item factories need live registry access.
They include domain/lifecycle and asset contracts; they do not automatically start
a server. A passing build does not establish live Paper, Geyser or visual behavior.

`jar` depends on pack generation and a reproducible source-seed ZIP. It embeds
both generated packs under `generated-resourcepacks/` and the seed under
`seed-content/`. See [operations](operations.md) for runtime copies and reloads.

[CI](../.github/workflows/build-jar.yml) runs Java 25 and
`bash gradlew --no-daemon clean build` for main pushes/PRs, `v*` tags and manual
dispatch. It uploads the JAR (14 days) and failure test reports (7 days); tag
builds publish/update GitHub releases. Node client tests and Python generators
are not CI steps. Node/Python are unnecessary for the normal plugin build.

For the separate client use Node 22+ and its
[README](../tools/minecraft-test-client/README.md). The root npm package only
installs Codex; it has no plugin build scripts. Python dependencies and destructive
regeneration behavior are documented in the [Celular guide](celular/README.md).
