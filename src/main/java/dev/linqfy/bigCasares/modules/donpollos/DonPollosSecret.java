package dev.linqfy.bigCasares.modules.donpollos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * El secreto del Don Pollo Gordito: al hacerle click derecho dice "Yo se cosas..." / "Queres saberlas..." /
 * "Dame Salsa y Picante"; con otro click abre un menu donde le das una salsa y un picante y a cambio, con la
 * estrella "Secreto", te da un bloque con la cara de Don Pollo. Al ponerlo en el piso cuenta la historia ("Era una
 * vez..."), se vuelve el Don Pollo malo, salen particulas rojas y blancas, explota una esfera de particulas, el
 * bloque desaparece y del medio sale el Don Pollo Boss.
 *
 * <p>El bloque de verdad es un beacon; encima va un item display un poquito mas grande con el cubo de la cara
 * (donpollos:bloque_don_pollo_bueno / _malo), asi no hay que retexturizar todos los beacons.
 */
final class DonPollosSecret implements Listener {

    static final List<String> PHRASES = DonPollosReward.SECRET_PHRASES;
    private static final long PHRASE_TICKS = 40;
    /** Despues de las frases, cuanto tiempo tenes para volver a hacerle click y abrir el menu. */
    private static final long READY_TICKS = 20 * 60;
    static final List<String> STORY = DonPollosReward.BLOCK_STORY;
    private static final long STORY_TICKS = 60;
    private static final double STORY_RADIUS = 32;
    private static final String GOOD_MODEL = "bloque_don_pollo_bueno";
    private static final String EVIL_MODEL = "bloque_don_pollo_malo";
    private static final long RITUAL_TICKS = 100;
    private static final long SPHERE_TICKS = 10;
    private static final int SALSA_SLOT = 11;
    private static final int SECRET_SLOT = 13;
    private static final int PICANTE_SLOT = 15;
    private static final int TRADES_SLOT = 22;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final DonPollosItems items;
    private final DonPollosBossManager bosses;
    private final Consumer<Player> openTrades;
    private final NamespacedKey secretKey;
    /** Por jugador: tick en que termina de hablar (o hasta cuando puede abrir el menu, si ya termino). */
    private final Map<UUID, Long> talkingUntil = new HashMap<>();
    private final Map<UUID, Long> readyUntil = new HashMap<>();
    private final List<Ritual> rituals = new ArrayList<>();
    private BukkitTask task;
    private Consumer<Player> closed = player -> { };

    DonPollosSecret(JavaPlugin plugin, DonPollosItems items, DonPollosBossManager bosses, Consumer<Player> openTrades) {
        this.plugin = plugin;
        this.items = items;
        this.bosses = bosses;
        this.openTrades = openTrades;
        this.secretKey = new NamespacedKey(plugin, "secreto_don_pollo");
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRituals, 1L, 1L);
    }

    /** Se llama al cerrar el menu del secreto (para la frase de despedida del Gordito). */
    void onMenuClosed(Consumer<Player> listener) {
        this.closed = listener;
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        clearRituals();
    }

    // ------------------------------------------------------------ el Gordito habla

    /** Click derecho al Gordito: la primera vez dice las frases; despues abre el menu del secreto. */
    void interact(Player player, Villager gordito) {
        long now = Bukkit.getCurrentTick();
        UUID id = player.getUniqueId();
        if (talkingUntil.getOrDefault(id, 0L) > now) {
            return;  // todavia esta hablando
        }
        if (readyUntil.getOrDefault(id, 0L) > now) {
            openMenu(player);
            return;
        }
        talkingUntil.put(id, now + PHRASES.size() * PHRASE_TICKS);
        for (int i = 0; i < PHRASES.size(); i++) {
            String phrase = PHRASES.get(i);
            boolean last = i == PHRASES.size() - 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.showTitle(Title.title(LEGACY.deserialize("§6§o" + phrase), LEGACY.deserialize("§7Don Pollo Gordito"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(300))));
                player.sendMessage(LEGACY.deserialize("§6Don Pollo Gordito: §e§o" + phrase));
                player.playSound(gordito.isValid() ? gordito.getLocation() : player.getLocation(),
                    Sound.ENTITY_VILLAGER_AMBIENT, 1f, 0.7f);
                if (last) {
                    readyUntil.put(player.getUniqueId(), Bukkit.getCurrentTick() + READY_TICKS);
                    player.sendMessage(LEGACY.deserialize("§7(Hacele click derecho de nuevo)"));
                }
            }, i * PHRASE_TICKS);
        }
    }

    // ------------------------------------------------------------ menu del secreto

    private void openMenu(Player player) {
        SecretHolder holder = new SecretHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY.deserialize("§6§lDon Pollo Gordito sabe cosas"));
        holder.inventory = inventory;
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, icon(Material.ORANGE_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(TRADES_SLOT, icon(Material.BREAD, "§eIntercambios", "§7Los intercambios de pan de siempre"));
        refresh(holder);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    private void refresh(SecretHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.setItem(SALSA_SLOT, holder.salsa
            ? items.menuIcon(DonPollosProduct.SALSA, 1, "§f§lSalsa §a✔", List.of("§7Click para sacarla"))
            : icon(Material.WHITE_STAINED_GLASS_PANE, "§fPone una §lSalsa", "§7Click para poner una de tu inventario"));
        inventory.setItem(PICANTE_SLOT, holder.picante
            ? items.menuIcon(DonPollosProduct.PICANTE, 1, "§c§lPicante §a✔", List.of("§7Click para sacarlo"))
            : icon(Material.RED_STAINED_GLASS_PANE, "§cPone un §lPicante", "§7Click para poner uno de tu inventario"));
        boolean ready = holder.salsa && holder.picante;
        ItemStack star = icon(Material.NETHER_STAR, ready ? "§d§lSecreto" : "§8§lSecreto",
            ready ? "§eClick para saber el secreto" : "§7Primero dale Salsa y Picante");
        inventory.setItem(SECRET_SLOT, star);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SecretHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
            || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        switch (event.getSlot()) {
            case SALSA_SLOT -> holder.salsa = toggle(player, DonPollosProduct.SALSA, holder.salsa);
            case PICANTE_SLOT -> holder.picante = toggle(player, DonPollosProduct.PICANTE, holder.picante);
            case SECRET_SLOT -> {
                if (!holder.salsa || !holder.picante) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
                    return;
                }
                holder.salsa = false;
                holder.picante = false;
                holder.done = true;
                player.closeInventory();
                give(player, secretBlock());
                player.sendMessage(LEGACY.deserialize("§6Don Pollo Gordito: §e§oPonelo en el piso... si te animas."));
                player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
                return;
            }
            case TRADES_SLOT -> {
                player.closeInventory();
                openTrades.accept(player);
                return;
            }
            default -> {
                return;
            }
        }
        refresh(holder);
    }

    /** Pone (saca uno del inventario) o saca (lo devuelve) la salsa o el picante. Devuelve si quedo puesto. */
    private boolean toggle(Player player, DonPollosProduct product, boolean placed) {
        if (placed) {
            give(player, items.create(product, 1));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
            return false;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (items.productOf(stack).filter(product::equals).isPresent()) {
                stack.setAmount(stack.getAmount() - 1);
                player.getInventory().setItem(slot, stack.getAmount() <= 0 ? null : stack);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
                return true;
            }
        }
        player.sendMessage(LEGACY.deserialize("§cNo tenes " + (product == DonPollosProduct.SALSA ? "Salsa" : "Picante")
            + ". §7Compralo en la polleria del Don Pollo Aura 67."));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
        return false;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SecretHolder) {
            event.setCancelled(true);
        }
    }

    /** Si cerras sin terminar, te devuelve lo que habias puesto. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SecretHolder && event.getPlayer() instanceof Player player) {
            closed.accept(player);
        }
        if (event.getInventory().getHolder() instanceof SecretHolder holder && event.getPlayer() instanceof Player player
            && !holder.done) {
            if (holder.salsa) {
                give(player, items.create(DonPollosProduct.SALSA, 1));
            }
            if (holder.picante) {
                give(player, items.create(DonPollosProduct.PICANTE, 1));
            }
            holder.salsa = false;
            holder.picante = false;
        }
    }

    private static void give(Player player, ItemStack stack) {
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    // ------------------------------------------------------------ el bloque y el ritual

    ItemStack secretBlock() {
        ItemStack block = new ItemStack(Material.BEACON);
        block.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
            net.kyori.adventure.key.Key.key("donpollos", GOOD_MODEL));
        ItemMeta meta = block.getItemMeta();
        meta.displayName(text("§4§lSecreto del Don Pollo"));
        meta.lore(List.of(text("§7Lo que sabe el Don Pollo Gordito."), text("§7Ponelo en el piso... si te animas.")));
        meta.getPersistentDataContainer().set(secretKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        block.setItemMeta(meta);
        return block;
    }

    private boolean isSecret(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
            && stack.getItemMeta().getPersistentDataContainer().has(secretKey, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isSecret(event.getItemInHand())) {
            return;
        }
        if (event.getBlock().getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(LEGACY.deserialize(
                "§cEn dificultad pacifica no puede aparecer el Don Pollo Boss. §7Usa /difficulty easy."));
            return;
        }
        Block block = event.getBlock();
        rituals.add(new Ritual(block, Bukkit.getCurrentTick(), faceCube(block)));
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
    }

    /** El cubo con la cara de Don Pollo, un poquito mas grande que el beacon para taparlo entero. */
    private static ItemDisplay faceCube(Block block) {
        return block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, display -> {
            display.setItemStack(model(GOOD_MODEL));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(1.02f, 1.02f, 1.02f), new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
        });
    }

    private static ItemStack model(String key) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
            net.kyori.adventure.key.Key.key("donpollos", key));
        return stack;
    }

    /** El bloque no se puede romper mientras invoca. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        for (Ritual ritual : rituals) {
            if (ritual.block.equals(event.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void tickRituals() {
        long now = Bukkit.getCurrentTick();
        Iterator<Ritual> iterator = rituals.iterator();
        while (iterator.hasNext()) {
            Ritual ritual = iterator.next();
            Location center = ritual.block.getLocation().add(0.5, 0.5, 0.5);
            World world = center.getWorld();
            long story = now - ritual.start;
            long storyLength = STORY.size() * STORY_TICKS;
            if (story < storyLength) {
                // primero cuenta la historia, una frase cada STORY_TICKS
                if (story % STORY_TICKS == 0) {
                    narrate(center, STORY.get((int) (story / STORY_TICKS)));
                }
                if (story % 10 == 0) {
                    world.spawnParticle(Particle.END_ROD, center, 2, 0.4, 0.4, 0.4, 0.01, null, true);
                }
                continue;
            }
            if (story == storyLength) {
                turnEvil(ritual, center);
            }
            long t = story - storyLength;
            if (t < RITUAL_TICKS) {
                swirl(center, t);
            } else if (t == RITUAL_TICKS) {
                burst(ritual, center);
            } else if (t <= RITUAL_TICKS + SPHERE_TICKS) {
                sphere(center, (t - RITUAL_TICKS) * 0.5);
            } else {
                iterator.remove();
            }
            if (t < RITUAL_TICKS && t % 20 == 0) {
                world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.6f + t / (float) RITUAL_TICKS);
            }
            if (t == RITUAL_TICKS - 20) {
                world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 1.5f, 1.4f);
            }
        }
    }

    /** Le cuenta la frase a todos los que estan cerca (abajo en la pantalla y en el chat). */
    private static void narrate(Location center, String phrase) {
        Title title = Title.title(Component.empty(), LEGACY.deserialize("§e§o" + phrase),
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2300), Duration.ofMillis(400)));
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= STORY_RADIUS * STORY_RADIUS) {
                player.showTitle(title);
                player.sendMessage(LEGACY.deserialize("§7§o" + phrase));
                player.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 0.8f);
            }
        }
    }

    /** "Desde ese dia algo cambio en Don Pollo...": el bloque pasa a ser el Don Pollo malo. */
    private static void turnEvil(Ritual ritual, Location center) {
        World world = center.getWorld();
        if (ritual.display.isValid()) {
            ritual.display.setItemStack(model(EVIL_MODEL));
        }
        // el cubo con la cara lo tapa en Java; Bedrock no ve el cubo y ve el bloque de abajo, que se pone rojo
        ritual.block.setType(Material.NETHER_WART_BLOCK, false);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.8f);
        world.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 1.5f, 0.6f);
        world.spawnParticle(Particle.DUST, center, 60, 0.7, 0.7, 0.7, 0,
            new Particle.DustOptions(Color.fromRGB(255, 20, 20), 1.8f), true);
    }

    /** Particulas rojas y blancas que suben girando alrededor del bloque, cada vez mas. */
    private static void swirl(Location center, long t) {
        World world = center.getWorld();
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(220, 20, 30), 1.2f);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(245, 245, 245), 1.2f);
        int strands = 2 + (int) (t * 4 / RITUAL_TICKS);
        for (int i = 0; i < strands; i++) {
            double angle = t * 0.35 + i * Math.PI * 2 / strands;
            double height = (t % 20) / 10.0;
            double radius = 1.2 - height * 0.35;
            Location at = center.clone().add(Math.cos(angle) * radius, height - 0.4, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, i % 2 == 0 ? red : white, true);
        }
    }

    /** Explota: el bloque desaparece y del medio sale el Don Pollo Boss. */
    private void burst(Ritual ritual, Location center) {
        World world = center.getWorld();
        world.spawnParticle(Particle.BLOCK, center, 40, 0.3, 0.3, 0.3, 0, Material.BEACON.createBlockData(), true);
        ritual.display.remove();
        ritual.block.setType(Material.AIR);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0, null, true);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.7f);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.2f, 1.2f);
        sphere(center, 0.5);
        try {
            bosses.spawn(ritual.block.getLocation().add(0.5, 0, 0.5));
        } catch (IllegalStateException cannotSpawn) {
            world.dropItemNaturally(center, secretBlock());
            plugin.getLogger().warning("No se pudo invocar al Don Pollo Boss: " + cannotSpawn.getMessage());
        }
    }

    /** Esfera de particulas rojas y blancas que se abre desde el centro. */
    private static void sphere(Location center, double radius) {
        World world = center.getWorld();
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(220, 20, 30), 1.6f);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(245, 245, 245), 1.6f);
        int points = 60 + (int) (radius * 25);
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - 2 * (i + 0.5) / points;
            double ring = Math.sqrt(1 - y * y);
            Location at = center.clone().add(Math.cos(golden * i) * ring * radius, y * radius + 1,
                Math.sin(golden * i) * ring * radius);
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, i % 2 == 0 ? red : white, true);
        }
    }

    // ------------------------------------------------------------ ayuda

    private static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(text(name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(text(line));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class SecretHolder implements InventoryHolder {
        private Inventory inventory;
        private boolean salsa;
        private boolean picante;
        private boolean done;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record Ritual(Block block, long start, ItemDisplay display) {
    }

    /** Si se apaga el plugin a mitad de la historia, saca los cubos de la cara (el beacon queda). */
    void clearRituals() {
        for (Ritual ritual : rituals) {
            ritual.display.remove();
        }
        rituals.clear();
    }
}
