"""Contenido de Alex's Caves que genera generate_content.py, agregado bioma por bioma.

BLOCKS: bloques respaldados por estados vanilla. ``variants`` va del nombre
de variante del módulo a la variante del blockstate del mod que se dibuja.
``base`` (por defecto note_block): tripwire para los que no tienen colisión ni son
cubos, chorus_plant para los sólidos que no son cubos (ver block_bases.py); puede
ser {variante: base} con "*" para el resto.
Con ``blocks.packet-remap`` y packetevents, los estados que no son cubos completos y los bloques de
WATERLOGGABLE colocados en agua se mandan a los clientes Java como otro bloque vanilla (ver
block_bases.client_remap).
Las máquinas del mod quedan afuera por decisión del usuario (D4).
ITEMS: ítems sin bloque (``material`` = material vanilla base del ítem custom).
SUBSTITUTES: bloques con forma (escaleras, losas, muros...) que se colocan como
el bloque vanilla ``material`` y solo cambian el ícono del ítem.
``smooth_limestone_slab`` usa ``PETRIFIED_OAK_SLAB`` (inobtenible en supervivencia): el pack del módulo
lo redibuja como la losa del mod (ver ``write_petrified_slab`` en generate_content.py).
``recipe``: nombre de la receta del mod (por defecto, el id); None = sin receta.
"""

AXIS = {"x": "axis=x", "y": "axis=y", "z": "axis=z"}
FACING = {f: "facing=" + f for f in ("down", "up", "north", "south", "west", "east")}
MAGNET = {f: "facing=%s,powered=false" % f for f in ("down", "up", "north", "south", "west", "east")}

