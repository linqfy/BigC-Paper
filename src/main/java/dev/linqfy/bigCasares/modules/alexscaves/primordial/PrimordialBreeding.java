package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Breeding and eggs of the Primordial dinosaurs, after the mod's {@code DinosaurEntity} and
 * {@code DinosaurEggBlock}:
 * <ul>
 *   <li>Feeding its breeding food (see {@link TamingRules#breedingFood}) puts an adult in love for
 *       30 s. Two in love of the same kind within 8 blocks lay eggs and rest 5 minutes.</li>
 *   <li>Eggs are placed on top of a block. The egg is an item display that holds the data (kind, hatch stage, link
 *       to its box; eggs placed before the models existed are the same) and an interaction box to pick it back up.
 *       What players see is the BetterModel egg of the mod's block model ({@link EggModels}), attached to the box and
 *       swapped as the egg cracks; the item display is hidden once the model is up, and stays visible if it cannot be.</li>
 *   <li>Eggs hatch in 3 stages at the mod's random-tick pace, twice as fast on fern thatch. The
 *       baby is half size for 20 minutes; Subterranodon, Vallumraptor and Tremorsaurus babies are
 *       tamed to the nearest player within 10 blocks.</li>
 * </ul>
 */
public final class PrimordialBreeding implements Listener, Runnable {
    static final int LOVE_TICKS = 600;
    static final double PAIR_DISTANCE = 8.0;
    /** Mod {@code AnimalLayEggGoal} maxTime: the mother buries her eggs for this long. */
    static final int BURY_TICKS = 100;

    private final ModMobService mobs;
    private final BlockData fernThatch;
    private final NamespacedKey ownerKey;
    private final NamespacedKey babyKey;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey eggKey;
    private final NamespacedKey stageKey;
    private final NamespacedKey linkKey;
    private final Map<UUID, Long> inLove = new HashMap<>();
    private final Set<UUID> eggs = new HashSet<>();
    private final Map<UUID, ShownEgg> shown = new HashMap<>();
    private long tick;

    public PrimordialBreeding(ModMobService mobs, BlockData fernThatch) {
        this.mobs = mobs;
        this.fernThatch = fernThatch;
        this.ownerKey = new NamespacedKey(mobs.plugin(), TamingRules.OWNER_KEY);
        this.babyKey = new NamespacedKey(mobs.plugin(), TamingRules.BABY_UNTIL_KEY);
        this.cooldownKey = new NamespacedKey(mobs.plugin(), TamingRules.BREED_COOLDOWN_KEY);
        this.eggKey = new NamespacedKey(mobs.plugin(), "alexscaves_egg");
        this.stageKey = new NamespacedKey(mobs.plugin(), "alexscaves_egg_stage");
        this.linkKey = new NamespacedKey(mobs.plugin(), "alexscaves_egg_link");
    }

    // --- Breeding ---------------------------------------------------------------------------------

    /** After the taming listener, which cancels what it handles. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (clicked instanceof Interaction part) {
            // The sauropods' neck and tail boxes count as clicking the body.
            clicked = SauropodBody.partOwner(part).map(id -> mobs.plugin().getServer().getEntity(id)).orElse(null);
        }
        if (!(clicked instanceof LivingEntity entity)) {
            return;
        }
        Optional<ModMobType> type = mobs.typeOf(entity);
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (type.isEmpty() || held.getType().isAir() || isBaby(entity)) {
            return;
        }
        String item = mobs.itemId(held).orElse(held.getType().name());
        String owner = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        boolean tamed = owner != null;
        if (tamed && !owner.equals(player.getUniqueId().toString())) {
            return;
        }
        if (!TamingRules.breedingFood(type.get(), item, tamed)) {
            return;
        }
        long fullTime = entity.getWorld().getGameTime();
        long stored = entity.getPersistentDataContainer().getOrDefault(cooldownKey, PersistentDataType.LONG, 0L);
        if (inLove.containsKey(entity.getUniqueId())
            || TamingRules.clampDeadline(stored, fullTime, TamingRules.BREED_COOLDOWN_TICKS) > fullTime) {
            // Mod: food that cannot be eaten falls through to the rest of mobInteract (the Atlatitan is then mounted).
            if (type.get().equals(Atlatitan.ATLATITAN) && Atlatitan.mount(entity, player)) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        inLove.put(entity.getUniqueId(), tick + LOVE_TICKS);
        PrimordialDinosaur fed = PrimordialDinosaur.of(mobs, entity);
        if (fed != null && type.get() == PrimordialMobs.GROTTOCERATOPS) {
            // Mod GrottoceratopsEntity.setInLove: it chews the food.
            fed.request("chew");
        }
        entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, entity.getHeight(), 0), 5, 0.4, 0.4, 0.4);
    }

    private boolean isBaby(Entity entity) {
        return entity.getPersistentDataContainer().has(babyKey, PersistentDataType.LONG);
    }

    private void pairUp() {
        inLove.values().removeIf(until -> until <= tick);
        Set<UUID> paired = new HashSet<>();
        for (UUID id : Set.copyOf(inLove.keySet())) {
            if (paired.contains(id) || !(mobs.plugin().getServer().getEntity(id) instanceof LivingEntity first)) {
                continue;
            }
            Optional<ModMobType> type = mobs.typeOf(first);
            if (type.isEmpty()) {
                continue;
            }
            for (Entity nearby : first.getNearbyEntities(PAIR_DISTANCE, PAIR_DISTANCE, PAIR_DISTANCE)) {
                if (nearby instanceof LivingEntity second && inLove.containsKey(nearby.getUniqueId())
                    && !paired.contains(nearby.getUniqueId()) && mobs.typeOf(second).equals(type)) {
                    paired.add(id);
                    paired.add(nearby.getUniqueId());
                    layEggs(first, second, type.get());
                    break;
                }
            }
        }
        paired.forEach(inLove::remove);
    }

    private void layEggs(LivingEntity first, LivingEntity second, ModMobType type) {
        long until = first.getWorld().getGameTime() + TamingRules.BREED_COOLDOWN_TICKS;
        first.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, until);
        second.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, until);
        int amount = TamingRules.eggsLaid(type, ThreadLocalRandom.current().nextInt(3));
        Location at = first.getLocation().add(second.getLocation()).multiply(0.5);
        PrimordialDinosaur mother = PrimordialDinosaur.of(mobs, first);
        if (mother != null) {
            // Mod AnimalLayEggGoal: about 100 ticks burying the eggs (the eggs themselves drop at once here).
            mother.buryEggs(BURY_TICKS);
        }
        mobs.createItem("ac_" + type.id() + "_egg", amount)
            .ifPresent(egg -> first.getWorld().dropItemNaturally(at, egg));
        first.getWorld().spawnParticle(Particle.HEART, at.clone().add(0, 1, 0), 10, 0.6, 0.4, 0.6);
        first.getWorld().playSound(at, Sound.ENTITY_TURTLE_LAY_EGG, 1.0f, 1.0f);
    }

    // --- Eggs -------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onPlaceEgg(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || held == null
            || event.getBlockFace() != BlockFace.UP || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Optional<String> mobId = mobs.itemId(held).filter(id -> id.endsWith("_egg")).map(id -> id.substring(3, id.length() - 4))
            .filter(id -> mobs.knownIds().contains(id));
        if (mobId.isEmpty()) {
            return;
        }
        if (clicked.getType().isInteractable() && !event.getPlayer().isSneaking()) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        Block above = clicked.getRelative(BlockFace.UP);
        if (!above.getType().isAir() || !clicked.getType().isSolid() || hasEgg(above)) {
            return;
        }
        BlockPlaceEvent check = new BlockPlaceEvent(above, above.getState(), clicked, held, event.getPlayer(), true,
            EquipmentSlot.HAND);
        if (!check.callEvent()) {
            return;
        }
        Location center = above.getLocation().add(0.5, 0, 0.5);
        ItemStack shown = held.clone();
        shown.setAmount(1);
        // Mod: the Atlatitan's egg is a full block (16 px); the others are 10 to 14 px.
        boolean big = Atlatitan.ATLATITAN.id().equals(mobId.get());
        float eggScale = big ? 2.0f : 1.2f;
        ItemDisplay display = above.getWorld().spawn(center.clone().add(0, big ? 0.5 : 0.3, 0), ItemDisplay.class, spawned -> {
            spawned.setItemStack(shown);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            spawned.setBillboard(Display.Billboard.VERTICAL);
            spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(eggScale, eggScale, eggScale), new AxisAngle4f()));
            spawned.setPersistent(true);
            spawned.getPersistentDataContainer().set(eggKey, PersistentDataType.STRING, mobId.get());
            spawned.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, 0);
        });
        Interaction box = above.getWorld().spawn(center, Interaction.class, spawned -> {
            spawned.setInteractionWidth(big ? 1.0f : 0.5f);
            spawned.setInteractionHeight(big ? 1.0f : 0.6f);
            spawned.setPersistent(true);
            spawned.getPersistentDataContainer().set(linkKey, PersistentDataType.STRING, display.getUniqueId().toString());
        });
        display.getPersistentDataContainer().set(linkKey, PersistentDataType.STRING, box.getUniqueId().toString());
        eggs.add(display.getUniqueId());
        showModel(display);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        above.getWorld().playSound(center, Sound.ENTITY_TURTLE_LAY_EGG, 0.8f, 1.2f);
    }

    // --- Egg models -------------------------------------------------------------------------------

    /** What an egg's model is attached to and which stage it shows. */
    private record ShownEgg(UUID box, int stage, JavaModelHandle handle) {
    }

    /**
     * Makes sure the egg wears the model of its current stage on its box. Idempotent; leaves the item display visible
     * when there is no BetterModel, no model or no box.
     */
    private void showModel(ItemDisplay egg) {
        JavaModelGateway models = mobs.models();
        PersistentDataContainer data = egg.getPersistentDataContainer();
        String mobId = data.get(eggKey, PersistentDataType.STRING);
        String link = data.get(linkKey, PersistentDataType.STRING);
        if (mobId == null) {
            return;
        }
        if (models == null || link == null || !(linked(link) instanceof Interaction box) || !box.isValid()) {
            showItem(egg, mobId);
            return;
        }
        int stage = data.getOrDefault(stageKey, PersistentDataType.INTEGER, 0);
        Optional<String> modelKey = EggModels.key(mobId, stage);
        if (modelKey.isEmpty()) {
            showItem(egg, mobId);
            return;
        }
        ShownEgg current = shown.get(egg.getUniqueId());
        if (current != null && current.stage() == stage && current.box().equals(box.getUniqueId())) {
            return;
        }
        releaseModel(egg.getUniqueId());
        try {
            JavaModelHandle handle = models.attach(box, modelKey.get());
            if (Tremorzilla.ID.equals(mobId)) {
                // Mod: the second element of the egg's model is a glow layer, drawn at full brightness.
                models.brighten(handle, 15, 15, List.of("glow"));
            }
            shown.put(egg.getUniqueId(), new ShownEgg(box.getUniqueId(), stage, handle));
        } catch (RuntimeException missing) {
            showItem(egg, mobId);
            return;
        }
        // The item stays as the egg's data (and what is given back when it is picked up), but is no longer drawn.
        egg.setViewRange(0.0f);
        egg.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.001f, 0.001f, 0.001f),
            new AxisAngle4f()));
    }

    /** Draws the egg as its item again (an egg hidden by an earlier model, now without one). */
    private static void showItem(ItemDisplay egg, String mobId) {
        if (egg.getViewRange() > 0.0f) {
            return;
        }
        float eggScale = Atlatitan.ATLATITAN.id().equals(mobId) ? 2.0f : 1.2f;
        egg.setViewRange(1.0f);
        egg.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
            new Vector3f(eggScale, eggScale, eggScale), new AxisAngle4f()));
    }

    private void releaseModel(UUID egg) {
        ShownEgg gone = shown.remove(egg);
        JavaModelGateway models = mobs.models();
        if (gone != null && models != null) {
            models.close(gone.handle());
        }
    }

    /** Closes the models of eggs that were removed or whose chunk unloaded; they come back through {@link #showModel}. */
    private void dropStaleModels() {
        for (UUID id : List.copyOf(shown.keySet())) {
            ShownEgg entry = shown.get(id);
            Entity egg = mobs.plugin().getServer().getEntity(id);
            Entity box = entry == null ? null : mobs.plugin().getServer().getEntity(entry.box());
            if (egg == null || !egg.isValid() || box == null || !box.isValid()) {
                releaseModel(id);
            }
        }
    }

    private boolean hasEgg(Block block) {
        for (Entity nearby : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.6, 0.5)) {
            if (nearby instanceof ItemDisplay && nearby.getPersistentDataContainer().has(eggKey, PersistentDataType.STRING)) {
                return true;
            }
        }
        return false;
    }

    /** Right-clicking the egg's box also picks it up. */
    @EventHandler(ignoreCancelled = true)
    public void onUseEgg(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Interaction box && box.getPersistentDataContainer().has(linkKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            pickUp(box, event.getPlayer());
        }
    }

    /** Hitting the egg's box picks it back up. */
    @EventHandler(ignoreCancelled = true)
    public void onHitEgg(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction box)) {
            return;
        }
        String link = box.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING);
        if (link == null) {
            return;
        }
        event.setCancelled(true);
        pickUp(box, event.getPlayer());
    }

    private void pickUp(Interaction box, Player player) {
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        String link = box.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING);
        Entity display = link == null ? null : linked(link);
        if (display instanceof ItemDisplay egg && egg.getPersistentDataContainer().has(eggKey, PersistentDataType.STRING)) {
            // Protection plugins: picking the egg up is breaking its block.
            if (!new BlockBreakEvent(box.getLocation().getBlock(), player).callEvent()) {
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack drop = egg.getItemStack().clone();
                drop.setAmount(1);
                box.getWorld().dropItemNaturally(box.getLocation().add(0, 0.3, 0), drop);
            }
            eggs.remove(egg.getUniqueId());
            releaseModel(egg.getUniqueId());
            egg.remove();
        }
        box.remove();
    }

    private Entity linked(String uuid) {
        try {
            return mobs.plugin().getServer().getEntity(UUID.fromString(uuid));
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(eggKey, PersistentDataType.STRING)) {
                eggs.add(entity.getUniqueId());
            }
        }
    }

    private void tickEggs() {
        dropStaleModels();
        Iterator<UUID> iterator = eggs.iterator();
        while (iterator.hasNext()) {
            Entity entity = mobs.plugin().getServer().getEntity(iterator.next());
            if (entity == null) {
                // Unloaded; it comes back through EntitiesLoadEvent.
                iterator.remove();
                continue;
            }
            if (!(entity instanceof ItemDisplay egg) || !egg.isValid()) {
                iterator.remove();
                continue;
            }
            // Eggs loaded from disk (or whose model was lost) get their model here.
            showModel(egg);
            if (Tremorzilla.ID.equals(egg.getPersistentDataContainer().get(eggKey, PersistentDataType.STRING))) {
                // Mod TremorzillaEggBlock.canHatchAt: never by itself, only in a nuclear explosion.
                continue;
            }
            Block at = egg.getLocation().getBlock();
            if (!at.isPassable()) {
                // Something was built on top of the egg: wait, or the baby would suffocate.
                continue;
            }
            boolean thatch = sameNoteState(fernThatch, at.getRelative(BlockFace.DOWN).getBlockData());
            if (ThreadLocalRandom.current().nextDouble() >= TamingRules.hatchStageChancePerSecond(thatch)) {
                continue;
            }
            PersistentDataContainer data = egg.getPersistentDataContainer();
            int stage = data.getOrDefault(stageKey, PersistentDataType.INTEGER, 0) + 1;
            if (stage < TamingRules.HATCH_STAGES) {
                data.set(stageKey, PersistentDataType.INTEGER, stage);
                showModel(egg);
                egg.getWorld().playSound(egg.getLocation(), Sound.BLOCK_SNIFFER_EGG_CRACK, 0.7f, 1.0f);
                continue;
            }
            iterator.remove();
            hatch(egg);
        }
    }

    static boolean sameNoteState(BlockData expected, BlockData actual) {
        return expected instanceof NoteBlock wanted && actual instanceof NoteBlock found
            && wanted.getInstrument() == found.getInstrument() && wanted.getNote().equals(found.getNote());
    }

    private void hatch(ItemDisplay egg) {
        String mobId = egg.getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
        Location at = egg.getLocation().subtract(0, Atlatitan.ATLATITAN.id().equals(mobId) ? 0.5 : 0.3, 0);
        String link = egg.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING);
        if (link != null && linked(link) instanceof Interaction box) {
            box.remove();
        }
        releaseModel(egg.getUniqueId());
        egg.remove();
        egg.getWorld().playSound(at, Sound.BLOCK_SNIFFER_EGG_HATCH, 0.8f, 1.0f);
        if (mobId == null) {
            return;
        }
        Optional<LivingEntity> spawned = mobs.spawn(mobId, at);
        if (spawned.isEmpty()) {
            // Peaceful or a cancelled spawn: give the egg back instead of losing it.
            egg.getWorld().dropItemNaturally(at, egg.getItemStack());
            return;
        }
        spawned.ifPresent(baby -> {
            // Babies stay until they grow up, tamed or not.
            if (baby instanceof Mob mob) {
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
            }
            if (mobs.typeOf(baby).filter(Atlatitan.ATLATITAN::equals).isPresent()) {
                Atlatitan.applyScale(baby, true);
            } else {
                AttributeInstance scale = baby.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(TamingRules.BABY_SCALE);
                }
            }
            baby.getPersistentDataContainer().set(babyKey, PersistentDataType.LONG,
                baby.getWorld().getGameTime() + TamingRules.BABY_TICKS);
            Optional<ModMobType> type = mobs.typeOf(baby);
            if (type.isPresent() && TamingRules.tamesFromHatching(type.get())) {
                Player closest = null;
                double best = 100.0;
                for (Player player : baby.getWorld().getPlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }
                    double distance = player.getLocation().distanceSquared(at);
                    if (distance <= best) {
                        best = distance;
                        closest = player;
                    }
                }
                if (closest != null) {
                    PrimordialTaming.markTamed(mobs.plugin(), baby, closest.getUniqueId(), true);
                }
            }
        });
    }

    private void growBabies() {
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            long now = player.getWorld().getGameTime();
            for (Entity nearby : player.getNearbyEntities(64, 64, 64)) {
                Long until = nearby.getPersistentDataContainer().get(babyKey, PersistentDataType.LONG);
                if (until != null && TamingRules.clampDeadline(until, now, TamingRules.BABY_TICKS) <= now
                    && nearby instanceof LivingEntity grown) {
                    if (mobs.typeOf(grown).filter(type -> type.id().equals(Tremorzilla.ID)).isPresent()) {
                        // Its giant base has a scale of its own; it stays an animal that never despawns.
                        Tremorzilla.applyScale(grown, false);
                        grown.getPersistentDataContainer().remove(babyKey);
                        continue;
                    }
                    if (mobs.typeOf(grown).filter(Atlatitan.ATLATITAN::equals).isPresent()) {
                        // Its body is a scaled golem: grown up is the full scale, not 1.
                        Atlatitan.applyScale(grown, false);
                    } else {
                        AttributeInstance scale = grown.getAttribute(Attribute.SCALE);
                        if (scale != null) {
                            scale.setBaseValue(1.0);
                        }
                    }
                    grown.getPersistentDataContainer().remove(babyKey);
                    if (grown instanceof Mob mob && !grown.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) {
                        // Wild adults despawn like any other module mob.
                        mob.setPersistent(false);
                        mob.setRemoveWhenFarAway(true);
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        tick++;
        if (tick == 1) {
            // Eggs already loaded when the module enabled never fire EntitiesLoadEvent.
            for (World world : mobs.plugin().getServer().getWorlds()) {
                for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                    if (display.getPersistentDataContainer().has(eggKey, PersistentDataType.STRING)) {
                        eggs.add(display.getUniqueId());
                    }
                }
            }
        }
        if (tick % 10 == 0) {
            pairUp();
        }
        if (tick % 20 == 0) {
            tickEggs();
        }
        if (tick % 200 == 0) {
            growBabies();
        }
    }
}
