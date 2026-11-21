package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialPose.Kind;
import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * What the plain Primordial dinosaurs do besides fighting, after the mod's goals, each one starting the mod's action
 * animation at the moment the goal does:
 * <ul>
 *   <li>Vallumraptor: leaps at a target 3 to 7 blocks away ({@code startleap}), opens wooden doors in its path
 *       ({@code grab}, the door opens at tick 16; a tamed one closes it behind) and takes dropped eggs, chops and meat
 *       ({@code grab}, the item is taken after tick 15) which it eats a while later.</li>
 *   <li>Relicheirus: nibbles high leaves ({@code eat_tree}, effects only as in the mod), and while it is fed primordial
 *       soup pushes trees over ({@code push_tree}, the tree falls at tick 35).</li>
 *   <li>Grottoceratops: grazes curly ferns, tree stars and pewen pines ({@code chew_from_ground}; a curly fern is left
 *       as a fiddlehead).</li>
 * </ul>
 * Every block change goes through {@link EntityChangeBlockEvent} and needs {@code mobGriefing}. Searches are local, rare
 * (the mod's goal intervals) and stop at the nearest hit; every task times out and is dropped when the mob dies.
 * The chest looting of the mod's raptor is not part of this.
 */
final class DinosaurChores {
    private static final int WALK_LIMIT = 600;
    private static final int LEAP_LIMIT = 80;
    private static final int ITEM_SCAN = 20;
    private static final int DOOR_HOLD = 200;
    private static final double ITEM_RADIUS = 9.0;

    private enum Mode { NONE, NIBBLE, PUSH, GRAZE }

    private final Kind kind;
    private final PrimordialBlocks blocks;
    private final PrimordialDinosaur host;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private NamespacedKey ownerKey;

    // raptor
    private int windup = -1;
    private boolean leaping;
    private boolean leapHurt;
    private int leapAge;
    private Block door;
    private int grabDoor = -1;
    private Block openedDoor;
    private int openedAge;
    private UUID wanted;
    private int itemClock = random.nextInt(ITEM_SCAN);
    private UUID grabbing;
    private int grabAge;
    private int eatIn = -1;

    // relicheirus and grottoceratops
    private Mode mode = Mode.NONE;
    private int cooldown = random.nextInt(60);
    private int[] spot;
    private int[] stand;
    private int age;
    private int actAge;
    private boolean acting;
    private boolean done;
    private boolean frozen;
    private int walkClock;

    DinosaurChores(Kind kind, PrimordialBlocks blocks, PrimordialDinosaur host) {
        this.kind = kind;
        this.blocks = blocks;
        this.host = host;
    }

    boolean leaping() {
        return leaping;
    }

    /** Called from the dinosaur's behaviour tick. */
    void tick(LivingEntity entity, ModMobContext context, int ticks, boolean sitting, boolean dancing) {
        if (!(entity instanceof Mob mob) || !entity.isValid() || entity.isDead()) {
            cancel(entity);
            return;
        }
        if (ownerKey == null) {
            ownerKey = new NamespacedKey(context.plugin(), TamingRules.OWNER_KEY);
        }
        if (sitting || dancing || !entity.getPassengers().isEmpty()) {
            cancel(entity);
            return;
        }
        LivingEntity target = mob.getTarget();
        switch (kind) {
            case VALLUMRAPTOR -> raptor(entity, mob, target, ticks);
            case RELICHEIRUS, GROTTOCERATOPS -> grounded(entity, mob, target, ticks);
            default -> {
            }
        }
    }

    /** Drops every task (the mob died, unloaded or started something that excludes them). */
    void cancel(LivingEntity entity) {
        windup = -1;
        leaping = false;
        door = null;
        grabDoor = -1;
        grabbing = null;
        wanted = null;
        if (openedDoor != null) {
            closeDoor(entity);
        }
        stop(entity);
    }

    // --- Vallumraptor ---

    private void raptor(LivingEntity entity, Mob mob, LivingEntity target, int ticks) {
        boolean tamed = entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
        leap(entity, mob, target, tamed, ticks);
        if (leaping || windup >= 0) {
            return;
        }
        doors(entity, mob, tamed, ticks);
        items(entity, mob, target, tamed, ticks);
    }

    /** Mod {@code VallumraptorMeleeGoal}: the leap, started by the startleap animation and made once it passes tick 15. */
    private void leap(LivingEntity entity, Mob mob, LivingEntity target, boolean tamed, int ticks) {
        boolean live = target != null && target.isValid() && !target.isDead() && target.getWorld().equals(entity.getWorld());
        if (leaping) {
            leapAge += ticks;
            if (live && !leapHurt) {
                leapHurt = leapHit(entity, target);
            }
            if (leapAge >= 4 && (entity.isOnGround() || entity.isInWater()) || leapAge > LEAP_LIMIT) {
                leaping = false;
            }
            return;
        }
        if (windup >= 0) {
            if (!live) {
                windup = -1;
                return;
            }
            mob.getPathfinder().stopPathfinding();
            mob.lookAt(target);
            windup += ticks;
            if (windup > DinosaurHabits.LEAP_WINDUP && entity.isOnGround()) {
                Vector velocity = entity.getVelocity();
                Location from = entity.getLocation();
                Location to = target.getLocation();
                double[] jump = DinosaurHabits.leapVelocity(velocity.getX(), velocity.getZ(), to.getX() - from.getX(),
                    to.getZ() - from.getZ());
                entity.setVelocity(new Vector(jump[0], jump[1], jump[2]));
                entity.getWorld().playSound(from, "alexscaves:vallumraptor_attack", SoundCategory.HOSTILE, 1.0F, 1.0F);
                windup = -1;
                leaping = true;
                leapAge = 0;
                leapHurt = false;
            } else if (windup > LEAP_LIMIT) {
                windup = -1;
            }
            return;
        }
        if (!live || !host.free() || !entity.isOnGround()) {
            return;
        }
        double distance = entity.getLocation().distance(target.getLocation());
        if (DinosaurHabits.leapRange(distance)
            && random.nextDouble() < SauropodRules.perTick(DinosaurHabits.leapOdds(tamed), ticks)
            && host.act(entity, "startleap")) {
            windup = 0;
        }
    }

    /** Mod {@code checkAndDealDamage}: a leaping raptor hurts what it lands on. */
    private boolean leapHit(LivingEntity entity, LivingEntity target) {
        double reach = entity.getWidth() + target.getWidth() + 1.0;
        if (entity.hasLineOfSight(target) && entity.getLocation().distance(target.getLocation()) < reach) {
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:vallumraptor_scratch", SoundCategory.HOSTILE, 1.0F, 1.0F);
            AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            target.damage(damage == null ? 3.0 : damage.getValue(), entity);
            return true;
        }
        return false;
    }

    /** Mod {@code VallumraptorOpenDoorGoal}: the grab starts within 2 blocks of a closed door on the path. */
    private void doors(LivingEntity entity, Mob mob, boolean tamed, int ticks) {
        if (openedDoor != null) {
            openedAge += ticks;
            Location at = entity.getLocation();
            boolean away = !openedDoor.getWorld().equals(at.getWorld())
                || openedDoor.getLocation().add(0.5, 0.5, 0.5).distanceSquared(at) > 9.0;
            if (tamed && away || openedAge > DOOR_HOLD || !openedDoor.getWorld().isChunkLoaded(openedDoor.getX() >> 4, openedDoor.getZ() >> 4)) {
                if (tamed) {
                    closeDoor(entity);
                } else {
                    openedDoor = null;
                }
            }
        }
        if (door != null) {
            int previous = grabDoor;
            grabDoor += ticks;
            mob.lookAt(door.getLocation().add(0.5, 0.5, 0.5));
            if (previous < DinosaurHabits.GRAB_OPENS_DOOR && grabDoor >= DinosaurHabits.GRAB_OPENS_DOOR) {
                if (closedWoodenDoor(door) && griefing(entity) && setOpen(entity, door, true)) {
                    openedDoor = door;
                    openedAge = 0;
                }
                door = null;
                grabDoor = -1;
            }
            return;
        }
        if (!host.moving() || !host.free() || !griefing(entity)) {
            return;
        }
        Pathfinder.PathResult path = mob.getPathfinder().getCurrentPath();
        Location next = path == null ? null : path.getNextPoint();
        if (next == null || !next.getWorld().equals(entity.getWorld())) {
            return;
        }
        Block block = next.getBlock();
        Block found = closedWoodenDoor(block) ? block : closedWoodenDoor(block.getRelative(BlockFace.DOWN))
            ? block.getRelative(BlockFace.DOWN) : null;
        if (found == null || found.getLocation().add(0.5, 0.5, 0.5).distanceSquared(entity.getLocation()) >= 4.0) {
            return;
        }
        if (host.act(entity, "grab")) {
            door = lower(found);
            grabDoor = 0;
        }
    }

    private static Block lower(Block block) {
        return block.getBlockData() instanceof Door door && door.getHalf() == org.bukkit.block.data.Bisected.Half.TOP
            ? block.getRelative(BlockFace.DOWN) : block;
    }

    private static boolean closedWoodenDoor(Block block) {
        return Tag.WOODEN_DOORS.isTagged(block.getType()) && block.getBlockData() instanceof Door data && !data.isOpen();
    }

    private void closeDoor(LivingEntity entity) {
        Block closing = openedDoor;
        openedDoor = null;
        if (closing != null && closing.getBlockData() instanceof Door data && data.isOpen() && griefing(entity)) {
            setOpen(entity, closing, false);
        }
    }

    /** Opens or closes a wooden door (both halves) unless a protection plugin vetoes the change. */
    private boolean setOpen(LivingEntity entity, Block lower, boolean open) {
        if (!(lower.getBlockData() instanceof Door base)) {
            return false;
        }
        Block upper = lower.getRelative(base.getHalf() == org.bukkit.block.data.Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
        Openable changed = (Openable) base.clone();
        changed.setOpen(open);
        if (!new EntityChangeBlockEvent(entity, lower, changed).callEvent()) {
            return false;
        }
        lower.setBlockData(changed, false);
        if (upper.getBlockData() instanceof Door other && Tag.WOODEN_DOORS.isTagged(upper.getType())) {
            Door twin = (Door) other.clone();
            twin.setOpen(open);
            upper.setBlockData(twin, false);
        }
        lower.getWorld().playSound(lower.getLocation().add(0.5, 0.5, 0.5),
            open ? org.bukkit.Sound.BLOCK_WOODEN_DOOR_OPEN : org.bukkit.Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Mod {@code MobTargetItemGoal}, {@code onGetItem} and the eating: it takes one dropped egg, chop or meat item. */
    private void items(LivingEntity entity, Mob mob, LivingEntity target, boolean tamed, int ticks) {
        var equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack held = equipment.getItemInMainHand();
        boolean empty = held.getType().isAir();
        if (!empty) {
            if (eatIn < 0) {
                eatIn = tamed ? 50 : 200;
            }
            eatIn -= ticks;
            if (eatIn <= 0 && stolenKind(held)) {
                AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
                entity.setHealth(Math.min(max == null ? entity.getHealth() : max.getValue(), entity.getHealth() + 5.0));
                entity.getWorld().spawnParticle(Particle.ITEM, entity.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.05, held);
                entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                equipment.setItemInMainHand(null);
                eatIn = -1;
            }
            return;
        }
        eatIn = -1;
        if (grabbing != null) {
            takeItem(entity, equipment, tamed, ticks);
            return;
        }
        itemClock += ticks;
        Item item = wanted == null ? null : item(entity, wanted);
        if (wanted != null && item == null) {
            wanted = null;
        }
        if (item == null) {
            if (itemClock < ITEM_SCAN || target != null || !host.free()) {
                return;
            }
            itemClock = 0;
            item = nearestItem(entity);
            if (item == null) {
                return;
            }
            wanted = item.getUniqueId();
        }
        if (entity.getLocation().distanceSquared(item.getLocation()) <= 4.0) {
            if (host.free() && host.act(entity, "grab")) {
                grabbing = item.getUniqueId();
                grabAge = 0;
                mob.getPathfinder().stopPathfinding();
            }
        } else if (itemClock % 10 < ticks) {
            mob.getPathfinder().moveTo(item.getLocation(), 1.1);
        }
    }

    private void takeItem(LivingEntity entity, org.bukkit.inventory.EntityEquipment equipment, boolean tamed, int ticks) {
        Item item = item(entity, grabbing);
        int previous = grabAge;
        grabAge += ticks;
        if (item == null || entity.getLocation().distanceSquared(item.getLocation()) > 9.0) {
            grabbing = null;
            wanted = null;
            return;
        }
        if (previous <= DinosaurHabits.GRAB_TAKES_ITEM && grabAge > DinosaurHabits.GRAB_TAKES_ITEM) {
            ItemStack stack = item.getItemStack();
            if (new EntityPickupItemEvent(entity, item, Math.max(0, stack.getAmount() - 1)).callEvent()) {
                ItemStack one = stack.clone();
                one.setAmount(1);
                equipment.setItemInMainHand(one);
                eatIn = tamed ? 50 : 200;
                if (stack.getAmount() <= 1) {
                    item.remove();
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                    item.setItemStack(stack);
                }
            }
            grabbing = null;
            wanted = null;
        }
    }

    private Item item(LivingEntity entity, UUID id) {
        Entity found = entity.getServer().getEntity(id);
        return found instanceof Item item && item.isValid() && item.getWorld().equals(entity.getWorld())
            && stolenKind(item.getItemStack()) ? item : null;
    }

    private Item nearestItem(LivingEntity entity) {
        Item best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity near : entity.getNearbyEntities(ITEM_RADIUS, 4.0, ITEM_RADIUS)) {
            if (near instanceof Item item && !item.getItemStack().getType().isAir() && stolenKind(item.getItemStack())) {
                double distance = near.getLocation().distanceSquared(entity.getLocation());
                if (distance < bestDistance) {
                    best = item;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private boolean stolenKind(ItemStack stack) {
        return DinosaurHabits.steals(stack.getType(), host.moduleItem(stack));
    }

    // --- Relicheirus and grottoceratops ---

    private void grounded(LivingEntity entity, Mob mob, LivingEntity target, int ticks) {
        if (blocks == null) {
            return;
        }
        if (mode == Mode.NONE) {
            cooldown = Math.max(0, cooldown - ticks);
            if (cooldown == 0 && target == null && host.free() && entity.isOnGround()) {
                begin(entity);
            }
            return;
        }
        if (target != null || !host.free() && !acting || !entity.isOnGround() && !acting) {
            stop(entity);
            return;
        }
        age += ticks;
        if (age > WALK_LIMIT || !stillValid(entity)) {
            stop(entity);
            return;
        }
        if (!acting) {
            approach(entity, mob, ticks);
            return;
        }
        int previous = actAge;
        actAge += ticks;
        mob.getPathfinder().stopPathfinding();
        face(entity, ticks);
        act(entity, previous);
        if (done) {
            stop(entity);
        }
    }

    /** One try at starting a goal: the mod's {@code MoveToBlockGoal} interval, then the nearest valid block. */
    private void begin(LivingEntity entity) {
        World world = entity.getWorld();
        Location at = entity.getLocation();
        int cx = at.getBlockX();
        int cy = at.getBlockY();
        int cz = at.getBlockZ();
        boolean baby = host.baby(entity);
        if (kind == Kind.GROTTOCERATOPS) {
            cooldown = 200 + random.nextInt(200);
            if (!griefing(entity)) {
                return;
            }
            Block plant = nearest(world, cx, cy, cz, 12, 6, block -> blocks.grottoFood(block));
            if (plant != null) {
                spot = new int[] {plant.getX(), plant.getY(), plant.getZ()};
                stand = new int[] {plant.getX(), plant.getZ()};
                start(Mode.GRAZE);
            }
            return;
        }
        if (host.pushing() > 0 && !baby) {
            cooldown = 10 + random.nextInt(20);
            if (!griefing(entity)) {
                return;
            }
            Block log = nearest(world, cx, cy, cz, 16, 6, this::treeTop);
            if (log != null) {
                spot = new int[] {log.getX(), log.getY(), log.getZ()};
                Block ground = groundOf(log);
                stand = DinosaurHabits.standAt(ground.getX(), ground.getZ(), at.getX(), at.getZ(),
                    ground.getRelative(BlockFace.DOWN).getType().isAir());
                start(Mode.PUSH);
            }
            return;
        }
        cooldown = 220 + random.nextInt(500);
        Block leaves = nearest(world, cx, cy, cz, 14, 6, block -> blocks.relicheirusNibbles(block) && nibbleHigh(block, baby));
        if (leaves != null) {
            spot = new int[] {leaves.getX(), leaves.getY(), leaves.getZ()};
            stand = DinosaurHabits.standAt(leaves.getX(), leaves.getZ(), at.getX(), at.getZ(),
                leaves.getRelative(BlockFace.DOWN).getType().isAir());
            start(Mode.NIBBLE);
        }
    }

    private void start(Mode next) {
        mode = next;
        age = 0;
        actAge = 0;
        acting = false;
        done = false;
        walkClock = 0;
    }

    /** Ends the task: the mob gets its AI back and the chosen block is forgotten. */
    private void stop(LivingEntity entity) {
        if (frozen && entity instanceof Mob mob) {
            frozen = false;
            if (!host.sitting(entity) && entity.getPassengers().isEmpty() && !host.dancing()) {
                mob.setAware(true);
            }
        }
        frozen = false;
        mode = Mode.NONE;
        spot = null;
        stand = null;
        acting = false;
        done = false;
    }

    private boolean stillValid(LivingEntity entity) {
        World world = entity.getWorld();
        if (!world.isChunkLoaded(spot[0] >> 4, spot[2] >> 4)) {
            return false;
        }
        Block block = world.getBlockAt(spot[0], spot[1], spot[2]);
        return switch (mode) {
            case GRAZE -> blocks.grottoFood(block) && (acting || griefing(entity));
            case PUSH -> host.pushing() > 0 && (acting || griefing(entity)) && treeTop(block);
            case NIBBLE -> blocks.relicheirusNibbles(block);
            default -> false;
        };
    }

    /** Walks toward the spot; once there, faces it and starts the action animation. */
    private void approach(LivingEntity entity, Mob mob, int ticks) {
        Location at = entity.getLocation();
        double goalX = mode == Mode.GRAZE ? spot[0] + 0.5 : stand[0] + 0.5;
        double goalZ = mode == Mode.GRAZE ? spot[2] + 0.5 : stand[1] + 0.5;
        double dx = goalX - at.getX();
        double dz = goalZ - at.getZ();
        double reach = mode == Mode.GRAZE ? Math.pow(entity.getWidth() + 1.0, 2) : 4.0;
        if (dx * dx + dz * dz < reach) {
            mob.getPathfinder().stopPathfinding();
            if (mode == Mode.GRAZE) {
                mob.lookAt(new Location(entity.getWorld(), spot[0] + 0.5, spot[1] + 0.5, spot[2] + 0.5));
                beginAct(entity, mob, "chew_from_ground");
            } else if (face(entity, ticks)) {
                beginAct(entity, mob, mode == Mode.NIBBLE ? "eat_tree" : "push_tree");
            }
            return;
        }
        walkClock += ticks;
        if (walkClock >= 10 || mob.getPathfinder().getCurrentPath() == null) {
            walkClock = 0;
            mob.getPathfinder().moveTo(new Location(entity.getWorld(), goalX, at.getY(), goalZ), 1.0);
        }
    }

    /** Mod {@code lockTreePosition}: turns to the tree's side, 20 degrees a tick; true when it looks straight at it. */
    private boolean face(LivingEntity entity, int ticks) {
        if (mode == Mode.GRAZE) {
            return true;
        }
        Location at = entity.getLocation();
        int[] side = DinosaurHabits.cardinal(spot[0] + 0.5 - at.getX(), spot[2] + 0.5 - at.getZ());
        float wanted = SauropodBody.yawTo(side[0], side[1]);
        float yaw = SauropodBody.approachDegrees(at.getYaw(), wanted, 20.0F * ticks);
        entity.setRotation(yaw, 0.0F);
        return Math.abs(SauropodBody.approachDegrees(yaw, wanted, 360.0F) - yaw) < 7.0F;
    }

    private void beginAct(LivingEntity entity, Mob mob, String animation) {
        if (!host.act(entity, animation)) {
            stop(entity);
            return;
        }
        acting = true;
        actAge = 0;
        frozen = true;
        mob.setAware(false);
        act(entity, -1);
    }

    /** The goal's work on the animation's ticks. */
    private void act(LivingEntity entity, int previous) {
        World world = entity.getWorld();
        Block block = world.getBlockAt(spot[0], spot[1], spot[2]);
        switch (mode) {
            case NIBBLE -> {
                if (actAge >= DinosaurHabits.NIBBLE_END) {
                    done = true;
                } else if (DinosaurHabits.passes(previous, actAge, DinosaurHabits.NIBBLE_PERIOD)) {
                    // Mod: destroyBlock without drops and the same block put back: only the break effect shows.
                    crumbs(block, 24);
                }
            }
            case PUSH -> {
                if (previous < DinosaurHabits.PUSH_TOPPLES && actAge >= DinosaurHabits.PUSH_TOPPLES) {
                    topple(entity, block);
                    done = true;
                }
            }
            case GRAZE -> {
                if (previous <= DinosaurHabits.GRAZE_EATS && actAge > DinosaurHabits.GRAZE_EATS) {
                    graze(entity, block);
                    done = true;
                } else if (actAge > 5) {
                    crumbs(block, 2);
                }
            }
            default -> {
            }
        }
    }

    private void crumbs(Block block, int count) {
        BlockData data = block.getBlockData();
        Location spotAt = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.BLOCK, spotAt, count, 0.4, 0.4, 0.4, 0.0, data);
        if (count > 4) {
            block.getWorld().playSound(spotAt, data.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 0.8F);
        }
    }

    /** Mod {@code GrottoceratopsEatPlantsGoal}: the plant is destroyed; a curly fern leaves a fiddlehead. */
    private void graze(LivingEntity entity, Block plant) {
        if (!blocks.grottoFood(plant) || !griefing(entity)) {
            return;
        }
        boolean fern = blocks.curlyFern(plant);
        BlockData next = Material.AIR.createBlockData();
        if (fern) {
            next = blocks.fiddlehead() != null && plant.getType() != Material.LARGE_FERN ? blocks.fiddlehead()
                : Material.FERN.createBlockData();
        }
        if (!new EntityChangeBlockEvent(entity, plant, next).callEvent()) {
            return;
        }
        crumbs(plant, 12);
        Block upper = plant.getRelative(BlockFace.UP);
        boolean twoHigh = fern && blocks.curlyFernUpper(upper);
        plant.setBlockData(next, false);
        if (twoHigh) {
            upper.setBlockData(Material.AIR.createBlockData(), false);
        }
    }

    // --- trees ---

    /** Mod {@code RelicheirusPushTreesGoal.isValidTarget}: a log with leaves right over it (the top of a tree). */
    private boolean treeTop(Block block) {
        if (!blocks.knockableLog(block)) {
            return false;
        }
        return naturalLeaves(block.getRelative(BlockFace.UP));
    }

    /** Mod {@code getBottomOfTree}: down through logs, leaves and air to the ground. */
    private Block groundOf(Block top) {
        Block at = top;
        for (int i = 0; i < 64 && at.getY() > at.getWorld().getMinHeight(); i++) {
            if (!(at.getType().isAir() || blocks.knockableLeaves(at) || blocks.knockableLog(at))) {
                break;
            }
            at = at.getRelative(BlockFace.DOWN);
        }
        return at;
    }

    /** Mod {@code nibble}: the height of the leaves counted straight down through leaves, air and logs. */
    private boolean nibbleHigh(Block block, boolean baby) {
        int height = 0;
        Block at = block;
        int floor = block.getWorld().getMinHeight();
        while (at.getY() > floor && height < 8
            && (at.getType().isAir() || blocks.relicheirusNibbles(at) || blocks.knockableLog(at))) {
            height++;
            at = at.getRelative(BlockFace.DOWN);
        }
        return DinosaurHabits.nibbleHigh(height, baby);
    }

    /**
     * Mod {@code gatherAttachedBlocks} and the falling tree: the tree's logs and leaves (up to 300, within 12 blocks
     * of the first log) are taken off and tumble away from the relicheirus as falling blocks. Nothing happens if a
     * protection plugin vetoes any of the blocks.
     */
    private void topple(LivingEntity entity, Block origin) {
        if (!griefing(entity) || !treeTop(origin)) {
            return;
        }
        List<Block> tree = gather(origin);
        if (tree.isEmpty() || built(tree)) {
            return;
        }
        BlockData air = Material.AIR.createBlockData();
        for (Block part : tree) {
            if (!new EntityChangeBlockEvent(entity, part, air).callEvent()) {
                return;
            }
        }
        Location from = entity.getLocation();
        int[] side = DinosaurHabits.cardinal(origin.getX() + 0.5 - from.getX(), origin.getZ() + 0.5 - from.getZ());
        World world = origin.getWorld();
        world.playSound(from, "alexscaves:relicheirus_topple", SoundCategory.NEUTRAL, 1.0F, 1.0F);
        int baseY = tree.stream().mapToInt(Block::getY).min().orElse(origin.getY());
        for (Block part : tree) {
            BlockData data = part.getBlockData();
            Location center = part.getLocation().add(0.5, 0.0, 0.5);
            part.setBlockData(air, false);
            FallingBlock falling = world.spawnFallingBlock(center, data);
            falling.setHurtEntities(false);
            falling.setDropItem(Tag.LOGS.isTagged(data.getMaterial()));
            double lean = Math.min(0.5, 0.03 + 0.02 * (part.getY() - baseY));
            falling.setVelocity(new Vector(side[0] * lean + (random.nextDouble() - 0.5) * 0.04, 0.05,
                side[1] * lean + (random.nextDouble() - 0.5) * 0.04));
        }
    }

    private List<Block> gather(Block origin) {
        List<Block> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(key(origin));
        World world = origin.getWorld();
        while (!queue.isEmpty() && found.size() < DinosaurHabits.TREE_MAX_BLOCKS) {
            Block at = queue.poll();
            found.add(at);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = at.getX() + dx;
                        int y = at.getY() + dy;
                        int z = at.getZ() + dz;
                        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
                            continue;
                        }
                        Block next = world.getBlockAt(x, y, z);
                        if (DinosaurHabits.attached(x - origin.getX(), y - origin.getY(), z - origin.getZ())
                            && seen.add(key(next)) && treePart(next)) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return found;
    }

    private boolean treePart(Block block) {
        Material type = block.getType();
        return !type.isAir() && !PrimordialBlocks.unmovable(type) && !block.isLiquid()
            && (blocks.knockableLog(block) || naturalLeaves(block));
    }

    /** Leaves a player placed are persistent: only the ones that grew with the tree count. */
    private boolean naturalLeaves(Block block) {
        return blocks.knockableLeaves(block)
            && !(block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves && leaves.isPersistent());
    }

    /** A log house or fence post is not a tree: stripped wood or built blocks touching a log rule the whole tree out. */
    private boolean built(List<Block> tree) {
        for (Block part : tree) {
            if (!blocks.knockableLog(part)) {
                continue;
            }
            if (part.getType().name().startsWith("STRIPPED_")) {
                return true;
            }
            for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
                if (DinosaurHabits.builtMaterial(part.getRelative(face).getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long key(Block block) {
        return ((long) block.getX() & 0x3FFFFFFL) << 38 | ((long) block.getZ() & 0x3FFFFFFL) << 12 | ((long) block.getY() & 0xFFFL);
    }

    // --- shared ---

    private static boolean griefing(LivingEntity entity) {
        return !Boolean.FALSE.equals(entity.getWorld().getGameRuleValue(GameRules.MOB_GRIEFING));
    }

    /**
     * The nearest block that fits within {@code radius} across and {@code vertical} up or down, looked for ring by ring
     * so that a close hit ends the search early.
     */
    private static Block nearest(World world, int cx, int cy, int cz, int radius, int vertical, Predicate<Block> fits) {
        for (int ring = 0; ring <= radius; ring++) {
            Block best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !world.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) {
                        continue;
                    }
                    for (int dy = -vertical; dy <= vertical; dy++) {
                        int y = cy + dy;
                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                            continue;
                        }
                        double distance = dx * (double) dx + dy * (double) dy + dz * (double) dz;
                        if (distance >= bestDistance) {
                            continue;
                        }
                        Block block = world.getBlockAt(cx + dx, y, cz + dz);
                        if (fits.test(block)) {
                            best = block;
                            bestDistance = distance;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }
}
