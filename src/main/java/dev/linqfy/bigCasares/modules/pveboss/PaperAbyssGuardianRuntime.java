package dev.linqfy.bigCasares.modules.pveboss;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Consumer;

public final class PaperAbyssGuardianRuntime implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final JavaPlugin plugin;
    private final AbyssGuardianDefinition definition;
    private final PveBossService service;
    private final ClientPlatformGateway platformGateway;
    private final Function<UUID, Boolean> resourcePackLoaded;
    private final BossPresentationService presentation;
    private final BossMusicService music;
    private final NamespacedKey bossIdKey;
    private final BossAnimationService animationService;
    private final JavaModelGateway javaModels;
    private final BossModelAnimationMapper modelAnimations;
    private final Consumer<List<BossDamageStanding>> damageLeaderboard;
    private final BossEncounterControllerFactory encounterFactory;
    private final PveTransientOwner transients = new PveTransientOwner();
    private final Map<UUID, Instance> instances = new LinkedHashMap<>();
    private BukkitTask scheduler;

    public PaperAbyssGuardianRuntime(
        JavaPlugin plugin,
        AbyssGuardianDefinition definition,
        PveBossService service,
        ClientPlatformGateway platformGateway,
        Function<UUID, Boolean> resourcePackLoaded,
        JavaModelGateway javaModels
    ) {
        this(plugin, definition, service, platformGateway, resourcePackLoaded, javaModels, ignored -> { });
    }

    public PaperAbyssGuardianRuntime(
        JavaPlugin plugin,
        AbyssGuardianDefinition definition,
        PveBossService service,
        ClientPlatformGateway platformGateway,
        Function<UUID, Boolean> resourcePackLoaded,
        JavaModelGateway javaModels,
        Consumer<List<BossDamageStanding>> damageLeaderboard
    ) {
        this(plugin, definition, service, platformGateway, resourcePackLoaded, javaModels, damageLeaderboard, null);
    }

    /**
     * @param encounterFactory scripted controller of every instance, or {@code null} for the plain ability
     *                         runtime; a scripted boss is marked with its own persistent key
     */
    public PaperAbyssGuardianRuntime(
        JavaPlugin plugin,
        AbyssGuardianDefinition definition,
        PveBossService service,
        ClientPlatformGateway platformGateway,
        Function<UUID, Boolean> resourcePackLoaded,
        JavaModelGateway javaModels,
        Consumer<List<BossDamageStanding>> damageLeaderboard,
        BossEncounterControllerFactory encounterFactory
    ) {
        this.plugin = plugin;
        this.definition = definition;
        this.service = service;
        this.platformGateway = platformGateway;
        this.resourcePackLoaded = resourcePackLoaded;
        this.presentation = new BossPresentationService(new PaperBossPresentationAdapter());
        this.music = new BossMusicService(new PaperBossAudioGateway(plugin.getServer()));
        this.bossIdKey = new NamespacedKey(plugin,
            BossEncounterRules.markerKeyName(definition.id(), encounterFactory != null));
        this.animationService = new BossAnimationService(new PaperBossAnimationGateway(new dev.linqfy.bigCasares.modules.geyser.GeyserBossVisualGateway()));
        this.javaModels = java.util.Objects.requireNonNull(javaModels, "javaModels");
        this.modelAnimations = new BossModelAnimationMapper();
        this.damageLeaderboard = java.util.Objects.requireNonNull(damageLeaderboard, "damageLeaderboard");
        this.encounterFactory = encounterFactory;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        scheduler = plugin.getServer().getScheduler().runTaskTimer(
            plugin, transients.guard(this::tick), definition.updateTicks(), definition.updateTicks());
        transients.own(scheduler, "boss scheduler", scheduler::cancel);
    }

    public UUID spawn(Location location) {
        if (!transients.isActive()) {
            throw new IllegalStateException("PvE boss runtime is stopped");
        }
        World world = java.util.Objects.requireNonNull(location.getWorld(), "location world");
        UUID bossId = UUID.randomUUID();
        double audienceRadiusSquared = definition.audienceRadius() * definition.audienceRadius();
        int participantCount = (int) world.getPlayers().stream()
            .filter(Player::isOnline)
            .filter(player -> !player.isDead())
            .filter(player -> player.getGameMode() != org.bukkit.GameMode.SPECTATOR)
            .filter(player -> player.getLocation().distanceSquared(location) <= audienceRadiusSquared)
            .count();
        double instanceMaximumHealth = definition.maximumHealthFor(participantCount);
        Warden boss = world.spawn(location, Warden.class, entity -> {
            ClientEntityPresentationRegistry.register(entity.getUniqueId(), "WARDEN", bossIdKey.getKey());
            entity.getPersistentDataContainer().set(bossIdKey, PersistentDataType.STRING, bossId.toString());
            if (encounterFactory != null) {
                // mob-scaling and Jeremy only know the two built-in boss keys; this is the marker they skip
                entity.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "custom_entity"), PersistentDataType.STRING, definition.id());
            }
            entity.customName(LEGACY.deserialize(definition.displayName()));
            entity.setCustomNameVisible(false);
            // a scripted boss is not saved with its chunk: its fight cannot be recovered after an unload or a
            // restart, and a saved Warden would come back invisible with no runtime behind it
            entity.setPersistent(encounterFactory == null);
            entity.setRemoveWhenFarAway(false);
            entity.setInvisible(true);
            entity.setAI(false);
            entity.setGravity(true);
            entity.setSilent(true);
            entity.setAware(false);
            AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth == null) {
                throw new IllegalStateException("Warden has no max-health attribute");
            }
            double vanillaHealth = Math.min(instanceMaximumHealth, 1024.0);
            maxHealth.setBaseValue(vanillaHealth);
            entity.setHealth(vanillaHealth);
        });
        JavaModelHandle model;
        try {
            model = javaModels.attach(boss, definition.javaModelKey());
        } catch (RuntimeException failure) {
            ClientEntityPresentationRegistry.unregister(boss.getUniqueId());
            boss.remove();
            throw failure;
        }
        TextDisplay text = world.spawn(location.clone().add(0.0, 4.0, 0.0), TextDisplay.class, display -> {
            mark(display, bossId);
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setNoPhysics(true);
        });

        BossAbilityCoordinator coordinator = new BossAbilityCoordinator(
            bossId,
            definition.abilities(),
            this::executeEffects,
            new Telegraphs()
        );
        BossHealthPool health = new BossHealthPool(instanceMaximumHealth);

        BossAnimationDefinition idleAnim = definition.animationFor("idle");
        if (idleAnim != null) {
            animationService.setDefaultAnimation(bossId, idleAnim);
            animateModel(model, idleAnim.id());
        }

        Instance instance = new Instance(
            bossId,
            boss,
            model,
            text,
            coordinator,
            health,
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>(),
            definition.id().equals("tung-tung-sahur")
                ? new PaperSahurCombatController(
                    plugin,
                    bossId,
                    boss,
                    model,
                    javaModels,
                    () -> eligiblePlayers(boss),
                    animation -> animateModel(model, animation)
                )
                : null,
            1
        );
        if (encounterFactory != null) {
            try {
                instance.encounter = new GuardedBossEncounter(
                    java.util.Objects.requireNonNull(
                        encounterFactory.create(new EncounterContext(instance)), "encounter controller"),
                    (stage, failure) -> logEncounterFailure(bossId, stage, failure));
            } catch (RuntimeException failure) {
                animationService.removeBoss(bossId);
                ClientEntityPresentationRegistry.unregister(boss.getUniqueId());
                javaModels.close(model);
                text.remove();
                boss.remove();
                throw failure;
            }
        }
        instances.put(bossId, instance);
        if (instance.sahur != null) instance.sahur.start();
        if (instance.encounter != null && !instance.encounter.start()) {
            // a controller that could not start would be ticked half-built; end the fight instead
            remove(bossId, MusicStopReason.MODULE_DISABLED, true);
            throw new IllegalStateException("El encuentro de " + definition.displayName() + " no pudo iniciar.");
        }
        service.registerInstance(coordinator);

        BossAudience audience = audienceFor(boss);
        presentation.showBoss(audience, view(instance));
        announce(instance, "spawn", BossAnnouncementChannel.CHAT);
        return bossId;
    }

    public int activeCount() {
        return instances.size();
    }

    public boolean isActive(UUID bossInstanceId) {
        return instances.containsKey(bossInstanceId);
    }

    /** Removes one live instance without publishing its damage ranking (no rewards). */
    public boolean removeBoss(UUID bossInstanceId) {
        if (!instances.containsKey(bossInstanceId)) {
            return false;
        }
        remove(bossInstanceId, MusicStopReason.MODULE_DISABLED, true);
        return true;
    }

    public void stop() {
        for (PveTransientCleanupFailure failure : transients.close()) {
            plugin.getLogger().warning(
                "No se pudo limpiar " + failure.resource() + ": " + failure.cause().getMessage());
        }
        scheduler = null;
        org.bukkit.event.HandlerList.unregisterAll(this);
        new ArrayList<>(instances.keySet()).forEach(id -> remove(id, MusicStopReason.MODULE_DISABLED, true));
        music.stopAll(MusicStopReason.MODULE_DISABLED);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        readBossId(event.getEntity()).ifPresent(id -> {
            Instance instance = instances.get(id);
            event.getDrops().clear();
            event.setDroppedExp(0);
            publishDamageLeaderboard(instance);
            remove(id, MusicStopReason.BOSS_DEFEATED, false);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        UUID id = readBossId(event.getEntity()).orElse(null);
        Instance instance = id == null ? null : instances.get(id);
        if (instance == null || event.getFinalDamage() <= 0.0) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
            && byEntity.getDamager() instanceof Projectile projectile
            && instance.sahur != null
            && instance.sahur.blocks(projectile)) {
            event.setCancelled(true);
            projectile.remove();
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
            && byEntity.getDamager() instanceof Projectile projectile
            && instance.encounter != null
            && instance.encounter.blocksProjectile(projectile)) {
            event.setCancelled(true);
            projectile.remove();
            return;
        }
        event.setCancelled(true);
        double damage = instance.encounter == null
            ? event.getFinalDamage()
            : instance.encounter.filterDamage(event, attackingPlayer(event).orElse(null), event.getFinalDamage());
        if (damage <= 0.0 || !instances.containsKey(id)) {
            return;
        }
        instance.health.damage(damage);
        attackingPlayer(event).ifPresent(player -> {
            recordDamage(instance, player, damage);
            if (instance.sahur != null
                && event instanceof EntityDamageByEntityEvent byEntity
                && !(byEntity.getDamager() instanceof Projectile)
                && player.getFallDistance() > 0.0f
                && !player.isOnGround()) {
                instance.sahur.recordCriticalHit(Instant.now());
            }
        });
        Location effectLocation = instance.boss.getLocation().add(0.0, 1.5, 0.0);
        instance.boss.getWorld().spawnParticle(
            Particle.DAMAGE_INDICATOR, effectLocation, 8, 0.8, 1.0, 0.8, 0.05);
        if (instance.health.destroyed()) {
            instance.boss.setHealth(0.0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        music.disconnect(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getEntity() instanceof Warden warden) {
            if (readBossId(warden).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnger(WardenAngerChangeEvent event) {
        if (event.getEntity() instanceof Warden warden) {
            if (readBossId(warden).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    private void tick() {
        Instant now = Instant.now();
        for (Map.Entry<UUID, Instance> entry : new ArrayList<>(instances.entrySet())) {
            UUID bossId = entry.getKey();
            Instance instance = entry.getValue();
            if (!instance.boss.isValid() || instance.boss.isDead()) {
                remove(bossId, MusicStopReason.BOSS_DEFEATED, false);
                continue;
            }
            updateInstance(bossId, instance, now);
        }
    }

    private void updateInstance(UUID bossId, Instance instance, Instant now) {
        if (instance.encounter != null) {
            instance.encounter.tick(now);
            if (!instances.containsKey(bossId)) {
                return;
            }
        }
        Location loc = instance.boss.getLocation();

        animationService.tick(bossId, definition.updateTicks());

        instance.text.teleport(loc.clone().add(0.0, 4.0, 0.0));

        double health = instance.health.current();
        double fraction = instance.health.fraction();
        int phase = instance.encounter == null
            ? definition.phaseFor(fraction)
            : instance.encounter.phase(definition.phaseFor(fraction));
        if (phase != instance.phase) {
            instance.phase = phase;
            if (phase == 3) {
                BossAnimationDefinition rage = definition.animationFor("rage");
                if (rage != null) {
                    animationService.playAnimation(bossId, rage);
                    animateModel(instance.model, rage.id());
                }
            }
            announce(instance, "phase-" + phase, BossAnnouncementChannel.TITLE);
        }

        BossAudience audience = audienceFor(instance.boss);
        presentation.showBoss(audience, view(instance));
        instance.text.text(LEGACY.deserialize(
            "§5§l" + definition.displayName() + "\n§f" + Math.round(health)
                + " §7/ §f" + Math.round(instance.health.maximum()) + " HP\n§dFASE " + roman(phase)));

        BossAbilityContext context = new BossAbilityContext(
            position(loc), health, instance.health.maximum(), phase,
            candidates(instance), currentTarget(instance.boss).orElse(null));
        Optional<BossAbilityRuntime> runtime = instance.encounter != null && instance.encounter.suspendsAbilities()
            ? Optional.empty()
            : instance.coordinator.tick(now, context);
        if (runtime.isPresent()) {
            BossAbilityRuntime rt = runtime.get();
            BossAbilityDefinition ability = definition.abilities().stream()
                .filter(a -> a.id().equals(rt.abilityId())).findFirst().orElseThrow();

            if (rt.state() == BossAbilityState.CASTING) {
                presentation.showAbilityCast(new BossAbilityCastView(
                    bossId,
                    ability.id(),
                    displayAbilityName(ability.id()),
                    Duration.between(now, rt.completesAt()),
                    ability.telegraph()
                ));

                if (ability.animationId() != null) {
                    BossAnimationDefinition anim = definition.animationFor(ability.animationId());
                    if (anim != null) {
                        animationService.playAnimation(bossId, anim);
                        animateModel(instance.model, anim.id());
                    }
                }
            } else if (rt.state() == BossAbilityState.COOLDOWN) {
                animationService.stopAnimation(bossId);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            double distance = player.getWorld().equals(instance.boss.getWorld())
                ? player.getLocation().distance(loc)
                : definition.audienceRadius() + 1.0;
            boolean customAudio = platformGateway.resolvePlatform(player.getUniqueId()) == ClientPlatform.BEDROCK
                || resourcePackLoaded.apply(player.getUniqueId());
            BossMusicTrack track = instance.encounter == null
                ? definition.musicForPhase(phase)
                : BossEncounterRules.musicFor(definition.musicByPhase(), phase).orElse(null);
            if (track == null) {
                continue;
            }
            music.update(
                bossId,
                player.getUniqueId(),
                track,
                distance,
                definition.audienceRadius(),
                customAudio,
                customAudio
            );
        }
    }

    private void executeEffects(
        UUID bossId,
        BossAbilityDefinition ability,
        BossAbilityRuntime runtime
    ) {
        Instance instance = instances.get(bossId);
        if (instance == null) {
            return;
        }
        for (BossAbilityEffectDefinition effect : ability.effects()) {
            if (instance.sahur != null && instance.sahur.execute(effect.type(), runtime.targets())) {
                continue;
            }
            if (instance.encounter != null && instance.encounter.execute(effect, List.copyOf(runtime.targets()))) {
                continue;
            }
            for (UUID targetId : runtime.targets()) {
                Player player = Bukkit.getPlayer(targetId);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                switch (effect.type()) {
                    case DAMAGE -> player.damage(number(effect, "amount", 0.0), instance.boss);
                    case KNOCKBACK, PUSH -> push(instance.boss, player, number(effect, "strength", 1.0));
                    case PULL -> pull(instance.boss, player, number(effect, "strength", 1.0));
                    case MESSAGE -> player.sendMessage(LEGACY.deserialize(text(effect, "text", "")));
                    case SOUND -> player.playSound(
                        player.getLocation(), text(effect, "sound", "minecraft:block.beacon.power_select"),
                        SoundCategory.HOSTILE, 1.0f, 1.0f);
                    case PARTICLE -> player.getWorld().spawnParticle(
                        Particle.WITCH, player.getLocation().add(0.0, 1.0, 0.0), 20, 0.8, 1.0, 0.8, 0.05);
                    case PROJECTILE -> new BossProjectileTask(instance, instance.boss.getLocation().add(0, 2, 0), targetId, effect).start();
                    case PULL_CONTINUOUS -> new GravityWellTask(instance, player.getLocation(), effect).start();
                    case POTION_EFFECT_AREA -> new TimeDilationTask(instance, player.getLocation(), effect).start();
                    case SUMMON_CLONE -> {
                        int amount = (int) number(effect, "amount", 1.0);
                        for (int i = 0; i < amount; i++) {
                            spawnClone(player, effect);
                        }
                    }
                    case PARTICLE_SHAPE -> {
                        String shape = text(effect, "shape", "CIRCLE").toUpperCase(java.util.Locale.ROOT);
                        double r = number(effect, "radius", 3.0);
                        Location l = player.getLocation();
                        if (shape.equals("CIRCLE")) {
                            for (int i=0; i<36; i++) l.getWorld().spawnParticle(Particle.FLAME, l.clone().add(Math.cos(i*Math.PI/18)*r, 0, Math.sin(i*Math.PI/18)*r), 1, 0, 0, 0, 0);
                        } else if (shape.equals("SPIRAL")) {
                            for (int i=0; i<36; i++) l.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, l.clone().add(Math.cos(i*Math.PI/18)*(r - i*(r/36)), i*0.1, Math.sin(i*Math.PI/18)*(r - i*(r/36))), 1, 0, 0, 0, 0);
                        }
                    }
                    default -> {}
                }
            }
        }
    }

    private void spawnClone(Player target, BossAbilityEffectDefinition effect) {
        Warden clone = target.getWorld().spawn(
            target.getLocation().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2),
            Warden.class,
            warden -> {
                warden.setHealth(1.0);
                warden.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1.0);
                warden.customName(LEGACY.deserialize(definition.displayName() + " (Clone)"));
                warden.setCustomNameVisible(true);
                warden.setTarget(target);
            }
        );
        transients.own(clone, "boss clone " + clone.getUniqueId(), () -> {
            if (clone.isValid()) {
                clone.remove();
            }
        });

        BukkitTask[] delayedRemoval = new BukkitTask[1];
        Runnable removeClone = transients.guard(() -> {
            if (clone.isValid()) {
                clone.getWorld().spawnParticle(
                    Particle.CAMPFIRE_COSY_SMOKE, clone.getLocation(), 10);
            }
            transients.release(clone);
            if (delayedRemoval[0] != null) {
                transients.forget(delayedRemoval[0]);
            }
        });
        try {
            delayedRemoval[0] = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                removeClone,
                (long) (number(effect, "duration", 10.0) * 20)
            );
            BukkitTask task = delayedRemoval[0];
            transients.own(task, "boss clone removal " + clone.getUniqueId(), task::cancel);
        } catch (RuntimeException exception) {
            transients.release(clone);
            throw exception;
        }
    }

    private void remove(UUID bossId, MusicStopReason reason, boolean removeBoss) {
        Instance instance = instances.remove(bossId);
        if (instance != null) {
            // First, and guarded: a scripted controller always gets to remove what it owns.
            if (instance.encounter != null) instance.encounter.stop();
            instance.coordinator.cancelAll(Instant.now());
            presentation.defeatBoss(bossId);
            music.stopBoss(bossId, reason);
            animationService.removeBoss(bossId);
            if (instance.sahur != null) instance.sahur.close();
            announce(instance, "defeat", BossAnnouncementChannel.CHAT);
            ClientEntityPresentationRegistry.unregister(instance.boss.getUniqueId());
            animateModel(instance.model, "death");
            javaModels.close(instance.model);
            instance.text.remove();
            if (removeBoss && instance.boss.isValid()) {
                instance.boss.remove();
            }
        }
    }

    private void publishDamageLeaderboard(Instance instance) {
        if (instance == null) return;
        List<BossDamageStanding> standings = new BossDamageRanking().topThree(
            instance.damageByPlayer.entrySet().stream()
                .map(entry -> new BossDamageContribution(
                    entry.getKey(),
                    entry.getValue(),
                    instance.damageReachedAt.getOrDefault(entry.getKey(), Instant.MAX)
                ))
                .toList()
        );
        damageLeaderboard.accept(standings);
    }

    private void recordDamage(Instance instance, Player player, double damage) {
        instance.damageByPlayer.merge(player.getUniqueId(), damage, Double::sum);
        instance.damageReachedAt.put(player.getUniqueId(), Instant.now());
    }

    /** Same defeat path as a lethal hit: the Warden dies, so {@link #onDeath} publishes the ranking. */
    private void finishDefeat(Instance instance) {
        if (instance.boss.isValid() && !instance.boss.isDead()) {
            instance.boss.setHealth(0.0);
        }
        if (instances.get(instance.id) == instance) {
            // The death event did not fire (the entity was already gone): finish the defeat directly.
            publishDamageLeaderboard(instance);
            remove(instance.id, MusicStopReason.BOSS_DEFEATED, true);
        }
    }

    private void logEncounterFailure(UUID bossId, String stage, RuntimeException failure) {
        plugin.getLogger().log(java.util.logging.Level.WARNING,
            "El controlador del boss " + definition.id() + " (" + bossId + ") falló en " + stage, failure);
    }

    private List<Player> eligiblePlayers(Warden boss) {
        double radiusSquared = definition.audienceRadius() * definition.audienceRadius();
        return boss.getWorld().getPlayers().stream()
            .filter(Player::isOnline)
            .filter(player -> !player.isDead())
            .filter(player -> player.getGameMode() != org.bukkit.GameMode.SPECTATOR)
            .filter(player -> player.getLocation().distanceSquared(boss.getLocation()) <= radiusSquared)
            .toList();
    }

    private BossAudience audienceFor(Warden boss) {
        double radiusSquared = definition.audienceRadius() * definition.audienceRadius();
        Set<UUID> players = boss.getWorld().getPlayers().stream()
            .filter(player -> player.getLocation().distanceSquared(boss.getLocation()) <= radiusSquared)
            .map(Entity::getUniqueId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new BossAudience(players);
    }

    private List<BossTargetCandidate> candidates(Instance instance) {
        Warden boss = instance.boss;
        double radiusSquared = definition.audienceRadius() * definition.audienceRadius();
        return boss.getWorld().getPlayers().stream()
            .map(player -> new BossTargetCandidate(
                player.getUniqueId(),
                position(player.getLocation()),
                player.getHealth(),
                Optional.ofNullable(player.getAttribute(Attribute.MAX_HEALTH))
                    .map(AttributeInstance::getValue).orElse(20.0),
                instance.damageByPlayer.getOrDefault(player.getUniqueId(), 0.0),
                !player.isDead() && player.getLocation().distanceSquared(boss.getLocation()) <= radiusSquared
            ))
            .toList();
    }

    private BossView view(Instance instance) {
        UUID id = instance.id;
        return new BossView(
            id,
            definition.displayName(),
            new BossHealthView(id, instance.health.current(), instance.health.maximum()),
            new BossPhaseView(id, instance.phase, "Fase " + roman(instance.phase), instance.phase == 3 ? "ENFURECIDO" : ""),
            instance.phase == 3 ? "ENFURECIDO" : ""
        );
    }

    private void announce(Instance instance, String key, BossAnnouncementChannel channel) {
        if (instance == null) {
            return;
        }
        for (String line : definition.dialogue().getOrDefault(key, List.of())) {
            presentation.showAnnouncement(new BossAnnouncement(instance.id, channel, line));
        }
    }

    private BossAbilityDefinition ability(String id) {
        return definition.abilities().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private Optional<UUID> currentTarget(Warden boss) {
        return Optional.ofNullable(boss.getTarget()).map(Entity::getUniqueId);
    }

    private static Optional<Player> attackingPlayer(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return Optional.empty();
        }
        if (byEntity.getDamager() instanceof Player player) {
            return Optional.of(player);
        }
        if (byEntity.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof Player player) {
            return Optional.of(player);
        }
        return Optional.empty();
    }

    private void mark(Entity entity, UUID bossId) {
        entity.getPersistentDataContainer().set(bossIdKey, PersistentDataType.STRING, bossId.toString());
        entity.setPersistent(true);
    }

    private Optional<UUID> readBossId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(bossIdKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void animateModel(JavaModelHandle model, String definitionAnimationId) {
        javaModels.animate(model, modelAnimations.forDefinition(definitionAnimationId));
    }

    private static BossPosition position(Location location) {
        return new BossPosition(location.getX(), location.getY(), location.getZ());
    }

    private static double number(BossAbilityEffectDefinition effect, String key, double fallback) {
        Object value = effect.parameters().get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String text(BossAbilityEffectDefinition effect, String key, String fallback) {
        Object value = effect.parameters().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static void push(Warden boss, Player player, double strength) {
        Vector direction = player.getLocation().toVector().subtract(boss.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() > 0.0) {
            direction.normalize().multiply(strength).setY(0.45);
            player.setVelocity(direction);
        }
    }

    private static void pull(Warden boss, Player player, double strength) {
        Vector direction = boss.getLocation().toVector().subtract(player.getLocation().toVector());
        if (direction.lengthSquared() > 0.0) {
            player.setVelocity(direction.normalize().multiply(strength));
        }
    }

    private static String displayAbilityName(String id) {
        return switch (id) {
            case "void-pulse" -> "Pulso del Vacío";
            default -> id.replace('-', ' ');
        };
    }

    private static String roman(int phase) {
        return switch (phase) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }

    private final class Telegraphs implements BossTelegraphGateway {
        @Override
        public void begin(UUID bossId, BossAbilityDefinition ability, BossAbilityRuntime runtime) {
            Instance instance = instances.get(bossId);
            if (instance == null) {
                return;
            }
            Location center = instance.boss.getLocation();
            instance.boss.getWorld().playSound(
                center, ability.telegraph().warningSound(), SoundCategory.HOSTILE, 1.0f, 0.7f);
            drawCircle(center, ability.telegraph().radius());
        }

        @Override
        public void update(UUID bossId, BossAbilityDefinition ability, BossAbilityRuntime runtime, Duration remaining) {
            Instance instance = instances.get(bossId);
            if (instance != null) {
                drawCircle(instance.boss.getLocation(), ability.telegraph().radius());
            }
        }

        private void drawCircle(Location center, double radius) {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(146, 61, 255), 1.2f);
            for (int i = 0; i < 48; i++) {
                double angle = Math.PI * 2.0 * i / 48.0;
                center.getWorld().spawnParticle(
                    Particle.DUST,
                    center.getX() + Math.cos(angle) * radius,
                    center.getY() + 0.15,
                    center.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0, dust
                );
            }
        }
    }

    private static final class Instance {
        private final UUID id;
        private final Warden boss;
        private final JavaModelHandle model;
        private final TextDisplay text;
        private final BossAbilityCoordinator coordinator;
        private final BossHealthPool health;
        private final Map<UUID, Double> damageByPlayer;
        private final Map<UUID, Instant> damageReachedAt;
        private final PaperSahurCombatController sahur;
        private GuardedBossEncounter encounter;
        private int phase;

        private Instance(
            UUID id,
            Warden boss,
            JavaModelHandle model,
            TextDisplay text,
            BossAbilityCoordinator coordinator,
            BossHealthPool health,
            Map<UUID, Double> damageByPlayer,
            Map<UUID, Instant> damageReachedAt,
            PaperSahurCombatController sahur,
            int phase
        ) {
            this.id = id;
            this.boss = boss;
            this.model = model;
            this.text = text;
            this.coordinator = coordinator;
            this.health = health;
            this.damageByPlayer = damageByPlayer;
            this.damageReachedAt = damageReachedAt;
            this.sahur = sahur;
            this.phase = phase;
        }
    }

    private final class EncounterContext implements BossEncounterContext {
        private final Instance instance;

        private EncounterContext(Instance instance) {
            this.instance = instance;
        }

        private boolean active() {
            return instances.get(instance.id) == instance;
        }

        @Override
        public JavaPlugin plugin() {
            return plugin;
        }

        @Override
        public UUID bossInstanceId() {
            return instance.id;
        }

        @Override
        public AbyssGuardianDefinition definition() {
            return definition;
        }

        @Override
        public Warden boss() {
            return instance.boss;
        }

        @Override
        public JavaModelGateway models() {
            return javaModels;
        }

        @Override
        public JavaModelHandle model() {
            return instance.model;
        }

        @Override
        public void animate(String animation) {
            animateModel(instance.model, animation);
        }

        @Override
        public List<Player> players() {
            return eligiblePlayers(instance.boss);
        }

        @Override
        public boolean isBedrock(UUID playerId) {
            return platformGateway.resolvePlatform(playerId) == ClientPlatform.BEDROCK;
        }

        @Override
        public double health() {
            return instance.health.current();
        }

        @Override
        public double maximumHealth() {
            return instance.health.maximum();
        }

        @Override
        public void damage(double amount, Player attributedTo) {
            double admitted = BossEncounterRules.admittedDamage(amount);
            if (admitted <= 0.0 || !active()) {
                return;
            }
            instance.health.damage(admitted);
            if (attributedTo != null) {
                recordDamage(instance, attributedTo, admitted);
            }
            if (instance.health.destroyed()) {
                finishDefeat(instance);
            }
        }

        @Override
        public void heal(double amount) {
            if (Double.isFinite(amount) && amount > 0.0 && active() && !instance.health.destroyed()) {
                instance.health.heal(amount);
            }
        }

        @Override
        public void defeat() {
            if (!active()) {
                return;
            }
            instance.health.damage(instance.health.current());
            finishDefeat(instance);
        }
    }

    private final class BossProjectileTask implements Runnable {
        private final Instance instance;
        private final UUID targetId;
        private final String movementType;
        private final double speed;
        private final double damage;
        private final double homingTurnRate;
        private final int maxBounces;
        private final Particle particle;
        private final ItemDisplay display;
        private Location current;
        private Vector velocity;
        private int bounces;
        private int ticks;
        private BukkitTask task;

        public BossProjectileTask(Instance instance, Location origin, UUID targetId, BossAbilityEffectDefinition effect) {
            this.instance = instance;
            this.targetId = targetId;
            this.movementType = text(effect, "movement", "LINEAR").toUpperCase(java.util.Locale.ROOT);
            this.speed = number(effect, "speed", 1.0);
            this.damage = number(effect, "damage", 5.0);
            this.homingTurnRate = number(effect, "turn-rate", 0.15);
            this.maxBounces = (int) number(effect, "bounces", 0.0);
            Particle pType = Particle.SOUL_FIRE_FLAME;
            try { pType = Particle.valueOf(text(effect, "particle", "SOUL_FIRE_FLAME").toUpperCase(java.util.Locale.ROOT)); } catch (Exception ignored) {}
            this.particle = pType;

            this.current = origin.clone();

            Player p = Bukkit.getPlayer(targetId);
            if (p != null) {
                this.velocity = p.getLocation().add(0, 1, 0).toVector().subtract(this.current.toVector()).normalize().multiply(this.speed);
            } else {
                this.velocity = instance.boss.getLocation().getDirection().normalize().multiply(this.speed);
            }

            this.display = origin.getWorld().spawn(origin, ItemDisplay.class, d -> {
                mark(d, instance.id);
                d.setItemStack(new ItemStack(Material.GHAST_TEAR));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setGravity(false);
            });
            transients.own(
                display,
                "boss projectile display " + display.getUniqueId(),
                display::remove
            );
        }

        public void start() {
            task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, transients.guard(this), 1, 1);
            transients.own(task, "boss projectile task", task::cancel);
        }

        @Override
        public void run() {
            if (!instance.boss.isValid() || display.isDead() || ticks++ > 200) {
                cancel();
                return;
            }

            if (movementType.equals("GRAVITY")) {
                velocity.setY(velocity.getY() - 0.05);
            } else if (movementType.equals("TELEDIRECTED")) {
                Player p = Bukkit.getPlayer(targetId);
                if (p != null && p.isOnline() && p.getWorld().equals(current.getWorld())) {
                    Vector desired = p.getLocation().add(0, 1, 0).toVector().subtract(current.toVector()).normalize().multiply(speed);
                    velocity.add(desired.subtract(velocity).multiply(homingTurnRate)).normalize().multiply(speed);
                }
            }

            Location next = current.clone().add(velocity);

            if (next.getBlock().getType().isSolid()) {
                if (bounces < maxBounces) {
                    bounces++;
                    velocity.multiply(-1);
                    next = current.clone().add(velocity);
                } else {
                    current.getWorld().spawnParticle(Particle.EXPLOSION, current, 1);
                    cancel();
                    return;
                }
            }

            current = next;
            display.teleport(current);
            current.getWorld().spawnParticle(particle, current, 2, 0.1, 0.1, 0.1, 0.0);

            for (Player p : current.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(current) < 2.25) {
                    p.damage(damage, instance.boss);
                    current.getWorld().spawnParticle(Particle.EXPLOSION, current, 1);
                    cancel();
                    return;
                }
            }
        }

        private void cancel() {
            if (task != null) {
                transients.release(task);
                task = null;
            }
            transients.release(display);
        }
    }

    private final class GravityWellTask implements Runnable {
        private final Instance instance;
        private final Location center;
        private final double radius;
        private final double pullStrength;
        private final double damage;
        private int ticksLeft;
        private BukkitTask task;

        public GravityWellTask(Instance instance, Location center, BossAbilityEffectDefinition effect) {
            this.instance = instance;
            this.center = center;
            this.radius = number(effect, "radius", 10.0);
            this.pullStrength = number(effect, "pull-strength", 0.3);
            this.damage = number(effect, "damage", 5.0);
            this.ticksLeft = (int) (number(effect, "duration", 5.0) * 20);
        }

        public void start() {
            task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, transients.guard(this), 1, 1);
            transients.own(task, "gravity well task", task::cancel);
        }

        @Override
        public void run() {
            if (!instance.boss.isValid() || ticksLeft-- <= 0) {
                cancel();
                return;
            }

            center.getWorld().spawnParticle(Particle.PORTAL, center, 30, radius/2, 0.5, radius/2, 0.5);

            double rSq = radius * radius;
            for (Player p : center.getWorld().getPlayers()) {
                double distSq = p.getLocation().distanceSquared(center);
                if (distSq <= rSq) {
                    if (distSq < 2.25) {
                        if (ticksLeft % 10 == 0) p.damage(damage, instance.boss);
                    } else {
                        Vector pull = center.toVector().subtract(p.getLocation().toVector()).normalize().multiply(pullStrength);
                        p.setVelocity(p.getVelocity().add(pull));
                    }
                }
            }
        }

        private void cancel() {
            if (task != null) {
                transients.release(task);
                task = null;
            }
        }
    }

    private final class TimeDilationTask implements Runnable {
        private final Instance instance;
        private final Location center;
        private final double radius;
        private int ticksLeft;
        private BukkitTask task;

        public TimeDilationTask(Instance instance, Location center, BossAbilityEffectDefinition effect) {
            this.instance = instance;
            this.center = center;
            this.radius = number(effect, "radius", 15.0);
            this.ticksLeft = (int) (number(effect, "duration", 10.0) * 20);
        }

        public void start() {
            task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, transients.guard(this), 5, 5);
            transients.own(task, "time dilation task", task::cancel);
        }

        @Override
        public void run() {
            if (!instance.boss.isValid() || ticksLeft <= 0) {
                cancel();
                return;
            }
            ticksLeft -= 5;

            center.getWorld().spawnParticle(Particle.WITCH, center, 50, radius, 0.5, radius, 0.0);

            double rSq = radius * radius;
            for (Player p : center.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(center) <= rSq) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 20, 2));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 20, 2));
                }
            }
        }

        private void cancel() {
            if (task != null) {
                transients.release(task);
                task = null;
            }
        }
    }
}
