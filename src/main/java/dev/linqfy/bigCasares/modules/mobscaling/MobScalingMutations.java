package dev.linqfy.bigCasares.modules.mobscaling;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MobScalingMutations implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey mutationKey;
    private final NamespacedKey stolenItemKey;

    // Track active special entities to avoid looping all entities on the server
    private final Map<LivingEntity, String> activeMutants = new ConcurrentHashMap<>();

    // Track countdowns for explosive sheep (ticks remaining)
    private final Map<LivingEntity, Integer> sheepCountdown = new ConcurrentHashMap<>();

    // Track infested players and their remaining waves
    private final Map<Player, Integer> infestedPlayers = new ConcurrentHashMap<>();

    // Track cooldowns for piggyback spiders (Entity to timestamp)
    private final Map<Entity, Long> spiderRideCooldown = new ConcurrentHashMap<>();

    // Track cooldowns for Wither Skeleton death swaps (Entity to timestamp)
    private final Map<Entity, Long> deathSwapCooldown = new ConcurrentHashMap<>();

    // Track drowned flood timers (Entity to loop count)
    private final Map<Entity, Integer> drownedFloodTimer = new ConcurrentHashMap<>();

    public MobScalingMutations(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mutationKey = new NamespacedKey(plugin, "cursed_mutation");
        this.stolenItemKey = new NamespacedKey(plugin, "stolen_item");

        startTasks();
    }

    public void applyRandomMutation(LivingEntity entity) {
        if (entity instanceof Phantom) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                applyMutation(entity, "KAMIKAZE");
            } else {
                Creeper creeper = (Creeper) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.CREEPER);
                entity.addPassenger(creeper);
                applyMutation(entity, "PHANTOM_RIDER");
            }
        } else if (entity instanceof Drowned) {
            applyMutation(entity, "FLOOD");
        } else if (entity instanceof Zombie zombie && !(entity instanceof PigZombie)) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                zombie.setBaby();
                zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
                applyMutation(entity, "SPEED_BABY");
            } else {
                applyMutation(entity, "TOXIC");
            }
        } else if (entity instanceof Enderman) {
            applyMutation(entity, "ABDUCTOR");
        } else if (entity instanceof Creeper) {
            int rand = ThreadLocalRandom.current().nextInt(4);
            if (rand == 0) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 1, false, true));
                applyMutation(entity, "INVISIBLE");
            } else if (rand == 1) {
                applyMutation(entity, "SPLITTING");
            } else if (rand == 2) {
                applyMutation(entity, "JUMPER");
            } else {
                applyMutation(entity, "ILLUSIONER");
            }
        } else if (entity instanceof Bat) {
            applyMutation(entity, "VAMPIRE");
        } else if (entity instanceof Spider || entity instanceof CaveSpider) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                applyMutation(entity, "WEAVING");
            } else {
                applyMutation(entity, "PIGGYBACK_SPIDER");
            }
        } else if (entity instanceof Pig || entity instanceof Cow) {
            applyMutation(entity, "TROJAN");
        } else if (entity instanceof Endermite) {
            applyMutation(entity, "THIEF");
        } else if (entity instanceof Squid || entity instanceof GlowSquid) {
            applyMutation(entity, "BLINDING");
        } else if (entity instanceof Witch) {
            applyMutation(entity, "WITHER_WITCH");
        } else if (entity instanceof AbstractSkeleton skeleton) {
            if (skeleton instanceof WitherSkeleton) {
                applyMutation(entity, "DEATH_SWAP");
            } else {
                applyMutation(entity, "NECROMANCER");
            }
        } else if (entity instanceof Ghast) {
            applyMutation(entity, "BOMBER");
        } else if (entity instanceof Slime) {
            applyMutation(entity, "MAGNETIC");
        } else if (entity instanceof Chicken) {
            applyMutation(entity, "LASER_CHICKEN");
            spawnLaserChickenPassenger((Chicken) entity);
        } else if (entity instanceof Sheep) {
            applyMutation(entity, "EXPLOSIVE_SHEEP");
        } else if (entity instanceof Silverfish) {
            applyMutation(entity, "INFESTATION");
        } else if (entity instanceof Bee) {
            applyMutation(entity, "LEVITY_BEE");
        } else if (entity instanceof Blaze) {
            applyMutation(entity, "RING_OF_FIRE");
        } else if (entity instanceof Villager) {
            applyMutation(entity, "SCAMMER");
        } else if (entity instanceof Llama) {
            applyMutation(entity, "ACID_SPIT");
        }
    }

    private void spawnLaserChickenPassenger(Chicken chicken) {
        Guardian guardian = chicken.getWorld().spawn(chicken.getLocation(), Guardian.class);
        guardian.setInvisible(true);
        guardian.setSilent(true);
        guardian.setInvulnerable(true);
        applyMutation(guardian, "LASER_CHICKEN_PASSENGER");
        chicken.addPassenger(guardian);
    }

    private void applyMutation(LivingEntity entity, String mutationId) {
        entity.getPersistentDataContainer().set(mutationKey, PersistentDataType.STRING, mutationId);
        activeMutants.put(entity, mutationId);
    }

    private String getMutation(Entity entity) {
        if (!(entity instanceof LivingEntity)) return null;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(mutationKey, PersistentDataType.STRING)) {
            return pdc.get(mutationKey, PersistentDataType.STRING);
        }
        return null;
    }

    private void triggerSheep(Sheep sheep) {
        if (sheepCountdown.containsKey(sheep)) return;
        sheepCountdown.put(sheep, 60); // 60 ticks = 3 seconds
        sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.2F, 0.8F);
        sheep.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 4));
    }

    private void startInfestation(Player player) {
        if (infestedPlayers.containsKey(player)) return;
        infestedPlayers.put(player, 15); // 15 waves * 2 seconds = 30 seconds total
        player.sendMessage("§c¡Has sido infestado por un pececillo de plata!");
        player.playSound(player.getLocation(), Sound.ENTITY_SILVERFISH_HURT, 1.0F, 0.5F);
    }

    private void startTasks() {
        new BukkitRunnable() {
            private int infestedTickCounter = 0;

            @Override
            public void run() {
                // Tick infested players
                infestedTickCounter++;
                if (infestedTickCounter % 4 == 0) { // Every 2s (40 ticks)
                    Iterator<Map.Entry<Player, Integer>> pit = infestedPlayers.entrySet().iterator();
                    while (pit.hasNext()) {
                        Map.Entry<Player, Integer> entry = pit.next();
                        Player player = entry.getKey();
                        int wavesLeft = entry.getValue();

                        if (player == null || !player.isOnline() || player.isDead()) {
                            pit.remove();
                            continue;
                        }

                        player.damage(1.0);
                        player.playSound(player.getLocation(), Sound.ENTITY_SILVERFISH_AMBIENT, 1.0F, 1.0F);
                        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                        
                        Silverfish fish = (Silverfish) player.getWorld().spawnEntity(player.getLocation(), EntityType.SILVERFISH);
                        fish.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));

                        if (wavesLeft <= 1) {
                            pit.remove();
                            player.sendMessage("§a¡Los pececillos de plata han salido de tu cuerpo!");
                        } else {
                            infestedPlayers.put(player, wavesLeft - 1);
                        }
                    }
                }

                Iterator<Map.Entry<LivingEntity, String>> it = activeMutants.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<LivingEntity, String> entry = it.next();
                    LivingEntity entity = entry.getKey();
                    String mutation = entry.getValue();

                    if (entity == null || !entity.isValid() || entity.isDead()) {
                        it.remove();
                        continue;
                    }

                    if (mutation.equals("ABDUCTOR") && entity instanceof Enderman enderman) {
                        for (Entity passenger : enderman.getPassengers()) {
                            if (passenger instanceof Player p) {
                                p.damage(1.0, enderman);
                            }
                        }
                    } else if (mutation.equals("VAMPIRE") || mutation.equals("BLINDING")) {
                        Player nearest = findNearestPlayer(entity, 15);
                        if (nearest != null) {
                            Vector dir = nearest.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.3);
                            entity.setVelocity(entity.getVelocity().add(dir));
                            if (entity.getLocation().distanceSquared(nearest.getLocation()) < 4.0) {
                                nearest.damage(1.0, entity);
                                if (mutation.equals("VAMPIRE")) {
                                    nearest.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
                                } else {
                                    nearest.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 1));
                                    nearest.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                                }
                            }
                        }
                    } else if (mutation.equals("JUMPER") && entity instanceof Creeper creeper) {
                        LivingEntity target = creeper.getTarget();
                        if (target != null && entity.isOnGround()) {
                            double dist = entity.getLocation().distance(target.getLocation());
                            if (dist > 3 && dist < 15) {
                                if (ThreadLocalRandom.current().nextInt(10) == 0) {
                                    Vector jump = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(1.5).setY(0.8);
                                    entity.setVelocity(jump);
                                }
                            }
                        }
                    } else if (mutation.equals("WITHER_WITCH") && entity instanceof Witch witch) {
                        Player nearest = findNearestPlayer(entity, 5);
                        if (nearest != null && ThreadLocalRandom.current().nextInt(20) == 0) {
                            entity.getWorld().strikeLightning(nearest.getLocation());
                        }
                    } else if (mutation.equals("BOMBER") && entity instanceof Ghast ghast) {
                        Player nearest = findNearestPlayer(ghast, 20);
                        if (nearest != null && nearest.getLocation().getY() < ghast.getLocation().getY()) {
                            if (ThreadLocalRandom.current().nextInt(6) == 0) { // ~3 seconds cooldown
                                Creeper creeper = (Creeper) ghast.getWorld().spawnEntity(ghast.getLocation().subtract(0, 1.5, 0), EntityType.CREEPER);
                                creeper.setMaxFuseTicks(30);
                                creeper.ignite();
                                creeper.setVelocity(new Vector(0, -0.5, 0));
                            }
                        }
                    } else if (mutation.equals("MAGNETIC") && entity instanceof Slime slime) {
                        for (Entity e : slime.getNearbyEntities(12, 12, 12)) {
                            if (e instanceof Player player && player.getGameMode() == GameMode.SURVIVAL) {
                                Vector direction = slime.getLocation().toVector().subtract(player.getLocation().toVector());
                                double distance = direction.length();
                                if (distance > 1.0) {
                                    double pullForce = 0.15 * (1.0 - (distance / 12.0));
                                    Vector pull = direction.normalize().multiply(pullForce);
                                    pull.setY(pull.getY() + 0.05);
                                    player.setVelocity(player.getVelocity().add(pull));
                                }
                            }
                        }
                    } else if (mutation.equals("LASER_CHICKEN") && entity instanceof Chicken chicken) {
                        boolean hasPassenger = false;
                        for (Entity passenger : chicken.getPassengers()) {
                            if (passenger instanceof Guardian) {
                                hasPassenger = true;
                                break;
                            }
                        }
                        if (!hasPassenger) {
                            spawnLaserChickenPassenger(chicken);
                        }
                    } else if (mutation.equals("LASER_CHICKEN_PASSENGER") && entity instanceof Guardian guardian) {
                        if (!(guardian.getVehicle() instanceof Chicken)) {
                            guardian.remove();
                            it.remove();
                            continue;
                        }
                    } else if (mutation.equals("EXPLOSIVE_SHEEP") && entity instanceof Sheep sheep) {
                        if (sheepCountdown.containsKey(sheep)) {
                            int ticksLeft = sheepCountdown.get(sheep) - 10;
                            if (ticksLeft <= 0) {
                                sheepCountdown.remove(sheep);
                                sheep.getWorld().createExplosion(sheep.getLocation(), 3.5F, false, false);
                                sheep.remove();
                                continue;
                            } else {
                                sheepCountdown.put(sheep, ticksLeft);
                                sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0F, 1.5F);
                                DyeColor[] rainbowColors = { DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.PURPLE, DyeColor.PINK };
                                sheep.setColor(rainbowColors[(ticksLeft / 2) % rainbowColors.length]);
                                sheep.getWorld().spawnParticle(Particle.FLAME, sheep.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.01);

                                Player target = findNearestPlayer(sheep, 15);
                                if (target != null) {
                                    Vector dir = target.getLocation().toVector().subtract(sheep.getLocation().toVector()).normalize().multiply(0.4);
                                    dir.setY(sheep.getVelocity().getY());
                                    sheep.setVelocity(dir);
                                }
                            }
                        } else {
                            Player nearest = findNearestPlayer(sheep, 4);
                            if (nearest != null) {
                                triggerSheep(sheep);
                            }
                        }
                    } else if (mutation.equals("PIGGYBACK_SPIDER") && entity instanceof Spider spider) {
                        Entity vehicle = spider.getVehicle();
                        if (vehicle instanceof Player player) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 1));
                            player.damage(1.0, spider);

                            boolean inWater = player.getLocation().getBlock().getType() == Material.WATER || player.getEyeLocation().getBlock().getType() == Material.WATER;
                            boolean ceilingOverhead = player.getEyeLocation().add(0, 1, 0).getBlock().getType().isSolid();
                            
                            if (inWater || ceilingOverhead) {
                                spider.leaveVehicle();
                                spiderRideCooldown.put(spider, System.currentTimeMillis() + 4000);
                                player.sendMessage("§a¡Te quitaste la araña de la cabeza!");
                            }
                        }
                    } else if (mutation.equals("FLOOD") && entity instanceof Drowned drowned) {
                        int timer = drownedFloodTimer.getOrDefault(drowned, 0) + 1;
                        if (timer >= 10) {
                            drownedFloodTimer.put(drowned, 0);
                            Player nearest = findNearestPlayer(drowned, 8);
                            if (nearest != null) {
                                Location center = nearest.getEyeLocation();
                                java.util.List<org.bukkit.block.BlockState> originalStates = new java.util.ArrayList<>();
                                for (int x = -1; x <= 1; x++) {
                                    for (int y = -1; y <= 1; y++) {
                                        for (int z = -1; z <= 1; z++) {
                                            org.bukkit.block.Block block = center.clone().add(x, y, z).getBlock();
                                            if (block.getType() == Material.AIR) {
                                                originalStates.add(block.getState());
                                                block.setType(Material.WATER, false);
                                            }
                                        }
                                    }
                                }
                                if (!originalStates.isEmpty()) {
                                    nearest.sendMessage("§c¡El ahogado ha creado una inundación a tu alrededor!");
                                    nearest.playSound(nearest.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.0F, 0.5F);
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            for (org.bukkit.block.BlockState state : originalStates) {
                                                state.update(true, false);
                                            }
                                        }
                                    }.runTaskLater(plugin, 80L);
                                }
                            }
                        } else {
                            drownedFloodTimer.put(drowned, timer);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // run every 0.5s
    }

    private Player findNearestPlayer(Entity entity, double radius) {
        Player nearest = null;
        double minDist = radius * radius;
        for (Entity e : entity.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player p && p.getGameMode() == GameMode.SURVIVAL) {
                double d = e.getLocation().distanceSquared(entity.getLocation());
                if (d < minDist) {
                    minDist = d;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Entity damager = event.getDamager();
        String mutation = getMutation(damager);
        if (mutation == null) return;

        switch (mutation) {
            case "ABDUCTOR":
                damager.addPassenger(player);
                break;
            case "WEAVING":
                player.getLocation().getBlock().setType(Material.COBWEB);
                break;
            case "KAMIKAZE":
                damager.getWorld().createExplosion(damager.getLocation(), 2.0F, false, false);
                damager.remove();
                break;
            case "THIEF":
                stealItem(player, (LivingEntity) damager);
                break;
            case "INFESTATION":
                startInfestation(player);
                damager.remove();
                break;
            case "LEVITY_BEE":
                player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 80, 4));
                player.sendMessage("§c¡Una abeja te inyectó veneno de levitación!");
                if (damager instanceof Bee bee) {
                    bee.setHasStung(false);
                    bee.setTarget(player);
                }
                break;
            case "PIGGYBACK_SPIDER":
                long rideNow = System.currentTimeMillis();
                if (rideNow > spiderRideCooldown.getOrDefault(damager, 0L)) {
                    player.addPassenger(damager);
                    player.sendMessage("§c¡Una araña se te subió a la cabeza!");
                    player.playSound(player.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.0F, 1.2F);
                }
                break;
            case "DEATH_SWAP":
                long swapNow = System.currentTimeMillis();
                if (swapNow > deathSwapCooldown.getOrDefault(damager, 0L)) {
                    if (ThreadLocalRandom.current().nextDouble() <= 0.30) {
                        deathSwapCooldown.put((LivingEntity) damager, swapNow + 3000);
                        Location loc1 = player.getLocation();
                        Location loc2 = damager.getLocation();
                        player.teleport(loc2);
                        damager.teleport(loc1);
                        player.getWorld().spawnParticle(Particle.PORTAL, loc1, 30, 0.5, 1, 0.5, 0.1);
                        player.getWorld().spawnParticle(Particle.PORTAL, loc2, 30, 0.5, 1, 0.5, 0.1);
                        player.getWorld().playSound(loc1, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                        player.getWorld().playSound(loc2, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                        player.sendMessage("§c¡Has intercambiado posición con el esqueleto del Wither!");
                    }
                }
                break;
        }
    }

    private void stealItem(Player player, LivingEntity thief) {
        if (thief.getPersistentDataContainer().has(stolenItemKey, PersistentDataType.STRING)) return; // already stole
        
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                // simple item drop fallback if we don't want to serialize
                thief.getPersistentDataContainer().set(stolenItemKey, PersistentDataType.STRING, String.valueOf(i));
                thief.getEquipment().setItemInMainHand(item.clone());
                player.getInventory().setItem(i, null);
                player.sendMessage("§c¡Un Endermite te robó un objeto!");
                break;
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        String mutation = getMutation(event.getEntity());
        if ("ILLUSIONER".equals(mutation)) {
            if (!event.getEntity().getPersistentDataContainer().has(new NamespacedKey(plugin, "split"), PersistentDataType.BYTE)) {
                event.getEntity().getPersistentDataContainer().set(new NamespacedKey(plugin, "split"), PersistentDataType.BYTE, (byte) 1);
                for (int i = 0; i < 3; i++) {
                    Creeper fake = (Creeper) event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.CREEPER);
                    applyMutation(fake, "ILLUSION_CLONE");
                }
            }
        } else if ("ILLUSION_CLONE".equals(mutation)) {
            event.getEntity().getWorld().spawnParticle(Particle.POOF, event.getEntity().getLocation(), 10);
            event.getEntity().remove();
        } else if ("EXPLOSIVE_SHEEP".equals(mutation) && event.getEntity() instanceof Sheep sheep) {
            triggerSheep(sheep);
        } else if ("RING_OF_FIRE".equals(mutation) && event.getEntity() instanceof Blaze blaze) {
            if (event instanceof EntityDamageByEntityEvent) {
                shootRingOfFire(blaze);
            }
        } else if ("DEATH_SWAP".equals(mutation) && event.getEntity() instanceof LivingEntity skeleton) {
            if (event instanceof EntityDamageByEntityEvent edbe && edbe.getDamager() instanceof Player player) {
                long now = System.currentTimeMillis();
                if (now > deathSwapCooldown.getOrDefault(skeleton, 0L)) {
                    if (ThreadLocalRandom.current().nextDouble() <= 0.30) {
                        deathSwapCooldown.put(skeleton, now + 3000);
                        Location loc1 = player.getLocation();
                        Location loc2 = skeleton.getLocation();
                        player.teleport(loc2);
                        skeleton.teleport(loc1);
                        player.getWorld().spawnParticle(Particle.PORTAL, loc1, 30, 0.5, 1, 0.5, 0.1);
                        player.getWorld().spawnParticle(Particle.PORTAL, loc2, 30, 0.5, 1, 0.5, 0.1);
                        player.getWorld().playSound(loc1, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                        player.getWorld().playSound(loc2, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                        player.sendMessage("§c¡Has intercambiado posición con el esqueleto del Wither!");
                    }
                }
            }
        }
    }

    private void shootRingOfFire(Blaze blaze) {
        Location loc = blaze.getLocation().add(0, 1, 0);
        double angleStep = 2 * Math.PI / 8;
        for (int i = 0; i < 8; i++) {
            double angle = i * angleStep;
            Vector direction = new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize().multiply(0.5);
            SmallFireball fireball = blaze.getWorld().spawn(loc, SmallFireball.class);
            fireball.setShooter(blaze);
            fireball.setDirection(direction);
        }
    }

    @EventHandler
    public void onPlayerShear(PlayerShearEntityEvent event) {
        Entity entity = event.getEntity();
        String mutation = getMutation(entity);
        if ("EXPLOSIVE_SHEEP".equals(mutation) && entity instanceof Sheep sheep) {
            event.setCancelled(true);
            triggerSheep(sheep);
        }
    }

    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent event) {
        LivingEntity parent = event.getEntity();
        String mutation = getMutation(parent);
        if ("MAGNETIC".equals(mutation)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!parent.isValid() || parent.isDead()) {
                        for (Entity e : parent.getNearbyEntities(2.0, 2.0, 2.0)) {
                            if (e instanceof Slime child && getMutation(child) == null) {
                                applyMutation(child, "MAGNETIC");
                            }
                        }
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String mutation = getMutation(entity);
        if (mutation == null) return;
        activeMutants.remove(entity);

        switch (mutation) {
            case "TROJAN":
                if (ThreadLocalRandom.current().nextBoolean()) {
                    for (int i = 0; i < 3; i++) {
                        Zombie z = (Zombie) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.ZOMBIE);
                        z.setBaby();
                        z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
                    }
                } else {
                    entity.getWorld().spawnEntity(entity.getLocation(), EntityType.VINDICATOR);
                }
                break;
            case "TOXIC":
                AreaEffectCloud cloud = (AreaEffectCloud) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.AREA_EFFECT_CLOUD);
                cloud.setRadius(3.0F);
                cloud.setDuration(200);
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 200, 1), true);
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0), true);
                break;
            case "THIEF":
                ItemStack hand = entity.getEquipment().getItemInMainHand();
                if (hand != null && hand.getType() != Material.AIR) {
                    entity.getWorld().dropItemNaturally(entity.getLocation(), hand);
                    entity.getEquipment().setItemInMainHand(null);
                }
                break;
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        String mutation = getMutation(event.getEntity());
        if ("SPLITTING".equals(mutation)) {
            for (int i = 0; i < 3; i++) {
                Zombie z = (Zombie) event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.ZOMBIE);
                z.setBaby();
            }
        } else if ("ILLUSION_CLONE".equals(mutation)) {
            event.setCancelled(true);
            event.getEntity().getWorld().spawnParticle(Particle.POOF, event.getEntity().getLocation(), 10);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity shooter) {
            String mutation = getMutation(shooter);
            if ("WITHER_WITCH".equals(mutation) && event.getEntity() instanceof ThrownPotion potion) {
                ItemStack item = potion.getItem();
                // Instead of editing the item perfectly, we just set a PDC flag to change the effect when it lands
                potion.getPersistentDataContainer().set(mutationKey, PersistentDataType.STRING, "WITHER_POTION");
            } else if ("NECROMANCER".equals(mutation) && event.getEntity() instanceof Arrow arrow) {
                arrow.getPersistentDataContainer().set(mutationKey, PersistentDataType.STRING, "NECRO_ARROW");
            }
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(mutationKey, PersistentDataType.STRING)) {
            String val = event.getEntity().getPersistentDataContainer().get(mutationKey, PersistentDataType.STRING);
            if ("WITHER_POTION".equals(val)) {
                for (LivingEntity affected : event.getAffectedEntities()) {
                    affected.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 1));
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(mutationKey, PersistentDataType.STRING)) {
            String val = event.getEntity().getPersistentDataContainer().get(mutationKey, PersistentDataType.STRING);
            if ("NECRO_ARROW".equals(val)) {
                Zombie z = (Zombie) event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.ZOMBIE);
                z.setBaby();
                event.getEntity().remove();
            }
        }

        if (event.getHitEntity() instanceof Player player && player.getGameMode() == GameMode.SURVIVAL) {
            if (event.getEntity() instanceof LlamaSpit spit && spit.getShooter() instanceof Llama llama) {
                if ("ACID_SPIT".equals(getMutation(llama))) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                    player.damage(4.0, llama);
                    
                    for (ItemStack armor : player.getInventory().getArmorContents()) {
                        if (armor != null && armor.getType() != Material.AIR) {
                            org.bukkit.inventory.meta.Damageable meta = (org.bukkit.inventory.meta.Damageable) armor.getItemMeta();
                            if (meta != null) {
                                meta.setDamage(meta.getDamage() + 25);
                                armor.setItemMeta(meta);
                                if (meta.getDamage() >= armor.getType().getMaxDurability()) {
                                    armor.setAmount(0);
                                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                                }
                            }
                        }
                    }
                    player.sendMessage("§c¡El escupitajo de llama de ácido corroyó tu armadura!");
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }
    }

    // An interaction another plugin already cancelled (a Tideframe menu holding the player's clicks, a protected area)
    // never reached the villager: no theft.
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        String mutation = getMutation(entity);
        if ("SCAMMER".equals(mutation) && entity instanceof Villager villager) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            
            player.getWorld().playSound(villager.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.2F, 0.8F);
            
            boolean stole = false;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.EMERALD) {
                    int amount = Math.min(item.getAmount(), 64);
                    item.setAmount(item.getAmount() - amount);
                    stole = true;
                    player.sendMessage("§c¡El estafador te robó " + amount + " Esmeraldas!");
                    break;
                }
            }
            if (!stole) {
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        player.getInventory().setItem(i, null);
                        player.sendMessage("§c¡El estafador te robó: " + item.getType().name() + "!");
                        stole = true;
                        break;
                    }
                }
            }
            if (!stole) {
                player.sendMessage("§cEl estafador intentó robarte, pero no tienes nada.");
            }
            
            Location loc = villager.getLocation();
            LivingEntity attacker;
            if (ThreadLocalRandom.current().nextBoolean()) {
                attacker = (LivingEntity) villager.getWorld().spawnEntity(loc, EntityType.VINDICATOR);
            } else {
                attacker = (LivingEntity) villager.getWorld().spawnEntity(loc, EntityType.ILLUSIONER);
            }
            
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0));
            if (attacker instanceof Mob mob) {
                mob.setTarget(player);
            }
            
            villager.getWorld().spawnParticle(Particle.POOF, loc, 15, 0.2, 0.5, 0.2, 0.1);
            villager.remove();
        }
    }
}