BLOCKS = {
    # Magnetic Caves
    "galena": {"variants": {"default": ""}},
    "packed_galena": {"variants": {"default": ""}},
    "galena_bricks": {"variants": {"default": ""}},
    "galena_iron_ore": {"variants": {"default": ""}, "recipe": None},
    "galena_pillar": {"variants": {"x": "axis=x,shape=0", "y": "axis=y,shape=0", "z": "axis=z,shape=0"},
                      "bedrock_texture": "block/galena_pillar_single"},
    "energized_galena_neutral": {"variants": {"default": ""}},
    "energized_galena_scarlet": {"variants": {"default": ""}},
    "energized_galena_azure": {"variants": {"default": ""}},
    "scarlet_neodymium_node": {"variants": dict(FACING)},
    "azure_neodymium_node": {"variants": dict(FACING)},
    "scarlet_neodymium_pillar": {"variants": {f: "facing=%s,top=false" % f for f in FACING},
                                 "bedrock_texture": "block/neodymium/scarlet_pillar_side"},
    "azure_neodymium_pillar": {"variants": {f: "facing=%s,top=false" % f for f in FACING},
                               "bedrock_texture": "block/neodymium/azure_pillar_side"},
    "block_of_scarlet_neodymium": {"variants": {"default": ""}},
    "block_of_azure_neodymium": {"variants": {"default": ""}},
    "scarlet_magnet": {"variants": dict(MAGNET)},
    "azure_magnet": {"variants": dict(MAGNET)},
    "tesla_bulb": {"variants": {"up": "down=false", "down": "down=true"}},
    "metal_swarf": {"variants": {"default": ""}, "recipe": "metal_swarf"},
    "scrap_metal": {"variants": {"default": ""}, "recipe": None},
    "scrap_metal_plate": {"variants": {"default": ""}},
    "magnetic_activator": {"variants": dict(FACING)},
    "heart_of_iron": {"variants": dict(AXIS), "recipe": None},
    # Primordial Caves (parte 1: roca, ámbar y luces)
    "limestone": {"variants": {"default": ""}, "recipe": None},
    "smooth_limestone": {"variants": {"default": ""}, "recipe": None},
    "limestone_chiseled": {"variants": dict(FACING)},
    "limestone_pillar": {"variants": dict(AXIS), "bedrock_texture": "block/limestone_pillar"},
    "amber": {"variants": {"default": ""}, "recipe": None},
    "ambersol": {"variants": {"default": ""}, "recipe": None},
    "fern_thatch": {"variants": {"default": ""}, "bedrock_texture": "block/fern_thatch_side"},
    "carmine_froglight": {"variants": dict(AXIS), "recipe": None, "bedrock_texture": "block/carmine_froglight_side"},
    # Primordial Caves (parte 2: madera pewen)
    "pewen_log": {"variants": dict(AXIS), "recipe": None, "bedrock_texture": "block/pewen/pewen_log"},
    "pewen_wood": {"variants": dict(AXIS), "bedrock_texture": "block/pewen/pewen_log"},
    "stripped_pewen_log": {"variants": dict(AXIS), "recipe": None, "bedrock_texture": "block/pewen/stripped_pewen_log"},
    "stripped_pewen_wood": {"variants": dict(AXIS), "bedrock_texture": "block/pewen/stripped_pewen_log"},
    "pewen_planks": {"variants": {"default": ""}, "bedrock_texture": "block/pewen/pewen_planks"},
    # Toxic Caves (parte 1: roca, uranio, azufre y desechos)
    "radrock": {"variants": {"default": ""}, "recipe": None},
    "radrock_bricks": {"variants": {"default": ""}},
    "radrock_chiseled": {"variants": {"default": ""}},
    "radrock_uranium_ore": {"variants": {"default": ""}, "recipe": None},
    "acidic_radrock": {"variants": {"default": ""}, "recipe": None},
    "block_of_uranium": {"variants": {"default": ""}},
    "sulfur": {"variants": {"default": ""}},
    "cinder_block": {"variants": {"default": ""}},
    "unrefined_waste": {"variants": {"default": ""}, "recipe": None},
    "rusty_scrap_metal": {"variants": {"default": ""}, "recipe": None},
    "rusty_scrap_metal_plate": {"variants": {"default": ""}},
    # Toxic Caves (parte 4: chimeneas geotermales y cristales de azufre)
    "geothermal_vent": {"variants": {"default": ""}, "bedrock_texture": "block/geothermal_vent"},
    "geothermal_vent_medium": {"variants": {"default": ""}, "bedrock_texture": "block/geothermal_vent_medium"},
    "geothermal_vent_thin": {"variants": {"default": ""}, "bedrock_texture": "block/geothermal_vent_thin"},
    "sulfur_bud_small": {"variants": dict(FACING), "recipe": None, "bedrock_texture": "block/sulfur_bud_small"},
    "sulfur_bud_medium": {"variants": dict(FACING), "recipe": None, "bedrock_texture": "block/sulfur_bud_medium"},
    "sulfur_bud_large": {"variants": dict(FACING), "recipe": None, "bedrock_texture": "block/sulfur_bud_large"},
    "sulfur_cluster": {"variants": dict(FACING), "recipe": None, "bedrock_texture": "block/sulfur_cluster"},
    # Primordial Caves (volcán)
    "flood_basalt": {"variants": dict(AXIS), "recipe": None, "bedrock_texture": "block/flood_basalt"},
    "primal_magma": {"variants": {"default": "active=false", "active": "active=true", "boss": "active=true"},
                     "bedrock_texture": "block/primal_magma"},
    "volcanic_core": {"variants": {"default": ""}, "recipe": None},
    # Hito 7 (Luxtructosaurus): la magma que enciende el jefe (active=true sin permanent) y las fisuras de su pisotón.
    "fissure_primal_magma": {"variants": {"r%d" % i: "" for i in range(5)}, "recipe": None,
                             "bedrock_texture": "block/primal_magma_active", "item_texture": "block/primal_magma_active"},
    "amber_monolith": {"variants": {"default": ""}, "bedrock_texture": "block/amber_monolith_center"},
    # Bloques de las ruinas magnéticas y tóxicas (plantillas del mod). Los barriles del mod guardan ítems; acá son
    # solo bloques. Las lámparas de radón no dan luz (un note block no emite luz).
    "metal_barrel": {"variants": {f: "facing=%s,open=false" % f for f in FACING}},
    "rusty_barrel": {"variants": {f: "facing=%s,open=false" % f for f in FACING}, "recipe": None},
    "waste_drum": {"variants": dict(FACING), "recipe": None},
    "metal_scaffolding": {"variants": {"default": "bottom=false"}, "recipe": None},
    "rusty_scaffolding": {"variants": {"default": "bottom=false"}, "recipe": None},
    "radon_lamp_green": {"variants": {"default": ""}},
    "radon_lamp_lime": {"variants": {"default": ""}},
    "radon_lamp_white": {"variants": {"default": ""}},
    "radon_lamp_orange": {"variants": {"default": ""}},
    "radon_lamp_magenta": {"variants": {"default": ""}},
    "radon_lamp_light_blue": {"variants": {"default": ""}},
    "radon_lamp_yellow": {"variants": {"default": ""}},
    "radon_lamp_pink": {"variants": {"default": ""}},
    "radon_lamp_gray": {"variants": {"default": ""}},
    "radon_lamp_light_gray": {"variants": {"default": ""}},
    "radon_lamp_cyan": {"variants": {"default": ""}},
    "radon_lamp_purple": {"variants": {"default": ""}},
    "radon_lamp_blue": {"variants": {"default": ""}},
    "radon_lamp_brown": {"variants": {"default": ""}},
    "radon_lamp_red": {"variants": {"default": ""}},
    "radon_lamp_black": {"variants": {"default": ""}},
    # Cubos simples del mod (bloques de las ruinas y de la fosa): la perla, el material de hazmat y sus variantes.
    "hazmat_block": {"variants": dict(AXIS), "bedrock_texture": "block/hazmat_block"},
    "hazmat_warning_block": {"variants": dict(AXIS), "bedrock_texture": "block/hazmat_warning_block"},
    "hazmat_skull_block": {"variants": dict(AXIS), "bedrock_texture": "block/hazmat_skull_block"},
    # Candy Cavity (parte 1: terreno, rocas de caramelo, dulces de las decoraciones y estructuras). Las plantas del
    # mod sin colisión (sundrop, sweet puff, brotes, lollipop bunch, frostmint, sprinkles, lianas de licoroot) quedan
    # para cuando haya bloques sin colisión.
    "block_of_chocolate": {"variants": dict(AXIS), "recipe": None, "bedrock_texture": "block/block_of_chocolate"},
    "block_of_frosted_chocolate": {"variants": {"default": ""}, "recipe": None},
    "block_of_chocolate_frosting": {"variants": {"default": ""}, "recipe": None},
    "block_of_frosting": {"variants": {"default": ""}, "recipe": None},
    "block_of_vanilla_frosting": {"variants": {"default": ""}, "recipe": None},
    "cake_layer": {"variants": dict(AXIS), "recipe": None},
    "cookie_block": {"variants": {"default": ""}, "recipe": None},
    "dough_block": {"variants": {"default": ""}, "recipe": None},
    "wafer_cookie_block": {"variants": {"default": ""}, "recipe": None},
    "gingerbread_block": {"variants": {"default": ""}, "recipe": None},
    "gingerbread_bricks": {"variants": {"default": ""}, "recipe": None},
    "frosted_gingerbread_block": {"variants": {"default": ""}, "recipe": None},
    "frosted_gingerbread_bricks": {"variants": {"default": ""}, "recipe": None},
    "candy_cane_block": {"variants": dict(AXIS), "recipe": None},
    "chiseled_candy_cane_block": {"variants": dict(AXIS), "recipe": None},
    "stripped_candy_cane_block": {"variants": dict(AXIS), "recipe": None},
    # The pole's hook (mod CandyCaneFeature): bent towards one side.
    "candy_cane_pole": {"variants": {"default": "north=false,south=false,east=false,west=false",
                                     "north": "north=true", "east": "east=true", "south": "south=true",
                                     "west": "west=true"}, "recipe": None},
    "stripped_candy_cane_pole": {"variants": {"default": "north=false,south=false,east=false,west=false"}, "recipe": None},
    "block_of_polished_chocolate": {"variants": {"default": ""}, "recipe": None},
    "block_of_chiseled_chocolate": {"variants": {"default": ""}, "recipe": None},
    "chocolate_ice_cream": {"variants": {"0": "type=0", "1": "type=1", "2": "type=2"}, "recipe": None},
    "vanilla_ice_cream": {"variants": {"0": "type=0", "1": "type=1", "2": "type=2"}, "recipe": None},
    "sweetberry_ice_cream": {"variants": {"0": "type=0", "1": "type=1", "2": "type=2"}, "recipe": None},
    "giant_sweetberry": {"variants": {"default": "rotation=0"}, "recipe": None},
    "licoroot": {"variants": dict(AXIS), "recipe": None},
    "small_peppermint": {"variants": dict(FACING), "recipe": None},
    "large_peppermint": {"variants": dict(FACING), "recipe": None},
    "gummy_ring_red": {"variants": dict({f: "facing=%s,floating=true" % f for f in FACING}, sunk="facing=up,floating=false"),
                        "recipe": None},
    "gummy_ring_green": {"variants": dict({f: "facing=%s,floating=true" % f for f in FACING}, sunk="facing=up,floating=false"),
                        "recipe": None},
    "gummy_ring_blue": {"variants": dict({f: "facing=%s,floating=true" % f for f in FACING}, sunk="facing=up,floating=false"),
                        "recipe": None},
    "gummy_ring_yellow": {"variants": dict({f: "facing=%s,floating=true" % f for f in FACING}, sunk="facing=up,floating=false"),
                        "recipe": None},
    "gummy_ring_pink": {"variants": dict({f: "facing=%s,floating=true" % f for f in FACING}, sunk="facing=up,floating=false"),
                        "recipe": None},
    "rock_candy_white": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_orange": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_magenta": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_light_blue": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_yellow": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_lime": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_pink": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_gray": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_light_gray": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_cyan": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_purple": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_blue": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_brown": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_green": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_red": {"variants": {"default": ""}, "recipe": None},
    "rock_candy_black": {"variants": {"default": ""}, "recipe": None},
    # Abyssal Chasm (Hito 5, parte 1): roca de la fosa, barro del fondo y la vida del fondo marino.
    "abyssmarine": {"variants": {"default": ""}, "recipe": None},
    "muck": {"variants": {"default": ""}, "recipe": None},
    "block_of_pearl": {"variants": {"default": ""}},
    "mussel": {"base": "tripwire", "variants": {"%s_%d" % (f, n): "facing=%s,mussels=%d" % (f, n)
                            for f in ("up", "down", "north", "south", "west", "east") for n in range(1, 6)},
               "recipe": None},
    "tube_worm": {"base": "chorus_plant", "variants": {"%s_%s" % (t, f): "type=%s,facing=%s" % (t, f)
                               for t in ("straight", "turn", "elbow") for f in ("north", "south", "west", "east")},
                  "recipe": None},
    "ping_pong_sponge": {"base": "tripwire", "variants": {"default": "top=false", "top": "top=true"}, "recipe": None},
    # Abyssal Chasm (parte 2): huesos de ballena y abyssmarine labrado de las ruinas.
    "baleen_bone": {"base": "chorus_plant", "variants": {"default": "x=false", "x": "x=true"}, "recipe": None},
    "bone_nodule": {"variants": dict(FACING), "recipe": None},
    "bone_ribs": {"base": "chorus_plant", "variants": {"%s_%s" % (f, u): "facing=%s,under=%s" % (f, u)
                               for f in ("north", "south", "west", "east") for u in ("false", "true")}, "recipe": None},
    "hollow_bone": {"variants": dict(AXIS), "recipe": None},
    "smooth_bone": {"variants": {"default": ""}, "recipe": None},
    "thin_bone": {"variants": {"%s_%d" % (a, o): "axis=%s,offset=%d" % (a, o) for a in ("x", "y", "z") for o in range(3)},
                  "recipe": None},
    "bone_worms": {"base": "tripwire", "variants": dict(FACING), "recipe": None},
    "abyssal_altar": {"variants": {"default": "active=false", "active": "active=true"}, "recipe": None},
    "abyssmarine_bricks": {"variants": {"default": "active=false", "active": "active=true"}, "recipe": None},
    "abyssmarine_pillar": {"variants": {"%s%s" % (a, "_active" if act == "true" else ""): "axis=%s,active=%s" % (a, act)
                                        for a in ("x", "y", "z") for act in ("false", "true")}, "recipe": None},
    "abyssmarine_tiles": {"variants": {"default": "active=false", "active": "active=true"}, "recipe": None},
    "copper_valve": {"variants": {"default": ""}, "recipe": None},
    # Abyssal Chasm (parte 4): anémonas, que salen del trueque con los profundos.
    "dusk_anemone": {"base": "tripwire", "variants": {"default": ""}, "recipe": None},
    "twilight_anemone": {"base": "tripwire", "variants": {"default": ""}, "recipe": None},
    "midnight_anemone": {"base": "tripwire", "variants": {"default": ""}, "recipe": None},
    # Toxic Caves (parte 5: munición del raygun y bomba nuclear)
    "uranium_rod": {"variants": dict(AXIS), "bedrock_texture": "block/uranium_rod"},
    "nuclear_bomb": {"variants": {"default": ""}, "bedrock_texture": "block/nuclear_bomb_side_0"},
    # Forlorn Hollows (Hito 6, parte 1: terreno). Las ramas de thornwood no tienen colisión en el mod: van en tripwire. El underweed (planta sin colisión) no se
    # dibuja todavía.
    "guanostone": {"variants": {"default": ""}, "recipe": None},
    "guanostone_bricks": {"variants": {"default": ""}},
    "guanostone_chiseled": {"variants": {"default": ""}},
    "guanostone_tiles": {"variants": {"default": ""}},
    "guanostone_redstone_ore": {"variants": {"default": ""}, "recipe": None},
    "guano_block": {"variants": {"default": ""}},
    "guano_layer": {"base": {"*": "tripwire", "8": "note_block"}, "variants": {str(n): "layers=%d" % n for n in range(1, 9)}, "bedrock_texture": "block/guano"},
    # Bola antioscurolillas: hasta 5 bolas por bloque (balls=N); las polillas oscuras huyen de ella (ForlornMobs).
    "moth_ball": {"variants": {str(n): "balls=%d" % n for n in range(1, 6)}, "bedrock_texture": "item/moth_ball"},
    "coprolith": {"variants": {"default": ""}, "recipe": None},
    "smooth_coprolith": {"variants": {"default": ""}},
    "coprolith_coal_ore": {"variants": {"default": ""}, "recipe": None},
    "porous_coprolith": {"variants": {"default": ""}, "recipe": None, "bedrock_texture": "block/porous_coprolith_0"},
    "peering_coprolith": {"variants": {"default": ""}, "recipe": None, "bedrock_texture": "block/porous_coprolith_1"},
    "forsaken_idol": {"variants": {f: "facing=" + f for f in ("north", "south", "west", "east")}, "recipe": None,
                      "bedrock_texture": "block/forsaken_idol_0"},
    "thornwood_log": {"variants": dict(AXIS), "recipe": None, "bedrock_texture": "block/thornwood/thornwood_log"},
    "thornwood_wood": {"variants": dict(AXIS), "bedrock_texture": "block/thornwood/thornwood_log"},
    "stripped_thornwood_log": {"variants": dict(AXIS), "recipe": None,
                               "bedrock_texture": "block/thornwood/stripped_thornwood_log"},
    "stripped_thornwood_wood": {"variants": dict(AXIS), "bedrock_texture": "block/thornwood/stripped_thornwood_log"},
    "thornwood_planks": {"variants": {"default": ""}, "bedrock_texture": "block/thornwood/thornwood_planks"},
    "thornwood_branch": {"base": "tripwire", "variants": dict(FACING), "recipe": None, "bedrock_texture": "block/thornwood/thornwood_branch"},
    # Plantas sin colisión: un estado de tripwire cada una (quedaban 10).
    # Sin el desplazamiento al azar del mod (offsetType); el helecho rizado solo mira al norte. ``item: False``: el
    # ítem ya existe en ITEMS (comida o ítem plano que no se coloca) y no se reemplaza.
    "curly_fern": {"base": "tripwire", "variants": {"default": "half=lower,facing=north", "upper": "half=upper,facing=north"},
                   "recipe": None, "bedrock_texture": "block/curly_fern_bottom"},
    "flytrap": {"base": "tripwire", "variants": {"default": "open=false"}, "recipe": None, "item": False},
    "fiddlehead": {"base": "tripwire", "variants": {"default": ""}, "recipe": None, "item": False},
    "underweed": {"base": "tripwire", "variants": {"default": ""}, "recipe": None},
    "sweet_puff": {"base": "tripwire", "variants": {"default": ""}, "recipe": None, "item": False},
    "sundrop": {"base": "tripwire", "variants": {"default": "facing=up"}, "recipe": None},
    "lollipop_bunch": {"base": "tripwire", "variants": {"default": ""}, "recipe": None},
    "licoroot_sprout": {"base": "tripwire", "variants": {"default": ""}, "recipe": None},
}

