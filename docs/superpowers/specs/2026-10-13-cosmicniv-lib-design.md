# CosmicNiv Lib Module Design

## Objective

`cosmicniv_lib` is a shared BigCasares library that turns what a player (or any mob) is wearing and carrying into a
single equipment level between 0 and 200. Other modules (events, bosses, mob scaling, missions...) read that number
through `BigCasares#getCosmicNivModule()` instead of each one inventing its own gear formula.

- **0** is the "zero reference": by default a player with absolutely nothing.
- **200** is the "max reference": by default full netherite armor with every useful enchantment plus one fully
  enchanted weapon of any of the five weapon families (sword, axe, mace, spear, trident).

Everything is tuned without code from two files in the data folder:
- `cosmicniv_lib.yml`: how the level is computed (output, weights, held search, curve, references).
- `cosmicniv_valores.yml`: what everything is worth (armor values, weapon and tool families with their item values,
  enchantment values and custom items).

## Scope

In scope:
- The math model (pure Java, no Bukkit) and its two-file loader with validation and a fail-safe fallback.
- Armor, weapons and tools (pickaxe, shovel, hoe, shears, fishing rod). Tools have weight 0 by default so the
  0–200 definition above holds; admins can give them weight.
- Custom items recognized by BigCasares catalog id (`bigcasares:item_id`), `item_model` component, string persistent
  data (key, optionally value) and/or material, each with its own value, category and family.
- Reading armor, weapons and tools from a `LivingEntity` (players: worn armor plus carried pieces, hotbar and
  inventory; mobs: their equipment).
- Equipment memory and a "real level" per player (see Confirmed rules), stored per player in
  `plugins/BigCasares/data/cosmicniv-lib/players/<uuid>.yml`.
- Public API on `CosmicNivModule` for other modules.
- `/cosmicniv [jugador]`, `/cosmicniv curva` and `/cosmicniv reload`.
- A Spanish user guide: `docs/cosmicniv-lib/README.md`.

Out of scope:
- Changing how any existing module computes power (Jeremy, mob scaling, danger). They can migrate later.
- Persisting levels, leaderboards or history. Only the equipment memory behind the real level is stored.
- Durability, potion effects, attribute modifiers, bows and crossbows (bows can be added as a weapon family).
- PlaceholderAPI placeholders.

## Confirmed rules

Item power of an item with key `k` and enchantments `{e: level}`:

```
P(item) = value(k) + Σ points(e) · min(level, maxLevel(e))      if k has a value in cosmicniv_valores.yml
P(item) = 0                                                      otherwise
```

Enchantment points are summed in key order so the result does not depend on the order Bukkit returns them.

Every valued item lives in exactly one place: `armor`, one weapon family, one tool family or `custom-items`. Armor
only counts as armor; weapons and tools only count as weapons or tools. Custom items are valued under `custom:<id>`.

Armor: for each slot the best piece counts among the candidates. With `armor-search: INVENTORY` (default) the
candidates are the worn piece plus every carried piece (inventory and off hand) whose equipment slot is that slot;
with `WORN` only the worn piece. The armor score is then normalized between the references:

```
A = clamp01( (P_armor − P_armor(zero)) / (P_armor(max) − P_armor(zero)) )
```

Weapon score W and tool score T: every held weapon (tool) belongs to one family `φ`, is normalized against its own
family's references and the best one wins (0 when there is none):

```
N(item) = clamp01( (P(item) − P_φ(zero)) / (P_φ(max) − P_φ(zero)) )
```

Combined progress, curve and output:

```
t     = (wArmor · A + wWeapon · W + wTool · T) / (wArmor + wWeapon + wTool)
level = maximum · f(t)                     maximum = 200 by default
```

`f` is the configured curve. Every curve satisfies `f(0) = 0` and `f(1) = 1`, so the zero reference is always 0 and
the max reference is always 200.

