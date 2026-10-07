# Celular Module Design

## Objective

Celular adds a craftable 3D phone that plays short vertical videos on its screen while it is held. Players switch
videos with F (next) and Shift + F (previous), and every village hands out one phone in the first chest somebody opens.
It was built and playtested as a standalone Paper plugin and is integrated into BigCasares as the `celular` module.

## Scope

In scope:
- The Celular item: shaped recipe (8 iron ingots around 1 glass), 3D Java item model with its own texture, and
  `/celular give [jugador]` for admins.
- Videos on the screen as animated item textures (72x128, 10 fps, at most 60 seconds each), one item model per video.
- F / Shift + F video navigation with an action-bar title.
- One phone per village, in the first village chest whose loot is generated.
- Bedrock: 3D model, inventory icon and Geyser item mappings, with "Pobre" on the screen.
- Asset generators under `tools/celular/`.

Out of scope:
- Sound for the videos and per-player playback control (see Limits).
- Player-uploaded videos.
- Videos and controls on Bedrock (animated item textures do not exist there; the screen shows "Pobre" by design).
- Changing BigCasares core dependency versions or the resource-pack module code. The only change outside the module
  is the phone's item list in `geyser-integration` (see Confirmed rules).

## Confirmed rules

- Recipe: `HHH / HVH / HHH` with H = iron ingot and V = glass; result is one phone (max stack size 1).
- The phone shows the video only in first and third person hands; inventory, ground and item frames show the still
  phone (`minecraft:select` on `display_context`).
- F (swap hands) with the phone in the main hand cancels the swap and moves to the next video; Shift + F moves to the
  previous one. Both wrap around. The item definitions set `hand_animation_on_swap: false`, so the phone does not bob.
- Q is not used on Java: the client removes the item from the hand before the server answers, which plays the
  re-equip animation. Keys such as I, O or G are never sent to the server.
- Bedrock (resolved through `BigCasares#resolvePlayerPlatform`): the phone is the same item, shown through Geyser as
  a 3D model in the hand (and an inventory icon) whose screen says "Pobre". Bedrock gets no videos and no controls.
  Crafting it on Bedrock also sends the chat message "Dale bobi, no tenes java?, bancatela pibe".
- The `geyser-integration` module registers one Geyser custom item per Java item model of the phone
  (`celular:celular` and `celular:video_N`, base item `minecraft:clock`, icon `bigcasares.celular`), asking
  `BigCasares#getCelularModule()` for the list. Each Bedrock item has an attachable in BigCasares' Bedrock pack.
- Left-hand display transforms equal the right-hand ones: Minecraft mirrors the left hand itself.
- Every phone remembers its video index in its persistent data; new phones start at the first video.
- Village chests are those whose loot table is `minecraft:chests/village/*`. The first one generated inside a village
  gets a phone added to its normal loot; the village is then flagged in its own `GeneratedStructure` persistent data
  container, so the flag survives restarts.

## Architecture

```
modules/celular/
  CelularModule          PluginModule: settings, videos, recipe, listener and command through BukkitRuntimeRegistrations;
                         itemModels() gives geyser-integration the phone's Java item models
  CelularService         navigation (next/previous/clamp), action-bar text, village loot rules, the Bedrock rule and
                         message, and the list of item models; no Bukkit
  CelularSettings        record: recipe.enabled, village-loot.enabled
  CelularSettingsLoader  reads the `celular:` section of config.yml with defaults
  CelularVideo           record: id, title, frames (+ seconds at 10 fps)
  CelularVideosLoader    reads celular/videos.yml from the jar
  CelularRecipe          recipe shape and ingredients as constants
  CelularItems           builds the phone (Paper data components) and reads/writes its video index
  CelularVillages        finds the village around a chest and stores the "already gave" flag
  CelularListener        swap / held / join / craft / loot events, delegates to the service
  CelularCommand         /celular give [jugador]
```

Assets ride the BigCasares packs, with no extra HTTP server: the Java ones under `resourcepack/java/assets/celular/`
(namespace `celular`, like Don Pollos does with `donpollos`) and the Bedrock ones under `resourcepack/bedrock/`. The
only `registry.yml` entry is `celular`, for the Bedrock inventory icon (it generates the `bigcasares.celular` key of
`item_texture.json`).

Changed outside the module: `GeyserIntegrationModule` adds `celularItemDefinitions()` to the custom items it
registers, reading `plugin.getCelularModule().itemModels()`.

- `textures/item/celular.png`: 64x64 atlas for the phone. `models/item/celular.json`: 8 cuboids (body, camera module,
  2 lenses, flash, 3 buttons) with display transforms.
- Bedrock (`resourcepack/bedrock/`, generated by `tools/celular/generar_bedrock.py`): `models/entity/celular.geo.json`
  (the Java cuboids converted like java2bedrock), `animations/celular.animation.json` (hand/head transforms),
  `textures/entity/celular_bedrock.png` (screen says "Pobre"), one `attachables/celular_<item>.json` per Bedrock item,
  and the icon `resourcepack/shared/textures/item/celular_bedrock.png` registered as `celular` in `registry.yml`.
- Per video N: `textures/item/video_N.png` (+ `.mcmeta`, frametime 2), `models/item/celular_video_N.json` (phone + a
  black screen plane + the video plane, emissive) and `items/video_N.json`.

## Config shape

```yaml
modules:
  celular:
    enabled: true

celular:
  recipe:
    enabled: true
  village-loot:
    enabled: true
```

## Limits

- Animated textures loop on the client from the moment the pack loads, so switching videos does not start from the
  beginning and the server cannot sync audio; videos are silent.
- Every frame stays in client memory: about 170 MB raw for the 15 bundled videos (4630 frames), which is why videos are
  10 fps and capped at 60 seconds.
- The Java pack grows by ~28 MB.
- Villages looted before the module was installed already generated their loot and get no phone.
- The Bedrock model, its hand positions and the Geyser mapping were converted with the java2bedrock rules and never
  checked with a Bedrock client: the phone could look mirrored or badly placed in the hand.
