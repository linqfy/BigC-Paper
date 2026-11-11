package dev.linqfy.bigCasares.modules.villagermood;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/** Server effects for temporary villager mafia crews. */
final class VillagerMafiaRuntime {

    private final VillagerMafiaSettings settings;
    private final VillagerMafiaService service;
    private final NamespacedKey targetKey;
    private final NamespacedKey confinedKey;
    private final NamespacedKey customEntityKey;
    private final Predicate<Villager> ordinary;

    VillagerMafiaRuntime(VillagerMafiaSettings settings, NamespacedKey confinedKey,
                        Predicate<Villager> ordinary, DoubleSupplier random) {
        this.settings = settings;
        this.service = new VillagerMafiaService(settings, random);
        this.confinedKey = confinedKey;
        this.targetKey = new NamespacedKey(confinedKey.getNamespace(), "villager_mafia_target");
        this.customEntityKey = new NamespacedKey(confinedKey.getNamespace(), "custom_entity");
        this.ordinary = ordinary;
    }

    void consider(Player player, long now) {
        if (!settings.enabled() || !canTarget(player)) {
            return;
        }
        double radius = settings.radius();
        Location center = player.getLocation();
        BlockProbe probe = new BukkitBlockProbe(player.getWorld());
        List<VillagerMafiaService.Source> sources = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Villager villager && ordinary.test(villager)
                && villager.getLocation().distanceSquared(center) <= radius * radius) {
                Location at = villager.getLocation();
                boolean confined = villager.isInsideVehicle()
                    || VillagerMoodPolicy.isConfined(probe, at.getBlockX(), at.getBlockY(), at.getBlockZ());
                Long ticks = villager.getPersistentDataContainer().get(confinedKey, PersistentDataType.LONG);
                sources.add(new VillagerMafiaService.Source(villager.getUniqueId(), confined, ticks == null ? 0L : ticks));
            }
        }
        service.consider(player.getUniqueId(), sources, now, (id, size, driveBy) -> spawn(player, size, driveBy));
    }

    private Crew spawn(Player player, int size, boolean driveBy) {
        Crew crew = new Crew(player);
        World world = player.getWorld();
        Location center = player.getLocation();
        Ground ground = new Ground(world);
        try {
            for (int slot = 0; slot < size; slot++) {
                var cell = VillagerMafiaSpawn.find(ground, center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                    driveBy, slot);
                boolean mounted = driveBy && cell.isPresent();
                if (cell.isEmpty() && driveBy) {
                    cell = VillagerMafiaSpawn.find(ground, center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                        false, slot);
                }
                if (cell.isEmpty()) {
                    continue;
                }
                var at = cell.orElseThrow();
                Location spawn = new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5);
                if (!world.getNearbyEntities(spawn, 1.0, 2.0, 1.0).isEmpty()) {
                    continue;
                }
                WanderingTrader mob = world.spawn(spawn, WanderingTrader.class, created -> {
                    mark(created, player);
                    created.setDespawnDelay(0);
                    created.setRecipes(List.of());
                    created.setCanPickupItems(false);
                    created.setCustomName(ChatColor.DARK_RED + (driveBy ? "Mafia aldeana · Ballestero" : "Mafia aldeana · Matón"));
                    created.setCustomNameVisible(true);
                    setAttribute(created, Attribute.MAX_HEALTH, settings.health());
                    created.setHealth(settings.health());
                    setAttribute(created, Attribute.MOVEMENT_SPEED, 0.25);
                    created.getEquipment().setItemInMainHand(new ItemStack(driveBy ? Material.CROSSBOW : Material.STONE_SWORD));
                    created.getEquipment().setItemInMainHandDropChance(0.0f);
                });
                crew.entities.add(mob);
                if (!mob.isValid()) {
                    continue;
                }
                // Traders have goal-based AI; normal villagers' Brain would override chase paths with work/panic.
                Bukkit.getMobGoals().removeAllGoals(mob);
                Horse horse = null;
                if (mounted) {
                    horse = world.spawn(spawn, Horse.class, created -> {
                        mark(created, player);
                        created.setAdult();
                        created.setAgeLock(true);
                        created.setBreed(false);
                        created.setTamed(true);
                        created.setColor(Horse.Color.BLACK);
                        setAttribute(created, Attribute.MAX_HEALTH, settings.health());
                        created.setHealth(settings.health());
                        setAttribute(created, Attribute.MOVEMENT_SPEED, 0.3);
                    });
                    crew.entities.add(horse);
                    if (!horse.isValid() || !horse.addPassenger(mob)) {
                        horse.remove();
                        horse = null;
                    } else {
                        Bukkit.getMobGoals().removeAllGoals(horse);
                    }
                }
                crew.members.add(new Member(mob, horse, driveBy));
            }
            if (crew.members.isEmpty()) {
                crew.remove();
                return null;
            }
            player.sendTitle(ChatColor.DARK_RED + "¡LA MAFIA ALDEANA!",
                ChatColor.GOLD + (driveBy ? "¡Se viene el drive-by!" : "Vinieron por tus aldeanos encerrados"), 5, 60, 15);
            player.sendMessage(ChatColor.DARK_RED + "[Mafia] " + ChatColor.WHITE
                + "¡Liberá a los aldeanos o arreglamos esto a los golpes!");
            world.playSound(center, Sound.EVENT_RAID_HORN, 1.0f, 0.8f);
            return crew;
        } catch (RuntimeException failure) {
            crew.remove();
            throw failure;
        }
    }

    void tick(long now) {
        service.tick(now);
    }

    void forget(UUID player) {
        service.forget(player);
    }

    void shutdown() {
        service.shutdown();
    }

    boolean owns(Entity entity) {
        return entity.getPersistentDataContainer().has(targetKey, PersistentDataType.STRING);
    }

    boolean canDamage(Entity attacker, Entity victim) {
        return victim instanceof Player player && canTarget(player)
            && player.getUniqueId().toString().equals(attacker.getPersistentDataContainer().get(targetKey, PersistentDataType.STRING));
    }

    void onDamage(EntityDamageByEntityEvent event) {
        if (!owns(event.getDamager())) {
            return;
        }
        if (!canDamage(event.getDamager(), event.getEntity())) {
            event.setCancelled(true);
        } else if (event.getDamager() instanceof AbstractArrow) {
            // Arrow base damage is multiplied by velocity; keep the configured hit strength predictable.
            event.setDamage(settings.damage() / 2.0);
        }
    }

    void removeOrphan(Entity entity) {
        if (owns(entity)) {
            entity.remove();
        }
    }

    static boolean canTarget(Player player) {
        return player.isOnline() && player.isValid() && !player.isDead()
            && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
            && player.getWorld().getDifficulty() != Difficulty.PEACEFUL;
    }

    static boolean withinChaseRange(Location from, Location target) {
        return from.getWorld() == target.getWorld() && from.distanceSquared(target) <= 64.0 * 64.0;
    }

    private void mark(Mob entity, Player target) {
        entity.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
        // Keep the global mob-scaling module from turning these into unrelated mutations/bosses.
        entity.getPersistentDataContainer().set(customEntityKey, PersistentDataType.STRING, "villager_mafia");
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
    }

    private static void setAttribute(Mob mob, Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private final class Crew implements VillagerMafiaService.Crew {
        private final Player target;
        private final World world;
        private final List<Member> members = new ArrayList<>();
        private final List<Entity> entities = new ArrayList<>();
        private final List<Shot> shots = new ArrayList<>();

        private Crew(Player target) {
            this.target = target;
            this.world = target.getWorld();
        }

        @Override
        public boolean tick(long now) {
            if (!canTarget(target) || target.getWorld() != world) {
                return false;
            }
            Iterator<Shot> iterator = shots.iterator();
            while (iterator.hasNext()) {
                Shot shot = iterator.next();
                if (!shot.arrow.isValid() || shot.arrow.isInBlock() || now >= shot.expires) {
                    shot.arrow.remove();
                    iterator.remove();
                }
            }
            boolean alive = false;
            for (Member member : members) {
                if (!member.mob.isValid() || member.mob.isDead()) {
                    if (member.horse != null) {
                        member.horse.remove();
                    }
                    continue;
                }
                if (!withinChaseRange(member.mob.getLocation(), target.getLocation())) {
                    member.mob.remove();
                    if (member.horse != null) {
                        member.horse.remove();
                    }
                    continue;
                }
                double distance = member.mob.getLocation().distanceSquared(target.getLocation());
                alive = true;
                move(member, now);
                if (member.ranged) {
                    if (distance <= 24.0 * 24.0 && now >= member.nextAttack && member.mob.hasLineOfSight(target)) {
                        member.nextAttack = now + settings.shotCooldownTicks();
                        shoot(member.mob, now);
                    }
                } else if (distance <= 2.2 * 2.2 && now >= member.nextAttack && member.mob.hasLineOfSight(target)) {
                    member.nextAttack = now + 20L;
                    target.damage(settings.damage(), member.mob);
                    member.mob.swingMainHand();
                }
            }
            return alive;
        }

        private void move(Member member, long now) {
            if (member.horse == null || !member.horse.isValid() || member.mob.getVehicle() != member.horse) {
                member.mob.getPathfinder().moveTo(target, 1.15);
                return;
            }
            Horse horse = member.horse;
            if (member.passEnd == null || now >= member.nextPass
                || horse.getLocation().distanceSquared(member.passEnd) < 9.0) {
                Vector forward = target.getLocation().toVector().subtract(horse.getLocation().toVector()).setY(0);
                if (forward.lengthSquared() < 0.01) {
                    forward = new Vector(1, 0, 0);
                }
                forward.normalize();
                Vector side = new Vector(-forward.getZ(), 0, forward.getX()).multiply(6.0);
                member.passEnd = target.getLocation().add(forward.multiply(12.0)).add(side);
                member.nextPass = now + 160L;
            }
            // Real navigation and collision: the riders sweep past the target while firing.
            if (!horse.getPathfinder().moveTo(member.passEnd, 1.4)) {
                horse.removePassenger(member.mob);
                horse.remove();
                member.mob.getPathfinder().moveTo(target, 1.15);
            }
        }

        private void shoot(WanderingTrader mob, long now) {
            Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
            if (direction.lengthSquared() < 0.01) {
                return;
            }
            direction.normalize();
            var arrow = world.spawnArrow(mob.getEyeLocation(), direction, 1.8f, 6.0f);
            shots.add(new Shot(arrow, now + 100L));
            arrow.setShooter(mob);
            arrow.setDamage(settings.damage() / 2.0);
            arrow.setWeapon(new ItemStack(Material.CROSSBOW));
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setPersistent(false);
            arrow.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
            world.playSound(mob.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 0.9f);
        }

        @Override
        public void remove() {
            shots.forEach(shot -> shot.arrow.remove());
            shots.clear();
            entities.forEach(Entity::remove);
            entities.clear();
        }
    }

    private static final class Member {
        private final WanderingTrader mob;
        private final Horse horse;
        private final boolean ranged;
        private long nextAttack;
        private long nextPass;
        private Location passEnd;

        private Member(WanderingTrader mob, Horse horse, boolean ranged) {
            this.mob = mob;
            this.horse = horse;
            this.ranged = ranged;
        }
    }

    private record Shot(AbstractArrow arrow, long expires) {
    }

    private record Ground(World world) implements VillagerMafiaSpawn.Terrain {
        @Override
        public boolean clear(int x, int y, int z) {
            Block block = loaded(x, y, z);
            return block != null && block.isPassable() && !block.isLiquid() && !hazard(block.getType());
        }

        @Override
        public boolean supported(int x, int y, int z) {
            Block block = loaded(x, y, z);
            return block != null && block.getType().isSolid() && !block.isPassable()
                && !hazard(block.getType()) && Math.abs(block.getBoundingBox().getMaxY() - (y + 1.0)) < 0.01;
        }

        private Block loaded(int x, int y, int z) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
                return null;
            }
            return world.getBlockAt(x, y, z);
        }

        private static boolean hazard(Material material) {
            return switch (material) {
                case LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK, CACTUS,
                     SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE, POINTED_DRIPSTONE -> true;
                default -> false;
            };
        }
    }
}
