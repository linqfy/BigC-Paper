package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Mobs of the Magnetic Caves with the stats and drops of the mod. */
public final class MagneticMobs {
    private static final CaveBiomeId BIOME = CaveBiomeId.MAGNETIC_CAVES;

    public static final ModMobType TELETOR = new ModMobType("teletor", BIOME, "blaze", SpawnCategory.MONSTER,
        Map.of("max_health", 18.0, "attack_damage", 2.0, "flying_speed", 1.0, "movement_speed", 0.2, "follow_range", 32.0),
        "alexscaves_teletor", List.of(MobDrop.of("ac_raw_azure_neodymium", 1, 2), MobDrop.of("ac_raw_scarlet_neodymium", 1, 2),
            MobDrop.of("ac_telecore", 0, 1)));
    public static final ModMobType MAGNETRON = new ModMobType("magnetron", BIOME, "zombie", SpawnCategory.MONSTER,
        Map.of("max_health", 30.0, "attack_damage", 2.0, "armor", 6.0, "movement_speed", 0.2, "follow_range", 32.0),
        "alexscaves_magnetron", List.of(MobDrop.of("ac_heart_of_iron", 1, 1)));
    public static final ModMobType BOUNDROID = new ModMobType("boundroid", BIOME, "spider", SpawnCategory.MONSTER,
        Map.of("max_health", 20.0, "attack_damage", 5.0, "armor", 20.0, "movement_speed", 0.2, "follow_range", 32.0),
        "alexscaves_boundroid", List.of(MobDrop.of("ac_heavyweight", 0, 1), MobDrop.of("iron_chain", 2, 3)));
    public static final ModMobType FERROUSLIME = new ModMobType("ferrouslime", BIOME, "slime", SpawnCategory.MONSTER,
        Map.of("max_health", 10.0, "attack_damage", 2.0, "movement_speed", 0.35),
        "alexscaves_ferrouslime", List.of(MobDrop.of("ac_ferrouslime_ball", 0, 1)));
    public static final ModMobType NOTOR = new ModMobType("notor", BIOME, "bat", SpawnCategory.AMBIENT,
        Map.of("max_health", 6.0, "movement_speed", 0.15),
        "alexscaves_notor", List.of(MobDrop.of("ac_notor_gizmo", 0, 1)));

    public static final List<ModMobType> ALL = List.of(TELETOR, MAGNETRON, BOUNDROID, FERROUSLIME, NOTOR);

    private MagneticMobs() {
    }

    public static void register(ModMobCatalog catalog) {
        catalog.register(TELETOR, (entity, type) -> new Teletor());
        catalog.register(MAGNETRON, (entity, type) -> new Magnetron());
        catalog.register(BOUNDROID, (entity, type) -> new Boundroid());
        catalog.register(FERROUSLIME, (entity, type) -> new Ferrouslime(entity));
        catalog.register(NOTOR, (entity, type) -> new Notor());
    }

    private static LivingEntity target(LivingEntity entity) {
        return entity instanceof Mob mob ? mob.getTarget() : null;
    }