# Bloques que el mod deja anegar (WATERLOGGED, o LIQUID_LOGGED con agua), por prioridad: si no alcanzan los estados
# de cliente mojados, quedan secos los últimos. Clases del mod: Mussel, TubeWorm, BoneWorms, OceanFlora (anémonas),
# PingPongSponge, BaleenBone, BoneRibs, BoneNodule, HollowBone, ThinBone, AbyssalAltar, CopperValve, NeodymiumNode,
# TeslaBulb, HeartOfIron, MetalScaffolding, ThinGeothermalVent, SulfurBud, UraniumRod, ThornwoodBranch, ForsakenIdol,
# MothBall, Peppermint, GummyRing, GiantSweetberry, CandyCanePole (CrossCollisionBlock).
WATERLOGGABLE = [
    "mussel", "tube_worm", "bone_worms", "dusk_anemone", "twilight_anemone", "midnight_anemone", "ping_pong_sponge",
    "baleen_bone", "bone_ribs", "bone_nodule", "hollow_bone", "thin_bone", "abyssal_altar", "copper_valve",
    "scarlet_neodymium_node", "azure_neodymium_node", "tesla_bulb", "heart_of_iron", "metal_scaffolding",
    "rusty_scaffolding", "geothermal_vent_thin", "geothermal_vent_medium", "sulfur_bud_small", "sulfur_bud_medium",
    "sulfur_bud_large", "sulfur_cluster", "uranium_rod", "thornwood_branch", "forsaken_idol", "moth_ball",
    "small_peppermint", "large_peppermint", "giant_sweetberry", "candy_cane_pole", "stripped_candy_cane_pole",
    "gummy_ring_red", "gummy_ring_green", "gummy_ring_blue", "gummy_ring_yellow", "gummy_ring_pink",
]

# Bloques que solo toman un estado de cliente seco si sobra (no alcanzan para todos): el
# coprolito poroso es terreno con agujeros chicos, casi un cubo.
CLIENT_REMAP_LAST = ["porous_coprolith"]