| Curve | f(t) | Parameters |
|---|---|---|
| `LINEAR` | `t` | — |
| `EXPONENTIAL` | `(e^(k·t) − 1) / (e^k − 1)` | `strength` k, 1e-6 ≤ abs(k) ≤ 50 (k > 0 slow start) |
| `LOGARITHMIC` | `ln(1 + k·t) / ln(1 + k)` | `strength` k in [1e-6, 1e6] (fast start) |
| `POWER` | `t^p` | `exponent` p in (0, 100] |
| `SIGMOID` | logistic normalized to [0,1] | `strength` k in [1e-6, 100], `midpoint` m in (0,1) |
| `POINTS` | piecewise linear | `points`, x strictly increasing from 0 to 1, y non-decreasing, first y 0, last y 1 |

Other rules:
- Keys accept `netherite_sword` or `minecraft:netherite_sword`; both are normalized to the namespaced lowercase form.
- Negative enchantment points (curses) are allowed; scores are clamped so they never go below 0.
- Searches: `armor-search` `WORN` or `INVENTORY` (default); `weapon-search` and `tool-search` `MAIN_HAND`,
  `HANDS`, `HOTBAR` or `INVENTORY` (defaults `HOTBAR` and `INVENTORY`). Mobs always use their equipment.
- A part with weight 0 needs no references and scores 0 when its references are missing.
- Values and points must be within ±1,000,000; keys that collide after normalization are errors.
- `output.maximum` must be representable with `output.decimals` (200.06 with 1 decimal is rejected), so the rounded
  level never exceeds the maximum and the max reference always reaches it.
- An armor piece only counts in its own slot: vanilla keys by name (`*_helmet`, `*_chestplate`, `*_leggings`,
  `*_boots`, heads/pumpkin, elytra), custom armor by its optional `slot` or its `match.material`. References are
  validated the same way. Hand-built loadouts with, e.g., four chestplates only count the chestplate slot.
- Custom items: the first match in file order wins; every set condition must hold; at least one must be set.
- 200 means the reference kit, not just as many points: each default armor/weapon/tool reference carries every
  compatible enchantment that has points (thorns included), so with the factory values no combination of
  lower-material pieces reaches it. The level measures equipment only; it is not a threat score (skill and danger
  belong to their own modules) and does not look into Ender Chests or containers.
- Default reference enchantments follow the vanilla 26.2 data (`data/minecraft/enchantment/*.json` and the
  `enchantable/*` item tags) so every max reference can be obtained in survival:
  - armor: protection, thorns, plus respiration and aqua affinity (helmet), swift sneak (leggings), feather falling,
    depth strider and soul speed (boots);
  - sword: sharpness, fire aspect, knockback, looting, sweeping edge;
  - axe: sharpness, efficiency, fortune;
  - mace: density, wind burst, fire aspect;
  - spear: sharpness, lunge, fire aspect, knockback, looting;
  - trident: impaling, loyalty, channeling;
  - pickaxe, shovel, hoe: efficiency, fortune;
  - shears: efficiency;
  - fishing rod: luck of the sea, lure;
  - and unbreaking III plus mending on all of them.
- Equipment memory (`memory:` in `cosmicniv_lib.yml`): per player and per memory slot (four armor slots, best
  weapon, best tool) the best item seen is remembered. A better item replaces it; an equally strong one only counts
  as seeing it now (so ties never rewrite the file); an empty slot or a worse item keeps it. Armor compares by power,
  weapons and tools by their family-normalized score. Armor pieces with negative power never count (an empty slot is
  worth 0) and are never remembered. A remembered item is forgotten after `forget-after-minutes` (real time) without
  being seen, on a non-cancelled death when `forget-on-death` (nothing is remembered until the respawn, because the
  inventory empties after the death event), and when it no longer has a value. Online players are checked every
  `check-interval-ticks`, plus on every real-level request and on quit. Real level = evaluate(current candidates +
  remembered items), so it is never below the current level. Mobs have no memory. Durability is not part of an
  item, so it never changes the memory. Files are written when remembered items change, every 5 minutes when only
  "last seen" times moved, on quit and on module disable; unreadable files are kept as `<uuid>.yml.broken-<millis>`.
- Validation errors are reported as `<file> > <yaml path>: <message>`. When the installed files are invalid, the
  module keeps the last valid settings (carried across `/bigcasares reload`, which builds a new module instance) or,
  if there never were any, the bundled defaults. Unknown item or enchantment keys only produce warnings.

