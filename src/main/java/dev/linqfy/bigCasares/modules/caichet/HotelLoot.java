package dev.linqfy.bigCasares.modules.caichet;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.block.data.type.Door;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Lo que se encuentra en los cofres del hotel:
 * <ul>
 *   <li>Arma de Fuego: dispara un rayo rojo como el del warden (atraviesa paredes); 5 usos;</li>
 *   <li>Botella de Agua de Almendras: se bebe y da 1 muslito de hambre;</li>
 *   <li>Jarra de Memorias: se bebe, muestra 8 imagenes rapidas a pantalla completa (flashback) y despues da 3 muslitos.</li>
 * </ul>
 * Los cofres aparecen solos en las partes nuevas del hotel.
 */
public final class HotelLoot implements Listener {

    static final NamespacedKey ITEM_KEY = new NamespacedKey("caichet", "hotel_item");

    // ---- arma
    private static final int GUN_USES = 5;
    private static final double BEAM_RANGE = 20;
    private static final double BEAM_RADIUS = 1.2;
    private static final double BEAM_DAMAGE = 10; // como el del warden
    private static final long GUN_COOLDOWN_MS = 1000;
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(220, 20, 30), 1.6f);
    private static final Particle.DustOptions RED_BIG = new Particle.DustOptions(Color.fromRGB(200, 10, 20), 2.2f);
    private static final Particle.DustTransition RED_DARK = new Particle.DustTransition(
            Color.fromRGB(255, 40, 40), Color.fromRGB(90, 0, 0), 2.2f);

    // ---- comida ("muslitos" = iconos de la barra de hambre; cada uno son 2 puntos)
    private static final int BOTTLE_FOOD = 2;
    private static final int JAR_FOOD = 6;
    private static final long MEMORY_TICKS = 4;
    private static final Key MEMORY_FONT = Key.key("caichet", "memorias");

    // ---- cofres
    /** Chance de que un chunk nuevo del hotel tenga un cofre. */
    private static final int CHEST_PERCENT = 12;

    /** Chance de que un chunk nuevo del hotel tenga una puerta suelta. */
    private static final int DOOR_PERCENT = 4;

    private final Map<UUID, Long> lastShot = new HashMap<>();
    /** Para Bedrock: hasta cuando (ms) no puede volver a beber, como si durara el trago de Java. */
    private final Map<UUID, Long> drinkingUntil = new HashMap<>();
    /** Comida de jarras ya bebidas que todavia no se entrego (llega al terminar el flashback, o al desconectarse). */
    private final Map<UUID, Integer> owedFood = new HashMap<>();
    private Watchers watchers;
    private KeyDoors doors;

    /** Para que el rayo pueda matar a las figuras del hotel. */
    void setWatchers(Watchers watchers) {
        this.watchers = watchers;
    }

    /** Las puertas que se abren con la llave (cuadrado negro + cuenta regresiva). */
    void setDoors(KeyDoors doors) {
        this.doors = doors;
    }

    // ------------------------------------------------------------------ items

    static ItemStack gun() {
        ItemStack item = named(Material.STICK, "arma", "Arma de Fuego", NamedTextColor.RED,
                "Dispara rayos rojos. " + GUN_USES + " usos");
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.setData(DataComponentTypes.MAX_DAMAGE, GUN_USES);
        item.setData(DataComponentTypes.DAMAGE, 0);
        return item;
    }

    static ItemStack bottle() {
        ItemStack item = named(Material.PAPER, "botella", "Botella de Agua de Almendras", NamedTextColor.WHITE,
                "Da un muslito");
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        item.setData(DataComponentTypes.CONSUMABLE, drink(BOTTLE_DRINK_MS / 1000f));
        return item;
    }

    static ItemStack jar() {
        ItemStack item = named(Material.PAPER, "jarra", "Jarra de Memorias", NamedTextColor.LIGHT_PURPLE,
                "Da tres muslitos... despues de recordar");
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        item.setData(DataComponentTypes.CONSUMABLE, drink(JAR_DRINK_MS / 1000f));
        return item;
    }

    /** La llave que sueltan las figuras: abre las puertas sueltas del hotel. */
    static ItemStack key() {
        ItemStack item = named(Material.PAPER, "llave", "Llave", NamedTextColor.GOLD,
                "Usala en una de las puertas del hotel");
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        return item;
    }

    public static final long BOTTLE_DRINK_MS = 1600;
    public static final long JAR_DRINK_MS = 2400;
    /** La bebida Despertar ({@link DreamItems#wakeDrink}). */
    public static final long WAKE_DRINK_MS = 1600;
    /** Ticks que se espera, tras el click de un jugador Bedrock, a ver si empezo a beber por su cuenta. */
    private static final long BEDROCK_DRINK_CHECK_TICKS = 5;

    static Consumable drink(float seconds) {
        return Consumable.consumable()
                .consumeSeconds(seconds)
                .animation(ItemUseAnimation.DRINK)
                .sound(Key.key("minecraft", "entity.generic.drink"))
                .hasConsumeParticles(false)
                .build();
    }

    /** El id de un objeto del modulo: los del hotel y tambien los del Sueño (el Despertar), que llevan otra marca. */
    static String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        String id = data.get(ITEM_KEY, PersistentDataType.STRING);
        return id != null ? id : data.get(DreamItems.ITEM_KEY, PersistentDataType.STRING);
    }

    private static ItemStack named(Material material, String id, String name, NamedTextColor color, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("caichet", id));
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------ arma

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!"arma".equals(idOf(event.getItem())) || event.getHand() == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastShot.get(player.getUniqueId());
        if (last != null && now - last < GUN_COOLDOWN_MS) {
            return;
        }
        lastShot.put(player.getUniqueId(), now);
        shoot(player);
        spendUse(player, event.getHand());
    }

    /** Al apagar el modulo: la comida de las jarras ya bebidas se entrega ahora (las tareas pendientes se cancelan). */
    void settleOwedFood() {
        owedFood.forEach((id, food) -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                feed(player, food);
            }
        });
        owedFood.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastShot.remove(event.getPlayer().getUniqueId());
        CaiChetSounds.forget(event.getPlayer().getUniqueId());
        drinkingUntil.remove(event.getPlayer().getUniqueId());
        // la jarra ya se gasto: si se va en medio del flashback, la comida se entrega ahora y no se pierde
        Integer owed = owedFood.remove(event.getPlayer().getUniqueId());
        if (owed != null) {
            feed(event.getPlayer(), owed);
        }
    }

    private void shoot(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        CaiChetSounds.play(world, eye, CaiChetSounds.WEAPON_BEAM, SoundCategory.PLAYERS, 2f, 1f);
        world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.3f);
        // el rayo: polvo rojo a lo largo de la linea y anillos oscuros cada tanto
        for (double d = 1; d <= BEAM_RANGE; d += 0.4) {
            Location p = eye.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.DUST, p, 2, 0.05, 0.05, 0.05, 0, RED, true);
            if (((int) (d * 10)) % 20 == 0) {
                // el degradado de color no esta garantizado en Bedrock: ahi va polvo rojo comun
                for (Player viewer : world.getPlayers()) {
                    if (Bedrock.is(viewer)) {
                        viewer.spawnParticle(Particle.DUST, p, 10, 0.35, 0.35, 0.35, 0, RED_BIG, true);
                    } else {
                        viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 10, 0.35, 0.35, 0.35, 0, RED_DARK, true);
                    }
                }
            }
        }
        // golpea a todo lo que este cerca de la linea (atraviesa paredes, como el del warden)
        DamageSource source = DamageSource.builder(DamageType.SONIC_BOOM)
                .withCausingEntity(player).withDirectEntity(player).build();
        Location end = eye.clone().add(dir.clone().multiply(BEAM_RANGE));
        Location mid = eye.clone().add(dir.clone().multiply(BEAM_RANGE / 2));
        for (Entity entity : world.getNearbyEntities(mid, BEAM_RANGE / 2 + 2, BEAM_RANGE / 2 + 2, BEAM_RANGE / 2 + 2)) {
            if (!(entity instanceof LivingEntity living) || entity == player) {
                continue;
            }
            // un doble son dos entidades en el mismo lugar: le pega al cuerpo (que pasa el golpe a la gallina), no a
            // las dos. Los cuerpos de adorno de Bedrock no reciben nada
            Doppelganger doppel = Doppelganger.of(entity);
            if ((doppel != null && entity == doppel.brain) || Bedrock.isStand(entity)) {
                continue;
            }
            Location center = living.getLocation().add(0, living.getHeight() / 2, 0);
            if (distanceToSegment(center.toVector(), eye.toVector(), end.toVector()) <= BEAM_RADIUS + living.getWidth() / 2) {
                living.damage(BEAM_DAMAGE, source);
                living.setVelocity(living.getVelocity().add(dir.clone().multiply(1.2).setY(0.4)));
            }
        }
        if (watchers != null) {
            watchers.killAlong(player, eye.toVector(), end.toVector(), BEAM_RADIUS + 0.4);
        }
    }

    private static double distanceToSegment(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double t = Math.max(0, Math.min(1, p.clone().subtract(a).dot(ab) / ab.lengthSquared()));
        return a.clone().add(ab.multiply(t)).distance(p);
    }

    private static void spendUse(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack item = player.getInventory().getItem(hand);
        Integer used = item.getData(DataComponentTypes.DAMAGE);
        int now = (used == null ? 0 : used) + 1;
        if (now >= GUN_USES) {
            player.getInventory().setItem(hand, null);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
        } else {
            item.setData(DataComponentTypes.DAMAGE, now);
            player.getInventory().setItem(hand, item);
        }
    }

    // ------------------------------------------------------------------ bebidas

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        String id = idOf(event.getItem());
        Player player = event.getPlayer();
        if ("botella".equals(id)) {
            feed(player, BOTTLE_FOOD);
        } else if ("jarra".equals(id)) {
            flashback(player);
        } else if ("despertar".equals(id)) {
            wakeSoon(player);
        }
    }

    /** Un tick despues, para no viajar en medio del trago (el juego todavia no gasto la bebida). */
    private static void wakeSoon(Player player) {
        CaiChetRuntime.tasks().later(() -> {
            if (player.isOnline() && !player.isDead()) {
                DespertarListener.wake(player);
            }
        }, 1L);
    }

    private static long drinkMillis(String id) {
        return switch (id) {
            case "jarra" -> JAR_DRINK_MS;
            case "despertar" -> WAKE_DRINK_MS;
            default -> BOTTLE_DRINK_MS;
        };
    }

    /**
     * Geyser le dice a Bedrock que la botella y la jarra se consumen (ver {@link CaiChetBedrockItems}), asi que el
     * cliente empieza a beber y el servidor lo cuenta como en Java: llega {@link PlayerItemConsumeEvent} y {@link
     * #onConsume} da la comida. Este camino es solo un respaldo para un cliente que no llega a beber: pasados unos
     * ticks del click, si el jugador no esta bebiendo, se bebe al instante. Nunca cancela el click, para no impedir
     * el trago normal, y si el trago empezo no hace nada, asi no cuenta dos veces.
     */
    @EventHandler
    public void onBedrockDrink(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String id = idOf(event.getItem());
        if ((!"botella".equals(id) && !"jarra".equals(id) && !"despertar".equals(id)) || event.getHand() == null
                || !Bedrock.is(event.getPlayer())
                || "despertar".equals(id) && HotelWorld.isHotel(event.getPlayer().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        CaiChetRuntime.tasks().later(() -> drinkIfNotStarted(player, hand, id), BEDROCK_DRINK_CHECK_TICKS);
    }

    private void drinkIfNotStarted(Player player, EquipmentSlot hand, String id) {
        if (!player.isOnline() || player.isHandRaised() || !id.equals(idOf(player.getInventory().getItem(hand)))) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = drinkingUntil.get(player.getUniqueId());
        if (until != null && now < until) {
            return;
        }
        drinkingUntil.put(player.getUniqueId(), now + drinkMillis(id));
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack held = player.getInventory().getItem(hand);
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held.getAmount() > 0 ? held : null);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
        if ("botella".equals(id)) {
            feed(player, BOTTLE_FOOD);
        } else if ("despertar".equals(id)) {
            wakeSoon(player);
        } else {
            flashback(player);
        }
    }

    private static void feed(Player player, int food) {
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + food));
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + food * 0.6f));
    }

    /** Las fotos seguidas en pantalla (cada una dura 0,2 s) y recien despues la comida. */
    private void flashback(Player player) {
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(MEMORY_TICKS * 50 + 100), Duration.ZERO);
        boolean bedrock = Bedrock.is(player); // Bedrock no tiene la fuente con las imagenes: se muestran frases
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
        for (int i = 0; i < MemoryFrames.COUNT; i++) {
            int frame = i;
            CaiChetRuntime.tasks().later(() -> {
                if (!player.isOnline()) {
                    return;
                }
                Component image = bedrock
                        ? Component.text(MemoryFrames.BEDROCK_TEXT.get(frame), NamedTextColor.LIGHT_PURPLE)
                        // sin sombra: la del titulo dibujaria la foto dos veces, corrida
                        : Component.text(MemoryFrames.glyphs(frame)).font(MEMORY_FONT).shadowColor(ShadowColor.none());
                player.showTitle(Title.title(image, Component.empty(), times));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.5f + frame * 0.1f);
            }, 2 + i * MEMORY_TICKS);
        }
        UUID id = player.getUniqueId();
        owedFood.merge(id, JAR_FOOD, Integer::sum);
        CaiChetRuntime.tasks().later(() -> {
            // si se desconecto, la comida ya se entrego al salir
            Integer owed = owedFood.get(id);
            if (owed == null || !player.isOnline()) {
                return;
            }
            if (owed <= JAR_FOOD) {
                owedFood.remove(id);
            } else {
                owedFood.put(id, owed - JAR_FOOD);
            }
            player.clearTitle();
            feed(player, JAR_FOOD);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
        }, 2 + MemoryFrames.COUNT * MEMORY_TICKS + 2);
    }

    // ------------------------------------------------------------------ cofres

    @EventHandler
    public void onChunk(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !HotelWorld.isHotel(event.getWorld())) {
            return;
        }
        World world = event.getWorld();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        Random random = new Random(HotelLayout.hash(world.getSeed(), cx, cz, 700));
        if (random.nextInt(100) < CHEST_PERCENT) {
            Block spot = freeSpot(world, cx, cz, random);
            if (spot != null) {
                placeChest(spot, random);
            }
        }
        if (random.nextInt(100) < DOOR_PERCENT) {
            Block spot = freeSpot(world, cx, cz, random);
            if (spot != null) {
                placeDoor(spot, random);
            }
        }
    }

    /** Un lugar del chunk con piso firme (no agua) y dos bloques de aire arriba. */
    private static Block freeSpot(World world, int cx, int cz, Random random) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = (cx << 4) + random.nextInt(16);
            int z = (cz << 4) + random.nextInt(16);
            Block floor = world.getBlockAt(x, HotelLayout.FLOOR, z);
            Block spot = floor.getRelative(BlockFace.UP);
            if (floor.getType().isSolid() && spot.getType().isAir() && spot.getRelative(BlockFace.UP).getType().isAir()) {
                return spot;
            }
        }
        return null;
    }

    /** Una puerta parada sola, en el medio de cualquier lado. */
    static void placeDoor(Block spot, Random random) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        BlockFace facing = faces[random.nextInt(faces.length)];
        Block top = spot.getRelative(BlockFace.UP);
        Door lower = (Door) Material.DARK_OAK_DOOR.createBlockData();
        lower.setFacing(facing);
        lower.setHalf(Bisected.Half.BOTTOM);
        Door upper = (Door) Material.DARK_OAK_DOOR.createBlockData();
        upper.setFacing(facing);
        upper.setHalf(Bisected.Half.TOP);
        spot.setBlockData(lower, false);
        top.setBlockData(upper, false);
    }

    // ------------------------------------------------------------------ llave y temporizador

    /** Usar (o golpear) una puerta del hotel con la llave: se gasta la llave y la puerta se abre a un cuadrado negro. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDoor(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !HotelWorld.isHotel(block.getWorld()) || !Tag.DOORS.isTagged(block.getType())
                || !"llave".equals(idOf(event.getItem())) || event.getHand() == null) {
            return;
        }
        event.setCancelled(true);
        if (doors == null || !doors.open(block)) {
            return; // ya estaba abierta: la llave no se gasta
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack inHand = player.getInventory().getItem(event.getHand());
            inHand.setAmount(inHand.getAmount() - 1);
            player.getInventory().setItem(event.getHand(), inHand.getAmount() > 0 ? inHand : null);
        }
    }

    static void placeChest(Block spot, Random random) {
        spot.setType(Material.CHEST, false);
        if (spot.getBlockData() instanceof org.bukkit.block.data.type.Chest data) {
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            data.setFacing(faces[random.nextInt(faces.length)]);
            data.setType(Type.SINGLE);
            spot.setBlockData(data, false);
        }
        if (!(spot.getState() instanceof Chest chest)) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>();
        if (random.nextInt(100) < 30) {
            loot.add(gun());
        }
        if (random.nextInt(100) < 70) {
            ItemStack b = bottle();
            b.setAmount(1 + random.nextInt(3));
            loot.add(b);
        }
        if (random.nextInt(100) < 35) {
            loot.add(jar());
        }
        if (loot.isEmpty()) {
            loot.add(bottle());
        }
        Inventory inventory = chest.getBlockInventory();
        for (ItemStack item : loot) {
            int slot;
            do {
                slot = random.nextInt(inventory.getSize());
            } while (inventory.getItem(slot) != null);
            inventory.setItem(slot, item);
        }
    }
}