ITEMS = {
    # Magnetic Caves
    "raw_scarlet_neodymium": {"recipe": None},
    "raw_azure_neodymium": {"recipe": None},
    "scarlet_neodymium_ingot": {"material": "IRON_NUGGET"},
    "azure_neodymium_ingot": {"material": "IRON_NUGGET"},
    "telecore": {"recipe": None},
    "notor_gizmo": {"recipe": None},
    "heavyweight": {"recipe": None},
    "ferrouslime_ball": {"material": "SLIME_BALL", "recipe": None},
    "seeking_arrow": {"material": "ARROW"},
    "galena_gauntlet": {"material": "SHIELD", "stack": 1, "max_damage": 400,
                        "citadel": ("GalenaGauntletModel", "entity/galena_gauntlet")},
    "resistor_shield": {"material": "SHIELD", "stack": 1, "max_damage": 1000,
                        "citadel": ("ResistorShieldModel", "entity/resistor_shield")},
    # Primordial Caves (parte 3: drops de los mobs). Lo crudo va sobre remolacha, que no tiene receta
    # vanilla de cocción: así solo lo cocinan las recetas propias (ModCookingRecipes) y el resultado se apila. La chuleta del mod es un bloque que se come
    # en 4 mordiscos; acá es un ítem que vale 2 mordiscos.
    "dinosaur_chop": {"material": "BEETROOT", "food": (6, 0.2), "recipe": None, "block_item": True,
                      "bedrock_texture": "block/dinosaur_chop_raw"},
    "cooked_dinosaur_chop": {"material": "COOKED_PORKCHOP", "food": (14, 0.35), "recipe": None, "block_item": True,
                             "bedrock_texture": "block/dinosaur_chop_cooked"},
    "trilocaris_tail": {"material": "BEETROOT", "food": (2, 0.3), "recipe": None},
    "cooked_trilocaris_tail": {"material": "COOKED_COD", "food": (5, 0.5), "recipe": None},
    "tough_hide": {"material": "RABBIT_HIDE", "recipe": None},
    "heavy_bone": {"material": "BONE", "stack": 16, "recipe": None},
    # Primordial Caves (parte 4: doma). Los efectos al comer (furia, prisa) quedan pendientes.
    # Bases comestibles: desde 1.21.2 la comida necesita también el componente consumable de la base.
    "dinosaur_nugget": {"material": "COOKED_CHICKEN", "food": (3, 0.3), "item_texture": "item/dinosaur_nugget_0"},
    "serene_salad": {"material": "MUSHROOM_STEW", "stack": 1, "food": (5, 0.35)},
    "seething_stew": {"material": "MUSHROOM_STEW", "stack": 1, "food": (6, 0.6)},
    "primordial_soup": {"material": "MUSHROOM_STEW", "stack": 1, "food": (6, 0.6)},
    "primitive_club": {"material": "WOODEN_SWORD", "stack": 1, "max_damage": 120, "combat": (9.0, 0.25),
                       "item_texture": "item/primitive_club"},
    # Plantas: en el mod son bloques; acá son ítems que se consiguen rompiendo plantas vanilla
    # dentro del bioma (ver PrimordialDrops).
    "pine_nuts": {"material": "DRIED_KELP", "food": (2, 0.175), "recipe": None},
    "tree_star": {"recipe": None},
    "fiddlehead": {"recipe": None, "item_texture": "item/fiddlehead"},
    "flytrap": {"material": "PAPER", "recipe": None, "block_item": True, "bedrock_texture": "block/flytrap_mouth_open"},
    # Lanza de caliza: el mod la carga como un arco; acá se lanza como bola de nieve (ver PrimordialSpear).
    # Hito 7 (Luxtructosaurus y Monolito de ámbar)
    "tectonic_shard": {"recipe": None},
    "ominous_catalyst": {},
    "amber_curiosity": {"recipe": None},
    "limestone_spear": {"material": "SNOWBALL", "stack": 16, "item_texture": "item/limestone_spear"},
    # Toxic Caves (parte 1)
    "uranium": {"recipe": None},
    "uranium_shard": {"recipe": None},
    "sulfur_dust": {"recipe": None},
    "cinder_brick": {"recipe": None},
    # Traje Hazmat (ACArmorMaterial "hazmat_suit": durabilidad x20, defensa 2/5/4/2, dureza 0.5).
    "hazmat_mask": {"material": "LEATHER_HELMET", "stack": 1, "max_damage": 220, "armor": ("HEAD", 2.0, 0.5, "hazmat_suit")},
    "hazmat_chestplate": {"material": "LEATHER_CHESTPLATE", "stack": 1, "max_damage": 320,
                          "armor": ("CHEST", 5.0, 0.5, "hazmat_suit")},
    "hazmat_leggings": {"material": "LEATHER_LEGGINGS", "stack": 1, "max_damage": 300,
                        "armor": ("LEGS", 4.0, 0.5, "hazmat_suit")},
    "hazmat_boots": {"material": "LEATHER_BOOTS", "stack": 1, "max_damage": 260, "armor": ("FEET", 2.0, 0.5, "hazmat_suit")},
    "polymer_plate": {},
    # La pasta la suelta el Gammaroach y el radon se embotella en respiraderos geotermicos (partes siguientes).
    "toxic_paste": {"recipe": None},
    "fissile_core": {"recipe": None},
    "charred_remnant": {"recipe": None},
    "radon_bottle": {"material": "POTION", "stack": 16, "recipe": None},
    # Raygun: se dispara manteniendo el clic derecho (ver ToxicRaygun); la barra de durabilidad es la carga usada.
    "raygun": {"stack": 1, "max_damage": 1000, "citadel": ("RaygunModel", "entity/raygun/raygun")},
    # Huevos de dinosaurio: en el mod son bloques; acá se apoyan como ItemDisplay (ver PrimordialBreeding).
    "subterranodon_egg": {"material": "PAPER", "stack": 16, "recipe": None},
    "vallumraptor_egg": {"material": "PAPER", "stack": 16, "recipe": None},
    "grottoceratops_egg": {"material": "PAPER", "stack": 16, "recipe": None},
    "tremorsaurus_egg": {"material": "PAPER", "stack": 16, "recipe": None},
    "relicheirus_egg": {"material": "PAPER", "stack": 16, "recipe": None},
    # Hito 7: huevo del Atlatitan (bloque completo de 16 px en el mod).
    "atlatitan_egg": {"material": "PAPER", "stack": 16, "recipe": None},
    # Toxic Caves (Hito 7): huevo del Tremorzilla. La receta del mod usa tectonic_shard, que llega con el
    # Luxtructosaurus: se activa cuando ese ítem exista.
    "tremorzilla_egg": {"material": "PAPER", "stack": 64},
    # Candy Cavity (parte 3: drops de los mobs), con la comida de ACFoods. La base es una golosina vanilla comestible.
    "candy_cane": {"material": "COOKIE", "food": (2, 0.1), "recipe": None},
    "sharpened_candy_cane": {"material": "COOKIE", "food": (2, 0.1), "recipe": None},
    "gumball_pile": {"material": "COOKIE", "food": (3, 0.2), "recipe": None},
    "caramel": {"material": "COOKIE", "food": (2, 0.3), "recipe": None},
    "caramel_apple": {"material": "APPLE", "food": (6, 0.2), "recipe": None},
    "sweetish_fish_red": {"material": "COOKIE", "food": (2, 0.2), "recipe": None},
    "gelatin_red": {"material": "COOKIE", "food": (3, 0.25), "recipe": None},
    "sweet_puff": {"material": "COOKIE", "food": (1, 0.1), "recipe": None, "item_texture": "block/sweet_puff"},
    "sweet_tooth": {"recipe": None},
    # Candy Cavity (parte 4): drops del hombre de jengibre y la licobruja. Las bolas de helado se tiran en el mod y el
    # bastón de azúcar es un arma con hechizos: por ahora son ítems sin habilidad.
    "gingerbread_crumbs": {"material": "COOKIE", "food": (1, 0.1), "recipe": None},
    "radiant_essence": {"recipe": None},
    "sugar_staff": {"stack": 1, "recipe": None, "item_texture": "item/sugar_staff"},
    "vanilla_ice_cream_scoop": {"material": "SNOWBALL", "stack": 16, "recipe": None},
    "chocolate_ice_cream_scoop": {"material": "SNOWBALL", "stack": 16, "recipe": None},
    "sweetberry_ice_cream_scoop": {"material": "SNOWBALL", "stack": 16, "recipe": None},
    # Candy Cavity (parte 5): ítems con habilidad (ver CandyItems). La barra de durabilidad del Shot Gum son los chicles.
    "shot_gum": {"stack": 1, "max_damage": 4, "citadel": ("ShotGumModel", "entity/shot_gum")},
    "candy_cane_hook": {"stack": 1, "max_damage": 200, "item_texture": "item/candy_cane_hook"},
    "sack_of_sating": {"stack": 1, "item_texture": "item/sack_of_sating"},
    # Armadura primordial (ACArmorMaterial "primordial": durabilidad x20, defensa 2/3/4, encantabilidad 25).
    "primordial_helmet": {"material": "LEATHER_HELMET", "stack": 1, "max_damage": 220,
                          "armor": ("HEAD", 2.0, 0.0, "primordial")},
    "primordial_tunic": {"material": "LEATHER_CHESTPLATE", "stack": 1, "max_damage": 320,
                         "armor": ("CHEST", 3.0, 0.0, "primordial")},
    "primordial_pants": {"material": "LEATHER_LEGGINGS", "stack": 1, "max_damage": 300,
                         "armor": ("LEGS", 4.0, 0.0, "primordial")},
    "cycad": {"material": "PAPER", "recipe": None, "block_item": True, "bedrock_texture": "block/cycad"},
    # Abyssal Chasm (parte 3: drops de las criaturas marinas). Lo crudo va sobre remolacha (ver dinosaur_chop); el
    # cerdo de mar da hambre al comerlo (AbyssalItems). La nieve marina es un bloque en el mod; acá es un ítem.
    "lanternfish": {"material": "BEETROOT", "food": (1, 0.175), "recipe": None},
    "cooked_lanternfish": {"material": "COOKED_COD", "food": (2, 0.3), "recipe": None},
    "tripodfish": {"material": "BEETROOT", "food": (2, 0.2), "recipe": None},
    "cooked_tripodfish": {"material": "COOKED_COD", "food": (5, 0.34), "recipe": None},
    "sea_pig": {"material": "COOKIE", "food": (1, 0.2), "recipe": None},
    "bioluminesscence": {"recipe": None},
    "pearl": {"recipe": "pearl_from_gazing_pearl"},
    "marine_snow": {"material": "PAPER", "recipe": None},
    # Carga de profundidad: se lanza como bola de nieve y explota 30 ticks después de tocar algo (AbyssalItems).
    "depth_charge": {"material": "SNOWBALL", "recipe": None},
    # Abyssal Chasm (parte 4: profundos). Las bombas de tinta se lanzan como bola de nieve (AbyssalItems); la perla
    # observante guarda la reputación del que la lleva. La ortolanza, el bastón marino y la caracola mágica salen del
    # trueque pero todavía no tienen habilidad.
    "ink_bomb": {"material": "SNOWBALL", "recipe": None},
    "glow_ink_bomb": {"material": "SNOWBALL", "recipe": None},
    "gazing_pearl": {"stack": 1, "recipe": None},
    "sea_glass_shards": {"recipe": None},
    "ortholance": {"material": "IRON_SWORD", "stack": 1, "max_damage": 340, "combat": (6.0, 1.6), "recipe": None},
    "sea_staff": {"stack": 1, "max_damage": 850, "recipe": None},
    "magic_conch": {"stack": 1, "max_damage": 5, "recipe": None},
    # Abyssal Chasm (parte 5: Hullbreaker y traje de buzo). El botín del Hullbreaker (hullbreaker.json del mod) trae el
    # motor enigmático (una máquina en el mod: acá es un ítem) y el embrión inmortal. El traje de buzo (ACArmorMaterial
    # "diving_suit": durabilidad x20, defensa 2/6/5/2, encantabilidad 25, reparación con lingotes de cobre) no tiene
    # receta: sale de las ruinas abisales y de los ahogados del abismo. Las calzas suman 0.5 de velocidad de nado
    # (ForgeMod.SWIM_SPEED), que acá es la eficiencia de movimiento en el agua (ver DivingSuit).
    "enigmatic_engine": {"material": "PAPER", "recipe": None, "block_item": True,
                         "bedrock_texture": "block/enigmatic_engine"},
    "immortal_embryo": {"recipe": None},
    # Forlorn Hollows (parte 1): el guano que sueltan las capas y el bloque y que trae el botín de las chozas. En el mod
    # se lanza (GuanoEntity): va sobre bola de nieve (ForlornItems).
    "guano": {"material": "SNOWBALL", "recipe": None},
    "diving_helmet": {"material": "LEATHER_HELMET", "stack": 1, "max_damage": 220, "recipe": None,
                      "armor": ("HEAD", 2.0, 0.0, "diving_suit"), "enchantability": 25, "repair": "COPPER_INGOT"},
    "diving_chestplate": {"material": "LEATHER_CHESTPLATE", "stack": 1, "max_damage": 320, "recipe": None,
                          "armor": ("CHEST", 6.0, 0.0, "diving_suit"), "enchantability": 25, "repair": "COPPER_INGOT"},
    "diving_leggings": {"material": "LEATHER_LEGGINGS", "stack": 1, "max_damage": 300, "recipe": None,
                        "armor": ("LEGS", 5.0, 0.0, "diving_suit"), "enchantability": 25, "repair": "COPPER_INGOT",
                        "water_efficiency": 0.5},
    "diving_boots": {"material": "LEATHER_BOOTS", "stack": 1, "max_damage": 260, "recipe": None,
                     "armor": ("FEET", 2.0, 0.0, "diving_suit"), "enchantability": 25, "repair": "COPPER_INGOT"},
    # Forlorn Hollows (parte 2: mobs). Lo que sueltan polillas oscuras, roedores corrosivos, vésperes y fieles bajo
    # tierra. El polvo de oscurolilla enfurece a los mobs del bioma (ForlornItems); el ala de visperador da hambre al
    # comerla. La daga desolada (diamante, 360 usos, ataque 4, velocidad 2) y la gema oculta son ítems simples: sus
    # habilidades (dagas orbitales, observador) llegan con la parte de ítems.
    "moth_dust": {"recipe": None},
    "dark_tatters": {"recipe": None},
    "corrodent_teeth": {"recipe": None},
    "vesper_wing": {"material": "BEETROOT", "food": (3, 0.2), "recipe": None},
    "occult_gem": {"recipe": None},
    "desolate_dagger": {"material": "DIAMOND_SWORD", "stack": 1, "max_damage": 360, "combat": (4.0, 2.0),
                        "recipe": None},
    # Forlorn Hollows (parte 3: ítems). La oscuridad pura es botín (Forsaken y cofres), la seda de sombra y el
    # fertilizante tienen receta del mod. La manzana y el guiso dan comida que se come siempre (ACFoods); el efecto del
    # guiso (visión nocturna 2400) y el de la manzana (alarga Oscuridad encarnada) están en ForlornItems.
    "fertilizer": {},
    "pure_darkness": {"recipe": None},
    "shadow_silk": {},
    "darkened_apple": {"material": "APPLE", "food": (4, 0.35), "always_eat": True},
    "vesper_stew": {"material": "MUSHROOM_STEW", "stack": 1, "food": (5, 0.3), "always_eat": True},
    # Flecha excavadora: va sobre flecha como la flecha buscadora (ForlornItems).
    "burrowing_arrow": {"material": "ARROW"},
    # Arco del pavor (500 usos): arco vanilla con las texturas de carga del mod (sin su modelo 3D de Citadel).
    "dreadbow": {"material": "BOW", "stack": 1, "max_damage": 500, "bow": True},
    # Tótem de posesión (1000 usos, ataque 3, velocidad 1.6): ítem de espada; la textura cambia con el estado
    # (sin vincular, vinculado, controlando) mediante banderas de custom_model_data (Totem).
    "totem_of_possession": {"material": "IRON_SWORD", "stack": 1, "max_damage": 1000, "combat": (3.0, 1.6),
                            "totem": True, "item_texture": "item/totem_of_possession_unbound"},
    # Armadura de oscuridad (ACArmorMaterial "darkness": durabilidad x15, defensa 1/1, dureza 0.5, encantabilidad 40).
    # Solo capucha y capa; la capa carga la habilidad (DarknessArmor).
    "hood_of_darkness": {"material": "LEATHER_HELMET", "stack": 1, "max_damage": 165,
                         "armor": ("HEAD", 1.0, 0.5, "darkness"), "enchantability": 40},
    "cloak_of_darkness": {"material": "LEATHER_CHESTPLATE", "stack": 1, "max_damage": 240,
                          "armor": ("CHEST", 1.0, 0.5, "darkness"), "enchantability": 40},
}