    /** Hovers and swings its floating iron tool at range (the blaze base's fireballs are cancelled). */
    static final class Teletor implements ModMobBehaviour {
        static final double RANGE = 10.0;
        private final MobAnimator animation = new MobAnimator();
        private int cooldown;

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            cooldown -= ticks;
            LivingEntity target = target(entity);
            animation.update(entity, context, ticks, controlling(entity, target) ? "control" : null);
            if (target == null || cooldown > 0 || !target.isValid()
                || target.getLocation().distanceSquared(entity.getLocation()) > RANGE * RANGE || !entity.hasLineOfSight(target)) {
                return;
            }
            cooldown = 20;
            Location from = entity.getEyeLocation();
            Vector step = target.getEyeLocation().toVector().subtract(from.toVector());
            double length = step.length();
            step.normalize().multiply(0.5);
            Location point = from.clone();
            for (double travelled = 0; travelled < length; travelled += 0.5) {
                point.add(step);
                entity.getWorld().spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
            }
            target.damage(entity.getAttribute(Attribute.ATTACK_DAMAGE) == null ? 2.0
                : entity.getAttribute(Attribute.ATTACK_DAMAGE).getValue() + 2.0, entity);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.4f);
            animation.action(entity, context, "attack");
        }

        /** Mod {@code controlProgress}: the arms-up pose while it controls its floating tool, i.e. has a live target in reach. */
        static boolean controlling(LivingEntity entity, LivingEntity target) {
            return target != null && target.isValid() && target.getWorld() == entity.getWorld()
                && target.getLocation().distanceSquared(entity.getLocation()) <= RANGE * RANGE;
        }
    }

    /** Rolls around until it sees a target, then builds a body of orbiting metal blocks (HP 30 -> 80). */
    static final class Magnetron implements ModMobBehaviour {
        private static final double FORM_RANGE = 16.0;
        private final List<BlockDisplay> body = new ArrayList<>();
        private final MobAnimator animation = new MobAnimator();
        private float angle;

        /** Mod: formed it shows {@code form}; still rolling on its wheel it leans ({@code roll_lean}) while moving. */
        static String state(boolean formed, boolean moving) {
            return formed ? "form" : moving ? "roll_lean" : null;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            LivingEntity target = target(entity);
            boolean moving = animation.moving();
            if (body.isEmpty() && target != null && target.getLocation().distanceSquared(entity.getLocation()) < FORM_RANGE * FORM_RANGE) {
                form(entity);
            }
            animation.update(entity, context, ticks, state(!body.isEmpty(), moving));
            if (body.isEmpty()) {
                return;
            }
            angle += 0.15f * ticks;
            Location center = entity.getLocation().add(0, 1.2, 0);
            for (int i = 0; i < body.size(); i++) {
                BlockDisplay display = body.get(i);
                double a = angle + i * (Math.PI * 2 / body.size());
                Location at = center.clone().add(Math.cos(a) * 1.1, (i % 3 - 1) * 0.6, Math.sin(a) * 1.1);
                display.teleport(at);
            }
            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                entity.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 6, 0.6, 0.6, 0.6, 0.05);
            }
        }

        private void form(LivingEntity entity) {
            AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(80.0);
                entity.setHealth(Math.min(80.0, entity.getHealth() + 50.0));
            }
            BlockData[] parts = {Material.IRON_BLOCK.createBlockData(), Material.POLISHED_DEEPSLATE.createBlockData(),
                Material.IRON_CHAIN.createBlockData()};
            for (int i = 0; i < 6; i++) {
                BlockData data = parts[i % parts.length];
                body.add(entity.getWorld().spawn(entity.getLocation(), BlockDisplay.class, display -> {
                    display.setBlock(data);
                    display.setPersistent(false);
                    display.setTransformation(new Transformation(new Vector3f(-0.3f, -0.3f, -0.3f),
                        new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
                    display.setTeleportDuration(2);
                }));
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            body.forEach(Entity::remove);
            body.clear();
        }
    }

    /** Climbs walls like its spider base and slams down on targets below it. */
    static final class Boundroid implements ModMobBehaviour {
        private final MobAnimator animation = new MobAnimator();
        private int cooldown;
        private boolean slamming;

        /** Mod {@code groundProgress}: resting on the ground it stops swaying its head; moving it walks. */
        static String state(boolean onGround, boolean moving) {
            return onGround && !moving ? "ground" : null;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            cooldown -= ticks;
            LivingEntity target = target(entity);
            boolean moving = animation.moving();
            animation.update(entity, context, ticks, state(entity.isOnGround(), moving));
            if (slamming && entity.isOnGround()) {
                slamming = false;
                for (Entity nearby : entity.getNearbyEntities(1.5, 1.0, 1.5)) {
                    if (nearby instanceof LivingEntity living && !(nearby.equals(entity))) {
                        living.damage(5.0, entity);
                    }
                }
                entity.getWorld().spawnParticle(Particle.BLOCK, entity.getLocation(), 20, 0.6, 0.1, 0.6,
                    Material.POLISHED_DEEPSLATE.createBlockData());
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.7f);
                animation.action(entity, context, "attack");
                return;
            }
            if (target == null || cooldown > 0) {
                return;
            }
            Location here = entity.getLocation();
            Location there = target.getLocation();
            double horizontal = Math.hypot(here.getX() - there.getX(), here.getZ() - there.getZ());
            if (here.getY() - there.getY() > 2.0 && horizontal < 2.0) {
                entity.setVelocity(new Vector(0, -1.2, 0));
                slamming = true;
                cooldown = 60;
            }
        }
    }

    /** Slime made of iron filings: HP 10 and damage 2 per head; merges with others up to 10 heads. */
    static final class Ferrouslime implements ModMobBehaviour {
        static final int MAX_HEADS = 10;
        private final MobAnimator animation = new MobAnimator();

        Ferrouslime(LivingEntity entity) {
            if (entity instanceof Slime slime) {
                heads(slime, Math.max(1, Math.min(slime.getSize(), 2)));
            }
        }

        static void heads(Slime slime, int heads) {
            int clamped = Math.max(1, Math.min(MAX_HEADS, heads));
            slime.setSize(clamped);
            // setSize resets movement speed by size; the mod keeps a flat 0.35.
            AttributeInstance speed = slime.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.35);
            }
            AttributeInstance health = slime.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(stat(clamped, 10, 10, 100));
                slime.setHealth(health.getValue());
            }
            AttributeInstance damage = slime.getAttribute(Attribute.ATTACK_DAMAGE);
            if (damage != null) {
                damage.setBaseValue(stat(clamped, 2, 2, 10));
            }
        }

        /** Mod formula: {@code clamp(heads * perHead, min, max)}. */
        static double stat(int heads, double perHead, double min, double max) {
            return Math.max(min, Math.min(max, heads * perHead));
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            animation.update(entity, context, ticks, null);
            if (!(entity instanceof Slime slime) || slime.getSize() >= MAX_HEADS || ThreadLocalRandom.current().nextInt(10) != 0) {
                return;
            }
            for (Entity nearby : entity.getNearbyEntities(0.8, 0.8, 0.8)) {
                if (nearby instanceof Slime other && other.isValid() && !other.equals(slime)
                    && context.mobs().typeOf(other).filter(type -> type == FERROUSLIME).isPresent()
                    && other.getSize() <= slime.getSize()) {
                    heads(slime, slime.getSize() + other.getSize());
                    other.remove();
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.6f);
                    return;
                }
            }
        }
    }

    /** Harmless scanner: marks nearby players and sends the cave's monsters after them. */
    static final class Notor implements ModMobBehaviour {
        private static final double SCAN_RANGE = 12.0;
        private static final double ALERT_RANGE = 24.0;
        private final MobAnimator animation = new MobAnimator();
        private int cooldown = 40;

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            animation.update(entity, context, ticks, entity.isOnGround() ? "ground" : null);
            cooldown -= ticks;
            if (cooldown > 0) {
                return;
            }
            cooldown = 60;
            for (Entity nearby : entity.getNearbyEntities(SCAN_RANGE, SCAN_RANGE, SCAN_RANGE)) {
                if (nearby instanceof Player player && player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR && entity.hasLineOfSight(player)) {
                    scan(entity, player, context);
                    return;
                }
            }
        }

        private void scan(LivingEntity notor, Player player, ModMobContext context) {
            notor.getWorld().spawnParticle(Particle.GLOW, player.getLocation().add(0, 1, 0), 15, 0.4, 0.8, 0.4, 0);
            notor.getWorld().playSound(notor.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f, 2.0f);
            for (Entity nearby : notor.getNearbyEntities(ALERT_RANGE, ALERT_RANGE, ALERT_RANGE)) {
                if (nearby instanceof Mob mob && mob.getTarget() == null
                    && context.mobs().typeOf(mob).filter(type -> type.category() == SpawnCategory.MONSTER).isPresent()) {
                    mob.setTarget(player);
                }
            }
        }
    }
}
