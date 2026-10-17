# Operations

## Install and configure

Run Paper 26.2 with Java 25. Install `BigCasares-0.0.1.jar` and a compatible
BetterModel plugin (compiled API: 3.2.0). `plugin.yml` declares BetterModel as a hard
dependency. Default-enabled Mission, Bounty and Entity Shop also need Vault and
an economy provider; their dependency failure causes plugin activation rollback.
Install them or explicitly disable those modules before enabling the plugin.

The default config is copied only when missing. Existing server configuration
does not automatically acquire new defaults on JAR upgrades. Review changes in
[`config.yml`](../src/main/resources/config.yml), the settings loaders and
[`plugin.yml`](../src/main/resources/plugin.yml) for command permissions.

PlaceholderAPI, Geyser-Spigot and floodgate are soft dependencies. Geyser has
capability checks/fallbacks; custom entity support remains experimental. Check
logs and test both clients rather than assuming animation/visual parity.
For model deployment prerequisites, see [BetterModel setup](blockbench/00-bettermodel-setup.md).

Discord reads IDs/presentation from `config.yml`, and token/webhook from
`plugins/BigCasares/discord-secrets.yml`. The secrets file is created with empty
values; missing/invalid configuration prevents the Discord connection. Keep it
local. There is no documented environment-variable substitute in this runtime.

The shipped End event is enabled with date `2026-07-25` in
`America/Argentina/Buenos_Aires`. That is deployment-specific historical config,
not a future schedule. Review `modules.end-event-system.enabled` and its settings
before deployment; the module controls End access and a one-shot event. Do not
change the date or reset persistent event state without understanding its rules.

## Editable content and pack delivery

| Server path under `plugins/BigCasares/` | Behavior |
| --- | --- |
| `content/items/` | Item YAML defaults are seeded if missing; existing files preserved |
| `content/pack/` | Editable resource-pack seed extracted if missing; existing files preserved |
| `packs/artifacts/`, `packs/active-pack.json` | Published artifacts and active pack identity |
| `packs/bigcasares-bedrock.mcpack` | Replaced from packaged output when the Geyser module extracts it |
| `data/` and feature-root YAML | Persistent gameplay/admin state; back up before upgrades |

Repository assets feed builds; runtime reloads read the server's editable copies.
Upgrading the JAR alone does not overwrite existing item/Java pack sources.
Reconcile intended asset changes into those copies, preserving local edits.
BetterModel has separate installed model sources and reload behavior.

The current Java runtime sends a stack: BigCasares content plus separately
published BetterModel output. It does not require one merged ZIP. The command-line
builder's optional merge workflow is covered in the model docs.

Publishing modes are `embedded-http`, `external-url` and `copy-only`. Shipped
embedded HTTP binds to `127.0.0.1:8123` and advertises `http://127.0.0.1:8123/`.
That only works for clients on the same machine. For remote clients, configure a
reachable public URL and a reachable listener/proxy; changing the advertised host
does not change the bind address. TLS can be provided by a reverse proxy.

For external hosting, publish the generated artifacts yourself. With
`publishing.public-base-url`, runtime URLs append artifact names. Without it,
`publishing.public-url` is a fixed BigCasares URL and
`publishing.bettermodel-public-url` can provide a distinct BetterModel URL.
The plugin does not upload these files to external hosting.
Required-pack plus `copy-only` is promoted to embedded HTTP by the settings loader.

## Reload commands

| Command | Scope and permission |
| --- | --- |
| `/bc reload` | Recreate plugin modules and rebind root commands; `bigcasares.shop.reload` |
| `/bc reload items` | Validate/install runtime item catalog, replace recipes, reconcile online stacks; `bigcasares.items.reload` |
| `/bc items info` | Catalog revision; `bigcasares.items.info` |
| `/bc reload pack` | Reload BetterModel, build/publish/activate Java content; `bigcasares.pack.reload` |
| `/bc pack info` | Active pack identity/URL; `bigcasares.pack.info` |
| `/bc pack send <player\|all>` | Request the active stack; `bigcasares.pack.send` |
| `/resourcepack` | Player-facing manual download links/update tracking |

Full reload stops the old runtime before validating/activating its replacement.
It reports failures and cleans failed candidates; it does **not** restore the old
runtime. Prefer targeted content reloads where applicable and inspect failure logs.
Pack staging protects the BigCasares active artifact, but BetterModel reload occurs
first and outside that transaction. `/bc reload pack` does not rebuild/re-register
the packaged Bedrock pack. Bedrock asset changes require a rebuilt JAR and a
Geyser lifecycle/restart check. Tests do not establish live-client behavior.

## Custom dimensions and The Sift

Dimensions are delivered as a generated datapack; see the
[dimension guide](dimensions/README.md) and [The Sift](dimensions/the-sift.md).

- After the first install, and after any change that alters the datapack (worldgen,
  `the-sift.seed-salt`, toggling the Ancient City ritual), the log asks for a
  restart. Until then `/dimension list` shows `PENDING_RESTART`. `/bc reload` does
  not add, remove or regenerate dimensions.
- `dimension-framework.datapack.install-mode: auto` installs into
  `<level>/datapacks/bigcasares-dimensions`; `manual` only writes
  `plugins/BigCasares/dimensions/generated-datapack`. Previous versions are kept in
  `plugins/BigCasares/dimensions/datapack-backups/`.
- Set `block-updates.disable-mushroom-block-updates: true` in
  `config/paper-global.yml` before players use Sift blocks; the custom-content
  module warns when it is missing.
- Back up `plugins/BigCasares/dimensions/state.yml` (return points, lit portals)
  with the world. Removing the plugin leaves the dimension datapack and world in
  place; remove the datapack folder deliberately if the dimension is retired.