# Pinturas rupestres: en el mod son caliza lisa con un dibujo en una cara. Acá el bloque es la caliza lisa del módulo
# y el dibujo un ItemDisplay (ver CavePaintings y display_models.py). El ítem es papel con el modelo del mod.
CAVE_PAINTINGS = ["ambersol", "dark", "dino_nuggets", "enderman", "footprint", "footprints", "friendship",
                  "grottoceratops", "grottoceratops_friend", "hunt"] + ["mystery_%d" % n for n in range(1, 10)] + [
                  "pewen", "portal", "relicheirus", "relicheirus_slash", "subterranodon", "subterranodon_ride",
                  "tree_stars", "tremorsaurus", "tremorsaurus_friend", "trilocaris", "vallumraptor_chest",
                  "vallumraptor_friend"]
for _painting in CAVE_PAINTINGS:
    ITEMS["cave_painting_" + _painting] = {"material": "PAPER", "recipe": None, "block_item": True,
                                           "bedrock_texture": "block/cave_painting/" + _painting}

SUBSTITUTES = {
    # Magnetic Caves
    "galena_stairs": {"material": "POLISHED_DEEPSLATE_STAIRS", "bedrock_texture": "block/galena"},
    "galena_slab": {"material": "POLISHED_DEEPSLATE_SLAB", "bedrock_texture": "block/galena"},
    "galena_wall": {"material": "POLISHED_DEEPSLATE_WALL", "bedrock_texture": "block/galena"},
    "galena_brick_stairs": {"material": "DEEPSLATE_BRICK_STAIRS", "bedrock_texture": "block/galena_bricks"},
    "galena_brick_slab": {"material": "DEEPSLATE_BRICK_SLAB", "bedrock_texture": "block/galena_bricks"},
    "galena_brick_wall": {"material": "DEEPSLATE_BRICK_WALL", "bedrock_texture": "block/galena_bricks"},
    # Primordial Caves
    "limestone_stairs": {"material": "SANDSTONE_STAIRS", "bedrock_texture": "block/limestone"},
    "limestone_slab": {"material": "SANDSTONE_SLAB", "bedrock_texture": "block/limestone"},
    "limestone_wall": {"material": "SANDSTONE_WALL", "bedrock_texture": "block/limestone"},
    "smooth_limestone_stairs": {"material": "SMOOTH_SANDSTONE_STAIRS", "bedrock_texture": "block/smooth_limestone"},
    "smooth_limestone_slab": {"material": "PETRIFIED_OAK_SLAB", "bedrock_texture": "block/smooth_limestone"},
    "smooth_limestone_wall": {"material": "SANDSTONE_WALL", "bedrock_texture": "block/smooth_limestone"},
    "pewen_stairs": {"material": "OAK_STAIRS", "bedrock_texture": "block/pewen/pewen_planks"},
    "pewen_slab": {"material": "OAK_SLAB", "bedrock_texture": "block/pewen/pewen_planks"},
    "pewen_fence": {"material": "OAK_FENCE", "bedrock_texture": "block/pewen/pewen_planks"},
    "pewen_fence_gate": {"material": "OAK_FENCE_GATE", "bedrock_texture": "block/pewen/pewen_planks"},
    "pewen_door": {"material": "OAK_DOOR", "bedrock_texture": "block/pewen/pewen_planks"},
    "pewen_trapdoor": {"material": "OAK_TRAPDOOR", "bedrock_texture": "block/pewen/pewen_planks"},
    "pewen_button": {"material": "OAK_BUTTON", "bedrock_texture": "block/pewen/pewen_planks"},
    "pewen_pressure_plate": {"material": "OAK_PRESSURE_PLATE", "bedrock_texture": "block/pewen/pewen_planks"},
    # Carteles: el de pared y el de pared colgante son el mismo ítem; en el módulo son carteles vanilla de roble.
    "pewen_sign": {"material": "OAK_SIGN", "item_texture": "item/pewen_sign", "stack": 16},
    "pewen_hanging_sign": {"material": "OAK_HANGING_SIGN", "item_texture": "item/pewen_hanging_sign", "stack": 16},
    # Toxic Caves
    "radrock_stairs": {"material": "MOSSY_COBBLESTONE_STAIRS", "bedrock_texture": "block/radrock"},
    "radrock_slab": {"material": "MOSSY_COBBLESTONE_SLAB", "bedrock_texture": "block/radrock"},
    "radrock_wall": {"material": "MOSSY_COBBLESTONE_WALL", "bedrock_texture": "block/radrock"},
    "radrock_brick_stairs": {"material": "MOSSY_STONE_BRICK_STAIRS", "bedrock_texture": "block/radrock_bricks"},
    "radrock_brick_slab": {"material": "MOSSY_STONE_BRICK_SLAB", "bedrock_texture": "block/radrock_bricks"},
    "radrock_brick_wall": {"material": "MOSSY_STONE_BRICK_WALL", "bedrock_texture": "block/radrock_bricks"},
    "cinder_block_stairs": {"material": "POLISHED_ANDESITE_STAIRS", "bedrock_texture": "block/cinder_block"},
    "cinder_block_slab": {"material": "POLISHED_ANDESITE_SLAB", "bedrock_texture": "block/cinder_block"},
    "cinder_block_wall": {"material": "ANDESITE_WALL", "bedrock_texture": "block/cinder_block"},
    # Candy Cavity
    "wafer_cookie_stairs": {"material": "BIRCH_STAIRS", "bedrock_texture": "block/wafer_cookie_block", "recipe": None},
    "wafer_cookie_wall": {"material": "SANDSTONE_WALL", "bedrock_texture": "block/wafer_cookie_block", "recipe": None},
    "wafer_cookie_slab": {"material": "BIRCH_SLAB", "bedrock_texture": "block/wafer_cookie_block", "recipe": None},
    "gingerbread_stairs": {"material": "ACACIA_STAIRS", "bedrock_texture": "block/gingerbread_block", "recipe": None},
    "gingerbread_slab": {"material": "ACACIA_SLAB", "bedrock_texture": "block/gingerbread_block", "recipe": None},
    "gingerbread_wall": {"material": "RED_SANDSTONE_WALL", "bedrock_texture": "block/gingerbread_block", "recipe": None},
    "gingerbread_brick_stairs": {"material": "GRANITE_STAIRS", "bedrock_texture": "block/gingerbread_bricks",
                                 "recipe": None},
    "gingerbread_brick_slab": {"material": "GRANITE_SLAB", "bedrock_texture": "block/gingerbread_bricks", "recipe": None},
    "gingerbread_brick_wall": {"material": "GRANITE_WALL", "bedrock_texture": "block/gingerbread_bricks", "recipe": None},
    "frosted_gingerbread_stairs": {"material": "JUNGLE_STAIRS", "bedrock_texture": "block/frosted_gingerbread_block",
                                   "recipe": None},
    "frosted_gingerbread_slab": {"material": "JUNGLE_SLAB", "bedrock_texture": "block/frosted_gingerbread_block",
                                 "recipe": None},
    "frosted_gingerbread_wall": {"material": "MUD_BRICK_WALL", "bedrock_texture": "block/frosted_gingerbread_block",
                                 "recipe": None},
    "frosted_gingerbread_brick_stairs": {"material": "BRICK_STAIRS", "bedrock_texture": "block/frosted_gingerbread_bricks",
                                         "recipe": None},
    "frosted_gingerbread_brick_slab": {"material": "BRICK_SLAB", "bedrock_texture": "block/frosted_gingerbread_bricks",
                                       "recipe": None},
    "frosted_gingerbread_brick_wall": {"material": "BRICK_WALL", "bedrock_texture": "block/frosted_gingerbread_bricks",
                                       "recipe": None},
    "gingerbread_door": {"material": "ACACIA_DOOR", "bedrock_texture": "block/gingerbread_door", "recipe": None},
    "frosted_gingerbread_door": {"material": "JUNGLE_DOOR", "bedrock_texture": "block/frosted_gingerbread_door",
                                 "recipe": None},
    "gingerbarrel": {"material": "BARREL", "bedrock_texture": "block/gingerbarrel", "recipe": None},
    "sugar_glass": {"material": "WHITE_STAINED_GLASS", "bedrock_texture": "block/sugar_glass", "recipe": None},
    # Abyssal Chasm
    "abyssmarine_stairs": {"material": "COBBLED_DEEPSLATE_STAIRS", "bedrock_texture": "block/abyssmarine", "recipe": None},
    "abyssmarine_slab": {"material": "COBBLED_DEEPSLATE_SLAB", "bedrock_texture": "block/abyssmarine", "recipe": None},
    "abyssmarine_wall": {"material": "COBBLED_DEEPSLATE_WALL", "bedrock_texture": "block/abyssmarine", "recipe": None},
    "abyssmarine_brick_stairs": {"material": "DEEPSLATE_TILE_STAIRS", "bedrock_texture": "block/abyssmarine_bricks",
                                 "recipe": None},
    "abyssmarine_brick_slab": {"material": "DEEPSLATE_TILE_SLAB", "bedrock_texture": "block/abyssmarine_bricks",
                               "recipe": None},
    "abyssmarine_brick_wall": {"material": "DEEPSLATE_TILE_WALL", "bedrock_texture": "block/abyssmarine_bricks",
                               "recipe": None},
    "smooth_bone_stairs": {"material": "SMOOTH_QUARTZ_STAIRS", "bedrock_texture": "block/smooth_bone", "recipe": None},
    "smooth_bone_slab": {"material": "SMOOTH_QUARTZ_SLAB", "bedrock_texture": "block/smooth_bone", "recipe": None},
    "smooth_bone_wall": {"material": "DIORITE_WALL", "bedrock_texture": "block/smooth_bone", "recipe": None},
    "depth_glass": {"material": "TINTED_GLASS", "bedrock_texture": "block/depth_glass", "recipe": None},
    "ancient_leaves": {"material": "JUNGLE_LEAVES", "bedrock_texture": "block/ancient_leaves", "recipe": None},
    # Forlorn Hollows
    "guanostone_stairs": {"material": "SMOOTH_SANDSTONE_STAIRS", "bedrock_texture": "block/guanostone"},
    "guanostone_slab": {"material": "SMOOTH_SANDSTONE_SLAB", "bedrock_texture": "block/guanostone"},
    "guanostone_wall": {"material": "SANDSTONE_WALL", "bedrock_texture": "block/guanostone"},
    "guanostone_brick_stairs": {"material": "MUD_BRICK_STAIRS", "bedrock_texture": "block/guanostone_bricks"},
    "guanostone_brick_slab": {"material": "MUD_BRICK_SLAB", "bedrock_texture": "block/guanostone_bricks"},
    "guanostone_brick_wall": {"material": "MUD_BRICK_WALL", "bedrock_texture": "block/guanostone_bricks"},
    "coprolith_stairs": {"material": "GRANITE_STAIRS", "bedrock_texture": "block/coprolith"},
    "coprolith_slab": {"material": "GRANITE_SLAB", "bedrock_texture": "block/coprolith"},
    "coprolith_wall": {"material": "GRANITE_WALL", "bedrock_texture": "block/coprolith"},
    "smooth_coprolith_stairs": {"material": "POLISHED_GRANITE_STAIRS", "bedrock_texture": "block/smooth_coprolith"},
    "smooth_coprolith_slab": {"material": "POLISHED_GRANITE_SLAB", "bedrock_texture": "block/smooth_coprolith"},
    "smooth_coprolith_wall": {"material": "GRANITE_WALL", "bedrock_texture": "block/smooth_coprolith"},
    "thornwood_stairs": {"material": "DARK_OAK_STAIRS", "bedrock_texture": "block/thornwood/thornwood_planks"},
    "thornwood_slab": {"material": "DARK_OAK_SLAB", "bedrock_texture": "block/thornwood/thornwood_planks"},
    "thornwood_fence": {"material": "DARK_OAK_FENCE", "bedrock_texture": "block/thornwood/thornwood_planks"},
    "thornwood_fence_gate": {"material": "DARK_OAK_FENCE_GATE", "bedrock_texture": "block/thornwood/thornwood_planks"},
    "thornwood_door": {"material": "DARK_OAK_DOOR", "bedrock_texture": "block/thornwood/thornwood_planks"},
    "thornwood_trapdoor": {"material": "DARK_OAK_TRAPDOOR", "bedrock_texture": "block/thornwood/thornwood_trapdoor"},
    "thornwood_button": {"material": "DARK_OAK_BUTTON", "bedrock_texture": "block/thornwood/thornwood_planks"},
    "thornwood_pressure_plate": {"material": "DARK_OAK_PRESSURE_PLATE",
                                 "bedrock_texture": "block/thornwood/thornwood_planks"},
    "thornwood_sign": {"material": "DARK_OAK_SIGN", "item_texture": "item/thornwood_sign", "stack": 16},
    "thornwood_hanging_sign": {"material": "DARK_OAK_HANGING_SIGN", "item_texture": "item/thornwood_hanging_sign", "stack": 16},
}