## Architecture

- `CosmicNivModule`: the `PluginModule`. Copies both files, loads them with the fallback, binds the command and
  exposes the API (`evaluate`, `level`, `readLoadout`, `isAvailable`, `maximum`, `service`).
- `CosmicNivService`: the model and custom item recognition (`identify`). No Bukkit.
- `CosmicNivSettings` + `CosmicNivSettingsLoader` + `CosmicNivConfigLoadResult`: immutable settings, parsing of both
  files, errors and warnings.
- `CosmicNivCurve` (sealed) + `CosmicNivCurveType`: the curves.
- `CosmicNivCustomItem`, `CosmicNivCustomMatch`, `CosmicNivItemIdentity`: custom items and how they are recognized.
- Value types: `CosmicNivItem`, `CosmicNivLoadout`, `CosmicNivArmorSlot`, `CosmicNivItemCategory`,
  `CosmicNivReference`, `CosmicNivEnchantmentValue`, `CosmicNivSearch`, `CosmicNivArmorSearch`,
  `CosmicNivHeldSearch`, `CosmicNivResult`, `CosmicNivKeys`.
- `BukkitCosmicNivEquipmentReader`: converts a `LivingEntity` into a `CosmicNivLoadout`, reading material,
  `bigcasares:item_id`, `item_model` and the persistent data keys custom items ask for.
- `BukkitCosmicNivKeyAudit`: warns about item and enchantment keys that do not exist on the server.
- `CosmicNivMemoryService` (pure rules), `CosmicNivMemoryTracker` (online cache, saves on item changes, on quit and
  on module disable), `CosmicNivMemory`, `CosmicNivMemorySlot`, `CosmicNivMemorySettings`, `CosmicNivMemoryStorage`
  + `YamlCosmicNivMemoryStorage`, `CosmicNivMemoryListener` (death, quit).
- `CosmicNivCommand`: `/cosmicniv`.

## Config shape

`config.yml`:

```yaml
modules:
  cosmicniv-lib:
    enabled: true
```

`cosmicniv_lib.yml`:

```yaml
output: { maximum: 200.0, decimals: 1 }
weights: { armor: 0.6, weapon: 0.4, tool: 0.0 }
armor-search: INVENTORY
weapon-search: HOTBAR
tool-search: INVENTORY
memory: { enabled: true, forget-after-minutes: 60, forget-on-death: true, check-interval-ticks: 20 }
curve: { type: LINEAR, strength: 3.0, exponent: 2.0, midpoint: 0.5, points: [[0, 0], [0.5, 0.3], [1, 1]] }
references:
  zero: { armor: {}, weapons: {}, tools: {} }
  max:
    armor: { helmet: { item: ..., enchantments: { ... } }, chestplate: ..., leggings: ..., boots: ... }
    weapons: { sword: ..., axe: ..., mace: ..., spear: ..., trident: ... }
    tools: { pickaxe: ..., shovel: ..., hoe: ..., shears: ..., fishing_rod: ... }
```

`cosmicniv_valores.yml`:

```yaml
armor: { netherite_helmet: 7.0, ... }
weapons: { sword: { netherite_sword: 8.0, ... }, axe: { ... }, mace: { mace: 6.0 }, spear: { ... }, trident: { ... } }
tools: { pickaxe: { ... }, shovel: { ... }, hoe: { ... }, shears: { shears: 2.0 }, fishing_rod: { fishing_rod: 2.0 } }
enchantments: { protection: { points-per-level: 1.5, max-level: 4 }, ... }
custom-items:
  sahurs_bat: { match: { bigcasares-item: sahurs_bat }, category: weapon, family: sword, value: 9.0 }
```

## Boundaries

- No changes to `build.gradle` or to other modules' code. The only shared files touched are `BigCasares.java`
  (field, getter, construction, registration), `config.yml`, `plugin.yml` and `README.md`.
- The catalog id is read straight from the item's persistent data (`bigcasares:item_id`); no catalog classes are
  imported.
- Levels are computed on demand on the main thread. The only persistence is the per-player equipment memory, and
  the only scheduled task is its periodic check.
