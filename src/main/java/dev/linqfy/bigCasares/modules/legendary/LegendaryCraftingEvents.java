package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

final class LegendaryCraftingEvents implements Listener, Runnable {
    private static final NamespacedKey VISUAL = new NamespacedKey("bigcasares", "legendary_event_visual");
    private static final NamespacedKey PREVIEW = new NamespacedKey("bigcasares", "legendary_event_preview");
    private static final NamespacedKey COMMIT = new NamespacedKey("bigcasares", "legendary_event_commit");
    private static final NamespacedKey INSTANCE = new NamespacedKey("bigcasares", "legendary_instance");
    private static final LegendaryCraftingPlan.Items<ItemStack> ITEMS = new LegendaryCraftingPlan.Items<>() {
        public String material(ItemStack item) { return item.getType().name(); }
        public int amount(ItemStack item) { return item.getAmount(); }
        public int maximum(ItemStack item) { return item.getMaxStackSize(); }
        public boolean eligible(ItemStack item) {
            return !item.getType().isAir() && (!item.hasItemMeta()
                || (!item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("bigcasares", "item_id"))
                && !item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("bigcasares", "legendary_receipt"))));
        }
        public boolean similar(ItemStack first, ItemStack second) { return first.isSimilar(second); }
        public ItemStack amount(ItemStack item, int amount) { var copy = item.clone(); copy.setAmount(amount); return copy; }
    };

    private final BigCasares plugin;
    private final LegendaryStateStore store;
    private final LegendaryCraftingLedger ledger;
    private final LegendaryCombatPolicy policy;
    private final BukkitRuntimeRegistrations registrations;
    private final Map<NamespacedKey, LegendaryWeapon> recipes = new HashMap<>();
    private final Map<UUID, Visual> visuals = new HashMap<>();
    private final Set<UUID> queued = new HashSet<>();
    private boolean failed;

    LegendaryCraftingEvents(BigCasares plugin, LegendaryStateStore store, BukkitRuntimeRegistrations registrations) {
        this.plugin = plugin; this.store = store; this.registrations = registrations;
        this.ledger = new LegendaryCraftingLedger(store, new java.security.SecureRandom());
        this.policy = new LegendaryCombatPolicy(plugin);
        for (var weapon : LegendaryWeapon.available()) {
            var key = new NamespacedKey("legendaryweapons", "legendary_" + weapon.id);
            ItemStack preview = new ItemStack(Material.BARRIER);
            var meta = preview.getItemMeta();
            meta.displayName(Component.text("Iniciar fabricación: " + weapon.displayName + " · 20 minutos"));
            meta.getPersistentDataContainer().set(PREVIEW, PersistentDataType.STRING, weapon.id);
            preview.setItemMeta(meta);
            var recipe = new ShapelessRecipe(key, preview);
            weapon.ingredients.forEach((material, amount) -> recipe.addIngredient(amount, Material.valueOf(material)));
            if (!registrations.registerRecipe("legendary-event-recipe-" + weapon.id, key, recipe)) {
                throw new IllegalStateException("No se pudo registrar una receta legendaria.");
            }
            recipes.put(key, weapon);
        }
    }

    LegendaryCraftingLedger ledger() { return ledger; }
    boolean pending(UUID player) { return queued.contains(player) || ledger.pending(player); }
    void discover(Player player) { player.discoverRecipes(recipes.keySet()); }

    private LegendaryWeapon recipe(org.bukkit.inventory.Recipe recipe) {
        return recipe instanceof org.bukkit.Keyed keyed ? recipes.get(keyed.getKey()) : null;
    }

    @EventHandler public void prepare(PrepareItemCraftEvent event) {
        var weapon = recipe(event.getRecipe());
        if (weapon == null) return;
        if (failed || ledger.find(weapon).isPresent() || event.getInventory().getMatrix().length != 9) {
            event.getInventory().setResult(null);
            return;
        }
        for (var item : event.getInventory().getMatrix()) {
            if (item == null || !ITEMS.eligible(item)) { event.getInventory().setResult(null); return; }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        var weapon = recipe(event.getRecipe());
        if (weapon == null) return;
        event.setCancelled(true); // The result is a GUI preview, never a transferable item.
        if (!(event.getWhoClicked() instanceof Player player) || pending(player.getUniqueId()) || failed) return;
        if (!emptyCursor(event.getCursor())) {
            player.sendMessage("§eDeja el cursor vacío antes de iniciar la fabricación.");
            return;
        }
        if (ledger.find(weapon).isPresent()) { player.sendMessage("§cEsta arma ya fue reservada."); return; }
        if (store.get("spear." + player.getUniqueId()) != null) {
            player.sendMessage("§eRecupera tu lanza antes de iniciar otra fabricación."); return;
        }
        CraftingInventory inventory = event.getInventory();
        Location table = inventory.getLocation();
        if (!validTable(player, table)) return;
        ItemStack[] matrix = copy(inventory.getMatrix());
        queued.add(player.getUniqueId());
        registrations.scheduleImmediate("legendary-event-start", () -> {
            try {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                    || !emptyCursor(player.getItemOnCursor())
                    || !Arrays.equals(matrix, inventory.getMatrix()) || !validTable(player, table)) return;
                if (ledger.find(weapon).isPresent()) throw new IllegalArgumentException("Esta arma ya fue reservada.");
                var plan = LegendaryCraftingPlan.create(weapon, Arrays.asList(copy(player.getInventory().getStorageContents())),
                    Arrays.asList(matrix), ITEMS);
                // Move the unsaved crafting grid into durable player storage first.
                inventory.setMatrix(new ItemStack[9]);
                player.getInventory().setStorageContents(plan.before().toArray(ItemStack[]::new));
                player.closeInventory();
                LegendaryPlayerSaveVerifier.checkpoint(player);
                var transaction = transaction(player, plan.after().toArray(ItemStack[]::new), LegendaryCraftingSession.TransactionKind.START);
                player.getInventory().setStorageContents(plan.after().toArray(ItemStack[]::new));
                try {
                    // The deadline begins after consumption; no other server tick can
                    // observe this inventory before its journal reservation exists.
                    ledger.start(weapon, table.getWorld().getUID(), dimension(table.getWorld()), table.getBlockX(), table.getBlockY(), table.getBlockZ(),
                        System.currentTimeMillis(), transaction);
                } catch (IOException | RuntimeException failure) {
                    if (!ledger.pending(player.getUniqueId())) {
                        player.getInventory().setStorageContents(plan.before().toArray(ItemStack[]::new));
                        LegendaryPlayerSaveVerifier.checkpoint(player);
                    }
                    throw failure;
                }
                recover(player);
                run();
            } catch (IllegalArgumentException failure) {
                player.sendMessage("§c" + failure.getMessage());
            } catch (IOException | RuntimeException failure) {
                quarantine(player, failure);
            } finally { queued.remove(player.getUniqueId()); }
        });
    }

    private boolean validTable(Player player, Location table) {
        return table != null && player.getWorld().equals(table.getWorld())
            && player.getLocation().distanceSquared(table.clone().add(0.5, 0.5, 0.5)) <= 64
            && table.getBlock().getType() == Material.CRAFTING_TABLE
            && table.clone().add(0, 1, 0).getBlock().getType().isAir()
            && policy.allowed(player, table, null, LegendaryProtectionEvent.Action.ALTAR)
            && policy.allowed(player, table.clone().add(0, 1, 0), null, LegendaryProtectionEvent.Action.ALTAR);
    }

    static boolean emptyCursor(ItemStack cursor) {
        return cursor == null || cursor.getType() == Material.AIR || cursor.getAmount() == 0;
    }

    private static LegendaryCraftingSession.Pending transaction(Player player, ItemStack[] after, LegendaryCraftingSession.TransactionKind kind) {
        return new LegendaryCraftingSession.Pending(player.getUniqueId(), UUID.randomUUID(),
            Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(after)), kind);
    }

    void recover(Player player) {
        try {
            for (var session : ledger.history()) for (var transaction : session.transactions()) {
                if (!transaction.player().equals(player.getUniqueId())) continue;
                LegendaryInventoryTransaction.finish(transaction, new LegendaryInventoryTransaction.Inventory() {
                    public String marker() { return player.getPersistentDataContainer().get(COMMIT, PersistentDataType.STRING); }
                    public void replace(String encoded) {
                        player.getInventory().setStorageContents(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded)));
                    }
                    public void mark(String nonce) { player.getPersistentDataContainer().set(COMMIT, PersistentDataType.STRING, nonce); }
                    public void saveAndVerify(String nonce) throws IOException { LegendaryPlayerSaveVerifier.save(player, COMMIT, nonce); }
                }, () -> ledger.confirm(session.weapon(), transaction.nonce()));
            }
        } catch (IOException | RuntimeException failure) { quarantine(player, failure); }
    }

    private void quarantine(Player player, Exception failure) {
        plugin.getLogger().log(Level.SEVERE, "La transacción legendaria requiere recuperación; no se confirma ni se repite el premio.", failure);
        if (ledger.pending(player.getUniqueId())) registrations.scheduleImmediate("legendary-event-quarantine", () -> {
            if (player.isOnline()) player.kick(Component.text("Tu transacción legendaria está guardada para recuperación. Contacta con administración."));
        });
        else player.sendMessage("§cNo se pudo guardar la fabricación. No se han confirmado consumos.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        var session = at(block);
        if (session == null) return;
        event.setCancelled(true);
        if (session.stage() == LegendaryCraftingSession.Stage.READY_TO_CLAIM) claim(event.getPlayer(), session);
        else if (session.stage() == LegendaryCraftingSession.Stage.CRAFTING) {
            event.getPlayer().sendMessage("§eFabricación legendaria en curso: " + seconds(session) + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void clickDisplay(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String id = event.getRightClicked().getPersistentDataContainer().get(VISUAL, PersistentDataType.STRING);
        if (id == null) return;
        event.setCancelled(true);
        ledger.all().stream().filter(s -> s.id().toString().equals(id)).findFirst().ifPresent(s -> claim(event.getPlayer(), s));
    }

    private void claim(Player player, LegendaryCraftingSession session) {
        if (failed || pending(player.getUniqueId()) || session.stage() != LegendaryCraftingSession.Stage.READY_TO_CLAIM) return;
        World world = plugin.getServer().getWorld(session.world());
        if (world == null || !player.getWorld().equals(world) || !world.isChunkLoaded(session.x() >> 4, session.z() >> 4)) return;
        Location table = location(session, world);
        if (player.getLocation().distanceSquared(table.clone().add(0.5, 1, 0.5)) > 36
            || !policy.allowed(player, table, null, LegendaryProtectionEvent.Action.ALTAR)) return;
        if (store.get("spear." + player.getUniqueId()) != null) {
            player.sendMessage("§eRecupera tu lanza antes de reclamar el premio."); return;
        }
        int slot = player.getInventory().firstEmpty();
        if (slot < 0 || slot >= 36) { player.sendMessage("§cLibera un espacio para reclamar el arma."); return; }
        try {
            ItemStack[] after = copy(player.getInventory().getStorageContents());
            ItemStack weapon = plugin.getCustomItemRegistry().findById(session.weapon().id).orElseThrow().createItemStack(1);
            var meta = weapon.getItemMeta();
            meta.getPersistentDataContainer().set(INSTANCE, PersistentDataType.STRING, session.id().toString());
            weapon.setItemMeta(meta);
            after[slot] = weapon;
            LegendaryPlayerSaveVerifier.checkpoint(player);
            ledger.claim(session.weapon(), transaction(player, after, LegendaryCraftingSession.TransactionKind.CLAIM));
            removeVisual(session.id());
            recover(player);
            run();
        } catch (IllegalArgumentException failure) { player.sendMessage("§eEl arma ya fue reclamada."); }
        catch (IOException | RuntimeException failure) { quarantine(player, failure); }
    }

    @Override public void run() {
        if (failed) return;
        try {
            for (var notice : ledger.advance(System.currentTimeMillis())) {
                if (notice.index() == 4) render(notice.session());
                for (String message : LegendaryCraftingMessages.notice(notice, notice.session().dimension())) plugin.getServer().broadcastMessage(message);
            }
            for (var session : ledger.all()) render(session);
        } catch (IOException | RuntimeException failure) {
            failed = true;
            plugin.getLogger().log(Level.SEVERE, "Eventos legendarios detenidos por un fallo persistente; reinicia tras recuperar el diario.", failure);
        }
    }

    private void render(LegendaryCraftingSession session) {
        if (session.stage() == LegendaryCraftingSession.Stage.CLAIMED) { removeVisual(session.id()); return; }
        World world = plugin.getServer().getWorld(session.world());
        if (world == null || !world.isChunkLoaded(session.x() >> 4, session.z() >> 4)) { removeVisual(session.id()); return; }
        Location table = location(session, world);
        if (table.getBlock().getType() != Material.CRAFTING_TABLE) table.getBlock().setType(Material.CRAFTING_TABLE, false);
        Visual visual = visuals.computeIfAbsent(session.id(), ignored -> new Visual());
        if (visual.text == null || !visual.text.isValid()) {
            visual.text = world.spawn(table.clone().add(0.5, 2.1, 0.5), TextDisplay.class, text -> {
                mark(text, session); text.setBillboard(Display.Billboard.CENTER); text.setViewRange(0.4f);
            });
        }
        boolean ready = session.stage() == LegendaryCraftingSession.Stage.READY_TO_CLAIM;
        visual.text.text(Component.text(session.weapon().displayName + "\n" + (ready ? "¡Clic derecho para reclamar!" : seconds(session))));
        if (ready && (visual.item == null || !visual.item.isValid())) {
            visual.item = world.spawn(table.clone().add(0.5, 1.12, 0.5), ItemDisplay.class, display -> {
                mark(display, session);
                display.setItemStack(plugin.getCustomItemRegistry().findById(session.weapon().id).orElseThrow().createItemStack(1));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setTransformation(new org.bukkit.util.Transformation(new Vector3f(),
                    new Quaternionf().rotateX((float) -Math.PI / 2), new Vector3f(0.8f), new Quaternionf()));
            });
        }
        if (ready && (visual.hitbox == null || !visual.hitbox.isValid())) {
            visual.hitbox = world.spawn(table.clone().add(0.5, 1, 0.5), Interaction.class, entity -> {
                mark(entity, session); entity.setInteractionWidth(0.95f); entity.setInteractionHeight(0.8f); entity.setResponsive(true);
            });
        }
        int amount = ready ? 5 : session.remaining(System.currentTimeMillis()) < 300_000 ? 8 : 2;
        Particle particle = switch (session.weapon()) {
            case BLOODLUST -> Particle.CRIMSON_SPORE;
            case FROST_SCYTHE -> Particle.SNOWFLAKE;
            case SHADOW_BLADE, VOID_KATANA -> Particle.PORTAL;
            case HYPERION -> Particle.ENCHANT;
            case WINDWEAVER -> Particle.CLOUD;
            case EARTHBREAKER -> Particle.ASH;
            case THUNDER_SPEAR -> Particle.ELECTRIC_SPARK;
        };
        world.spawnParticle(particle, table.clone().add(0.5, 1.3, 0.5), amount, 0.3, 0.2, 0.3, 0.01);
    }

    private static String dimension(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld (" + world.getName() + ")";
            case NETHER -> "Nether (" + world.getName() + ")";
            case THE_END -> "End (" + world.getName() + ")";
            default -> world.getName();
        };
    }

    private static String seconds(LegendaryCraftingSession session) {
        long seconds = (session.remaining(System.currentTimeMillis()) + 999) / 1000;
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }
    private static Location location(LegendaryCraftingSession session, World world) { return new Location(world, session.x(), session.y(), session.z()); }
    private static ItemStack[] copy(ItemStack[] items) { return Arrays.stream(items).map(i -> i == null || i.getType().isAir() ? null : i.clone()).toArray(ItemStack[]::new); }
    private static void mark(Entity entity, LegendaryCraftingSession session) {
        entity.setPersistent(false); entity.setInvulnerable(true);
        entity.getPersistentDataContainer().set(VISUAL, PersistentDataType.STRING, session.id().toString());
    }
    private LegendaryCraftingSession at(Block block) {
        return ledger.all().stream().filter(s -> s.protectsTable() && s.world().equals(block.getWorld().getUID())
            && s.x() == block.getX() && s.y() == block.getY() && s.z() == block.getZ()).findFirst().orElse(null);
    }
    boolean protectedCell(Block block) {
        return ledger.protectedCell(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
    void clean(Entity[] entities) {
        for (var entity : entities) if (entity.getPersistentDataContainer().has(VISUAL)) entity.remove();
    }
    private void removeVisual(UUID id) { var visual = visuals.remove(id); if (visual != null) visual.remove(); }
    void shutdown() { visuals.values().forEach(Visual::remove); visuals.clear(); queued.clear(); }
    private static final class Visual {
        TextDisplay text; ItemDisplay item; Interaction hitbox;
        void remove() { if (text != null) text.remove(); if (item != null) item.remove(); if (hitbox != null) hitbox.remove(); }
    }

    @EventHandler public void join(PlayerJoinEvent event) { recover(event.getPlayer()); discover(event.getPlayer()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        registrations.scheduleImmediate("legendary-event-respawn", () -> recover(event.getPlayer()));
    }
    @EventHandler(priority = EventPriority.LOWEST) public void death(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (ledger.pending(event.getEntity().getUniqueId())) { event.getDrops().clear(); event.setKeepInventory(true); }
    }
    @EventHandler public void load(ChunkLoadEvent event) { clean(event.getChunk().getEntities()); }
    @EventHandler public void unload(ChunkUnloadEvent event) {
        for (var session : ledger.all()) if (session.world().equals(event.getWorld().getUID())
            && (session.x() >> 4) == event.getChunk().getX() && (session.z() >> 4) == event.getChunk().getZ()) removeVisual(session.id());
    }
    @EventHandler public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && pending(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void automation(CrafterCraftEvent event) {
        if (event.getResult().hasItemMeta() && event.getResult().getItemMeta().getPersistentDataContainer().has(PREVIEW)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void breakBlock(BlockBreakEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void place(BlockPlaceEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void multiPlace(org.bukkit.event.block.BlockMultiPlaceEvent event) {
        if (event.getReplacedBlockStates().stream().anyMatch(state -> protectedCell(state.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void entityExplosion(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedCell); }
    @EventHandler(ignoreCancelled = true) public void blockExplosion(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedCell); }
    @EventHandler(ignoreCancelled = true) public void extend(BlockPistonExtendEvent event) {
        if (protectedCell(event.getBlock().getRelative(event.getDirection()))
            || event.getBlocks().stream().anyMatch(b -> protectedCell(b) || protectedCell(b.getRelative(event.getDirection())))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void retract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> protectedCell(b) || protectedCell(b.getRelative(event.getDirection())))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void flow(BlockFromToEvent event) { if (protectedCell(event.getToBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void bucket(PlayerBucketEmptyEvent event) {
        if (protectedCell(event.getBlockClicked().getRelative(event.getBlockFace()))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void burn(BlockBurnEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void ignite(org.bukkit.event.block.BlockIgniteEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void destroy(com.destroystokyo.paper.event.block.BlockDestroyEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void fade(BlockFadeEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void form(BlockFormEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void change(EntityChangeBlockEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void tree(org.bukkit.event.world.StructureGrowEvent event) {
        if (event.getBlocks().stream().anyMatch(state -> protectedCell(state.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void fertilize(org.bukkit.event.block.BlockFertilizeEvent event) {
        if (event.getBlocks().stream().anyMatch(state -> protectedCell(state.getBlock()))) event.setCancelled(true);
    }
}