# Eventos de sonido del mod que se publican en el pack Java (namespace alexscaves).
SOUNDS = [
    # Magnetic Caves: bioma
    "magnetic_caves_music", "magnetic_caves_ambience", "magnetic_caves_ambience_additions",
    "magnetic_caves_ambience_mood",
    # Magnetic Caves: bloques e ítems
    "neodymium_step", "neodymium_place", "neodymium_break", "azure_neodymium_push_loop",
    "scarlet_neodymium_pull_loop", "galena_gauntlet_use_loop", "galena_gauntlet_stop",
    "resistor_shield_azure_loop", "resistor_shield_scarlet_loop", "resistor_shield_slam", "resistor_shield_spin",
    # Magnetic Caves: mobs
    "teletor_idle", "teletor_hurt", "teletor_death", "magnetron_idle", "magnetron_hurt", "magnetron_death",
    "magnetron_attack", "magnetron_assemble", "boundroid_idle", "boundroid_hurt", "boundroid_death",
    "boundroid_slam", "ferrouslime_hurt", "ferrouslime_death", "ferrouslime_combine", "notor_idle",
    "notor_hurt", "notor_death",
    # Primordial Caves: bioma y mobs
    "primordial_caves_music", "primordial_caves_ambience", "primordial_caves_ambience_additions",
    "primordial_caves_ambience_mood",
    "subterranodon_idle", "subterranodon_hurt", "subterranodon_death", "subterranodon_flap",
    "vallumraptor_idle", "vallumraptor_hurt", "vallumraptor_death", "vallumraptor_call", "vallumraptor_attack",
    "grottoceratops_idle", "grottoceratops_hurt", "grottoceratops_death", "grottoceratops_attack",
    "trilocaris_hurt", "trilocaris_death",
    "tremorsaurus_idle", "tremorsaurus_hurt", "tremorsaurus_death", "tremorsaurus_bite", "tremorsaurus_roar",
    "relicheirus_idle", "relicheirus_hurt", "relicheirus_death", "relicheirus_scratch",
    # Primordial Caves: jefe, Atlatitan, Monolito de ámbar y tephra (Hito 7)
    "luxtructosaurus_idle", "luxtructosaurus_hurt", "luxtructosaurus_death",
    "luxtructosaurus_step", "luxtructosaurus_stomp", "luxtructosaurus_roar", "luxtructosaurus_attack_stomp",
    "luxtructosaurus_kick", "luxtructosaurus_tail", "luxtructosaurus_summon",
    "luxtructosaurus_jump", "luxtructosaurus_boss_music",
    "atlatitan_idle", "atlatitan_hurt", "atlatitan_death", "atlatitan_step", "atlatitan_stomp", "atlatitan_kick",
    "atlatitan_tail", "amber_monolith_place", "amber_monolith_summon", "tephra_whistle", "tephra_hit",
    "primal_magma_fissure_close",
    # Toxic Caves: bioma y mobs
    "toxic_caves_music", "toxic_caves_ambience", "toxic_caves_ambience_additions", "toxic_caves_ambience_mood",
    "nucleeper_idle", "nucleeper_hurt", "nucleeper_death", "nucleeper_charge",
    "brainiac_idle", "brainiac_hurt", "brainiac_death", "brainiac_attack",
    "gammaroach_idle", "gammaroach_hurt", "gammaroach_death", "gammaroach_attack",
    "raycat_idle", "raycat_hurt", "radgill_hurt",
    # Toxic Caves: ácido, chimeneas, habilidades de mobs, raygun y explosión nuclear
    "acid_burn", "geothermal_vent_bubble", "brainiac_throw", "brainiac_lick", "gammaroach_spray",
    "raycat_absorb", "raycat_eat", "raygun_start", "raygun_loop", "raygun_empty", "raygun_reload",
    "nuclear_bomb_place", "nuclear_bomb_defuse", "nuclear_explosion", "large_nuclear_explosion",
    "nuclear_explosion_rumble", "nuclear_siren",
    # Candy Cavity: bioma y mobs
    "candy_cavity_music", "candy_cavity_ambience", "candy_cavity_ambience_additions", "candy_cavity_ambience_mood",
    "caniac_idle", "caniac_hurt", "caniac_death", "caniac_attack", "caniac_swing",
    "gumbeeper_hurt", "gumbeeper_death", "gumbeeper_explode", "gumball_launch", "gumball_bounce", "gumball_hit",
    "caramel_cube_attack", "caramel_cube_big", "caramel_cube_small",
    "sweetish_fish_flop", "sweetish_fish_hurt",
    "candicorn_idle", "candicorn_hurt", "candicorn_death", "candicorn_step", "candicorn_gallop",
    "candicorn_charge_start", "candicorn_charge_loop",
    "gummy_bear_idle", "gummy_bear_hurt", "gummy_bear_death", "gummy_bear_eat", "gummy_bear_step", "gummy_bear_snore",
    "gingerbread_man_idle", "gingerbread_man_hurt", "gingerbread_man_death", "gingerbread_man_attack",
    "gingerbread_man_talk",
    "licowitch_idle", "licowitch_hurt", "licowitch_death", "licowitch_cast_hex", "licowitch_cast_peppermint",
    "licowitch_cast_summon", "licowitch_celebrate",
    "gum_worm_idle", "gum_worm_hurt", "gum_worm_death", "gum_worm_attack", "gum_worm_dig_loop", "gum_worm_dig_stop",
    "shotgum_shoot", "shotgum_reload", "shotgum_empty", "candy_cane_hook_launch", "candy_cane_hook_reel",
    # Abyssal Chasm: bioma y criaturas marinas
    "abyssal_chasm_music", "abyssal_chasm_ambience", "abyssal_chasm_ambience_additions",
    "lanternfish_hurt", "lanternfish_flop", "sea_pig_idle", "sea_pig_hurt", "sea_pig_death", "sea_pig_eat",
    "gossamer_worm_idle", "gossamer_worm_hurt", "gossamer_worm_death", "tripodfish_hurt", "tripodfish_flop",
    "mine_guardian_idle", "mine_guardian_hurt", "mine_guardian_death", "mine_guardian_explode",
    "mine_guardian_land_explode", "mine_guardian_scan", "mine_guardian_flop",
    # Abyssal Chasm (parte 4): profundos, altar abisal y magia de agua
    "deep_one_idle", "deep_one_hurt", "deep_one_death", "deep_one_admire", "deep_one_hostile", "deep_one_attack",
    "deep_one_knight_idle", "deep_one_knight_hurt", "deep_one_knight_death", "deep_one_knight_admire",
    "deep_one_knight_hostile", "deep_one_knight_attack",
    "deep_one_mage_idle", "deep_one_mage_hurt", "deep_one_mage_death", "deep_one_mage_admire",
    "deep_one_mage_hostile", "deep_one_mage_attack",
    "abyssmarine_glow_on", "abyssmarine_glow_off", "sea_staff_woosh", "sea_staff_hit", "magic_conch_summon",
    # Abyssal Chasm (parte 5): Hullbreaker
    "hullbreaker_idle", "hullbreaker_hurt", "hullbreaker_death", "hullbreaker_land_idle", "hullbreaker_land_hurt",
    "hullbreaker_land_death", "hullbreaker_attack",
    # Forlorn Hollows (parte 2): mobs
    "gloomoth_hurt", "gloomoth_death", "gloomoth_flap",
    "underzealot_idle", "underzealot_hurt", "underzealot_death", "underzealot_attack", "underzealot_chant",
    "underzealot_dig", "underzealot_transformation",
    "watcher_idle", "watcher_hurt", "watcher_death", "watcher_attack", "watcher_scare", "watcher_spawn",
    "corrodent_idle", "corrodent_hurt", "corrodent_death", "corrodent_attack", "corrodent_dig_loop",
    "corrodent_dig_stop",
    "vesper_idle", "vesper_quiet_idle", "vesper_hurt", "vesper_death", "vesper_flap", "vesper_scream",
    "moth_ball_place",
    # Forlorn Hollows (Hito 7): el Forsaken
    "forsaken_idle", "forsaken_hurt", "forsaken_death", "forsaken_screech", "forsaken_aoe", "forsaken_leap",
    "forsaken_grab", "forsaken_bite", "forsaken_step", "forsaken_spawn",
    # Forlorn Hollows (parte 3): ítems
    "dreadbow_release", "dreadbow_rain", "totem_of_possession_use", "desolate_dagger_summon", "desolate_dagger_hit",
    "darkness_incarnate_enter", "darkness_incarnate_exit", "darkness_incarnate_idle", "corrodent_teeth",
    # Toxic Caves (Hito 7): Tremorzilla
    "tremorzilla_idle", "tremorzilla_hurt", "tremorzilla_death", "tremorzilla_stomp", "tremorzilla_beam_start",
    "tremorzilla_beam_end", "tremorzilla_beam_loop", "tremorzilla_charge_normal", "tremorzilla_charge_complete",
    "tremorzilla_roar", "tremorzilla_eat", "tremorzilla_bite", "tremorzilla_scratch_attack",
    "tremorzilla_stomp_attack", "tremorzilla_tail_attack",
]

# Encantamientos del mod ya implementados: id -> (material base del ítem, rareza, nivel máx., costo mín.).
# Incompatibilidades: ENCHANTMENT_EXCLUSIVE.
ENCHANTMENTS = {
    "field_extension": ("shield", "common", 4, 6),
    "crystallization": ("shield", "rare", 1, 15),
    "ferrous_haste": ("shield", "rare", 1, 15),
    "arrow_inducting": ("shield", "rare", 1, 18),
    "heavy_slam": ("shield", "common", 3, 6),
    # Forlorn Hollows: tótem de posesión (ítem de espada de hierro), daga desolada (espada de diamante), arco del pavor.
    "detonating_death": ("iron_sword", "rare", 1, 11),
    "rapid_possession": ("iron_sword", "common", 3, 5),
    "sightless": ("iron_sword", "rare", 1, 13),
    "astral_transferring": ("iron_sword", "rare", 1, 12),
    "impending_stab": ("diamond_sword", "common", 3, 6),
    "sated_blade": ("diamond_sword", "common", 2, 11),
    "double_stab": ("diamond_sword", "rare", 1, 12),
    "precise_volley": ("bow", "rare", 1, 18),
    "dark_nock": ("bow", "rare", 3, 10),
    "relentless_darkness": ("bow", "very_rare", 1, 20),
    "twilight_perfection": ("bow", "rare", 3, 7),
    "shaded_respite": ("bow", "very_rare", 1, 9),
}
ENCHANTMENT_EXCLUSIVE = {
    "detonating_death": ["astral_transferring"], "astral_transferring": ["detonating_death"],
    "impending_stab": ["double_stab"], "double_stab": ["impending_stab"],
    "relentless_darkness": ["dark_nock", "precise_volley", "twilight_perfection"],
    "dark_nock": ["relentless_darkness"], "precise_volley": ["relentless_darkness"],
    "twilight_perfection": ["relentless_darkness"],
}

# Sets de armadura: nombre del asset de equipamiento -> (textura del cuerpo, textura de las piernas) del mod.
EQUIPMENT = {
    "primordial": ("armor/primordial_armor_0", "armor/primordial_armor_1"),
    "hazmat_suit": ("armor/hazmat_suit_0", "armor/hazmat_suit_1"),
    "diving_suit": ("armor/diving_suit_0", "armor/diving_suit_1"),
    # La capucha y la capa del mod usan un modelo propio (128x128) que la capa humanoide no puede dibujar: se genera una
    # túnica oscura con la paleta del mod (grises casi negros).
    "darkness": ("generated:robe", "generated:robe"),
}
