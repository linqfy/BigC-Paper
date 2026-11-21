package dev.linqfy.bigCasares.modules.donpollos;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.linqfy.bigCasares.modules.donpollos.DonPollosBossSupport.*;

/**
 * Derrota del Don Pollo Boss: el golpe que lo mataria lo deja con 1 de vida, llora, dice sus frases y abre el menu
 * del premio (solo para quien le gano). Lleva la cuenta de las victorias de cada jugador.
 */
final class DonPollosBossDefeat implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Lo que dice cuando le ganas, una frase cada {@link #PHRASE_TICKS}. */
    private static final List<String> DEFEAT_PHRASES = DonPollosReward.DEFEAT_PHRASES;
    static final int PHRASE_TICKS = 60;
    private static final List<DonPollosBossPhase> PHASES = DonPollosBossPhase.defaults();

    private final JavaPlugin plugin;
    private final DonPollosSounds sounds;
    private final Map<UUID, DonPollosBoss> bosses;
    private final DonPollosBossMinions minions;
    private final DonPollosBossAttacks attacks;
    private final DonPollosCubeBattles cubes;
    /** Saca un boss del juego (lo maneja el manager). */
    private final java.util.function.Consumer<UUID> untrack;
    /** Avisa al manager que el nivel del boss subio (para reaplicar el dano cuerpo a cuerpo). */
    private final java.util.function.Consumer<DonPollosBoss> onLevelRaised;
    /** Cuantas veces le gano cada jugador al boss (guardado en el jugador). */
    private final NamespacedKey bossWinsKey;
    /** En el boss derrotado: UUID de quien le gano ("" si no se sabe). */
    private final NamespacedKey defeatedKey;
    /** En el boss derrotado: el premio ya se cobro (se guarda antes de entregarlo, para que un reinicio no deje cobrar de nuevo). */
    private final NamespacedKey claimedKey;
    /** Mismo guardado que usa el manager al invocar. */
    private final NamespacedKey difficultyKey;
    private final DonPollosClaimLedger ledger;
    /** Golpes recibidos por boss: la correccion de fase de 1 tick solo vale si no hubo otro golpe en el medio. */
    private final Map<UUID, Integer> hitSerial = new java.util.HashMap<>();
    private final DonPollosKfcBucket bucket;
    private final DonPollosRewardMenu rewardMenu;

    DonPollosBossDefeat(JavaPlugin plugin, DonPollosSounds sounds, Map<UUID, DonPollosBoss> bosses,
                        DonPollosBossMinions minions, DonPollosBossAttacks attacks, DonPollosCubeBattles cubes,
                        java.util.function.Consumer<UUID> untrack,
                        java.util.function.Consumer<DonPollosBoss> onLevelRaised) {
        this.plugin = plugin;
        this.onLevelRaised = onLevelRaised;
        this.sounds = sounds;
        this.bosses = bosses;
        this.minions = minions;
        this.attacks = attacks;
        this.cubes = cubes;
        this.untrack = untrack;
        this.bossWinsKey = new NamespacedKey(plugin, "boss_ganadas");
        this.defeatedKey = new NamespacedKey(plugin, "don_pollo_boss_derrotado");
        this.difficultyKey = new NamespacedKey(plugin, "boss_dificultad");
        this.claimedKey = new NamespacedKey(plugin, "don_pollo_boss_cobrado");
        this.ledger = new DonPollosClaimLedger(
            new java.io.File(plugin.getDataFolder(), "data/don-pollos/premios-cobrados.yml"), plugin.getLogger());
        this.bucket = new DonPollosKfcBucket(plugin);
        this.rewardMenu = new DonPollosRewardMenu(bucket, this::claimReward);
    }

    NamespacedKey winsKey() {
        return bossWinsKey;
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(rewardMenu, plugin);
        plugin.getServer().getPluginManager().registerEvents(bucket, plugin);
        bucket.start();
    }

    /** Cierra los menus del premio mientras su listener todavia los protege. */
    void closeMenus() {
        rewardMenu.closeAll();
    }

    void stop() {
        bucket.stop();
        closeMenus();
        org.bukkit.event.HandlerList.unregisterAll(rewardMenu);
        org.bukkit.event.HandlerList.unregisterAll(bucket);
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    /** Un Balde de KFC dado vuelta (el premio fijo), para /donpollo give balde. */
    ItemStack kfcBucket() {
        return bucket.create();
    }

    /** Un boss que ya estaba derrotado (se recargo el chunk o el servidor): sigue llorando con el menu listo. */
    void restore(DonPollosBoss boss) {
        Ravager ravager = boss.ravager;
        if (DonPollosBossClaims.restore(ravager.getPersistentDataContainer(), defeatedKey, claimedKey, ledger,
            ravager.getUniqueId()) == DonPollosBossClaims.Restore.CLAIMED) {
            // ya cobro el premio: no se ofrece otra vez, el boss se retira (la despedida es solo visual)
            untrack.accept(ravager.getUniqueId());
            ravager.remove();
            return;
        }
        String winner = ravager.getPersistentDataContainer().get(defeatedKey, PersistentDataType.STRING);
        if (winner != null) {
            long done = Bukkit.getCurrentTick() - (long) DEFEAT_PHRASES.size() * PHRASE_TICKS - 40;
            boss.defeat = new DonPollosBoss.Defeat(winner.isEmpty() ? null : UUID.fromString(winner), done);
            boss.defeat.phrase = DEFEAT_PHRASES.size();
            boss.defeat.menuOpened = true;
            ravager.setAI(false);
            showDefeatedBar(boss);
        }
    }

    /**
     * El golpe que lo mataria no lo mata: queda con 1 de vida, se te queda mirando y llora. Mientras llora no
     * le pasa nada (salvo /kill y el vacio).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        DonPollosBoss boss = bosses.get(event.getEntity().getUniqueId());
        if (boss == null) {
            return;
        }
        org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.KILL
            || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (boss.defeat != null) {
            event.setCancelled(true);
        } else if (boss.levitateEnd > 0 || boss.hoverAt != null) {
            // arriba, adentro de la esfera, no le pasa nada
            event.setCancelled(true);
            boss.ravager.getWorld().spawnParticle(Particle.DUST, boss.ravager.getLocation().add(0, 1.55 * scale(boss.ravager), 0),
                20, 1.5, 1.5, 1.5, 0, new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.6f), true);
            boss.ravager.getWorld().playSound(boss.ravager.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.8f);
        } else {
            raiseLevelIfStronger(boss, killer(event));
            // las fases no se saltean: un golpe frena en el umbral de la fase que sigue; solo desde la ultima se derrota
            double health = boss.ravager.getHealth();
            double max = boss.ravager.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            double finalDamage = event.getFinalDamage();
            DonPollosBossPhase activePhase = boss.phase;
            double allowed = DonPollosBossPhase.allowedDamage(PHASES, activePhase, max, health, finalDamage);
            if (allowed <= 0) {
                event.setCancelled(true);
                return;
            }
            UUID id = boss.ravager.getUniqueId();
            int serial = hitSerial.merge(id, 1, Integer::sum);
            if (allowed < finalDamage && finalDamage > 0) {
                event.setDamage(event.getDamage() * allowed / finalDamage);
                finalDamage = event.getFinalDamage();
                if (finalDamage < health) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                        () -> fixOvershoot(boss, id, serial, activePhase, max, health), 1L);
                }
            }
            Player hitter = killer(event);
            if (hitter != null && finalDamage > 0) {
                boss.damageByPlayer.merge(hitter.getUniqueId(), Math.min(finalDamage, health), Double::sum);
            }
            if (finalDamage >= health) {
                event.setCancelled(true);
                startDefeat(boss, hitter);
            }
        }
    }

    /** Un jugador con mas victorias que el nivel del boss lo sube a su nivel (queda guardado en el boss). */
    private void raiseLevelIfStronger(DonPollosBoss boss, Player attacker) {
        if (attacker == null) {
            return;
        }
        int level = DonPollosBossLevels.levelAfterHit(boss.difficulty.level(), bossWins(attacker));
        if (level > boss.difficulty.level()) {
            boss.difficulty = new DonPollosBossDifficulty(level);
            boss.ravager.getPersistentDataContainer().set(difficultyKey, PersistentDataType.INTEGER, level);
            onLevelRaised.accept(boss);
        }
    }

    /** Red de seguridad: si con armadura o absorcion el golpe freno mas abajo del umbral, devuelve la vida a su lugar. */
    private void fixOvershoot(DonPollosBoss boss, UUID id, int serial, DonPollosBossPhase activePhase,
                              double max, double healthBefore) {
        if (bosses.get(id) != boss || boss.defeat != null || !boss.ravager.isValid()
            || hitSerial.getOrDefault(id, 0) != serial) {
            return;
        }
        double fixed = DonPollosBossPhase.overshootFix(PHASES, activePhase, max, healthBefore,
            boss.ravager.getHealth());
        if (!Double.isNaN(fixed)) {
            boss.ravager.setHealth(Math.min(max, fixed));
        }
    }

    static Player killer(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player player) {
                return player;
            }
            if (byEntity.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        return null;
    }

    void startDefeat(DonPollosBoss boss, Player killer) {
        Ravager ravager = boss.ravager;
        long now = Bukkit.getCurrentTick();
        Player winner = killer != null ? killer : nearestPlayer(ravager.getLocation(), BAR_RADIUS);
        // corta todo lo que estuviera haciendo
        boss.levitateEnd = 0;
        boss.firingUntil = 0;
        boss.target = null;
        boss.beamStart = 0;
        boss.beamPoint = null;
        boss.jumpEnd = 0;
        attacks.setInflate(ravager, 0);
        ravager.setGravity(true);
        ravager.setVelocity(new Vector());
        ravager.setTarget(null);
        ravager.setAI(false);
        ravager.setHealth(1);
        minions.removeAll(boss.minions);
        cubes.endCube(boss);
        boss.defeat = new DonPollosBoss.Defeat(winner == null ? null : winner.getUniqueId(), now);
        if (winner != null) {
            int wins = bossWins(winner) + 1;
            winner.getPersistentDataContainer().set(bossWinsKey, PersistentDataType.INTEGER, wins);
            winner.sendMessage(LEGACY.deserialize("§6Ya le ganaste §f" + wins + "§6 " + (wins == 1 ? "vez" : "veces")
                + " a Don Pollo. §7La proxima vez que lo invoques va a ser mas fuerte."));
        }
        ravager.getPersistentDataContainer().set(defeatedKey, PersistentDataType.STRING,
            winner == null ? "" : winner.getUniqueId().toString());
        showDefeatedBar(boss);
        if (plugin instanceof dev.linqfy.bigCasares.BigCasares bigCasares) {
            bigCasares.awardBossSouls("don-pollo", "Don Pollo", boss.damageByPlayer);
        }
        ravager.getWorld().playSound(ravager.getLocation(), Sound.ENTITY_RAVAGER_HURT, 3f, 0.5f);
        plugin.getLogger().info("Don Pollo Boss derrotado por " + (winner == null ? "nadie" : winner.getName()));
    }

    void showDefeatedBar(DonPollosBoss boss) {
        boss.bar.name(LEGACY.deserialize("§b§lDON POLLO BOSS §8| §7derrotado"));
        boss.bar.color(BossBar.Color.BLUE);
    }

    /** Te mira, llora y dice las frases de a una; despues abre el menu del premio. */
    void tickDefeat(DonPollosBoss boss, long now) {
        Ravager ravager = boss.ravager;
        DonPollosBoss.Defeat defeat = boss.defeat;
        Player winner = defeat.winner == null ? null : Bukkit.getPlayer(defeat.winner);
        if (winner != null && winner.getWorld() == ravager.getWorld()) {
            faceTowards(ravager, winner.getLocation());
        }
        tears(ravager, now);
        if (defeat.claimed) {
            return;
        }
        int phrase = (int) ((now - defeat.start) / PHRASE_TICKS);
        if (phrase > defeat.phrase && phrase < DEFEAT_PHRASES.size()) {
            defeat.phrase = phrase;
            say(boss, DEFEAT_PHRASES.get(phrase));
        } else if (defeat.phrase < 0 && phrase == 0) {
            defeat.phrase = 0;
            say(boss, DEFEAT_PHRASES.getFirst());
        }
        if (!defeat.menuOpened && now - defeat.start >= (long) DEFEAT_PHRASES.size() * PHRASE_TICKS) {
            defeat.menuOpened = true;
            if (winner != null && winner.getWorld() == ravager.getWorld()
                && winner.getLocation().distanceSquared(ravager.getLocation()) <= 64 * 64) {
                rewardMenu.open(winner, ravager.getUniqueId());
                winner.sendMessage(LEGACY.deserialize(
                    "§7(Si cerras el menu, hacele click derecho al Don Pollo para volver a abrirlo)"));
            }
        }
    }

    void say(DonPollosBoss boss, String phrase) {
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
            LEGACY.deserialize("§b§o" + phrase), LEGACY.deserialize("§7Don Pollo"),
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                java.time.Duration.ofMillis(2200), java.time.Duration.ofMillis(500)));
        for (UUID viewer : boss.viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                player.showTitle(title);
                player.sendMessage(LEGACY.deserialize("§6Don Pollo: §b§o" + phrase));
                player.playSound(boss.ravager.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.5f, 0.5f);
            }
        }
    }

    /** Lagrimas que caen de los dos ojos. */
    void tears(Ravager ravager, long now) {
        World world = ravager.getWorld();
        for (int side : new int[]{1, -1}) {
            Location eye = eyes(ravager, side);
            // una lagrima de a ratos, alternando los ojos
            if ((now + (side > 0 ? 0 : 5)) % 10 == 0) {
                world.spawnParticle(Particle.FALLING_WATER, eye, 1, 0.02, 0.01, 0.02, 0, null, true);
            }
        }
        if (now % 120 == 0) {
            world.playSound(ravager.getLocation(), Sound.ENTITY_PANDA_CANT_BREED, 1f, 0.5f);
        }
    }

    /** Click derecho al Don Pollo derrotado: vuelve a abrir el menu del premio. */
    @EventHandler
    public void onInteractDefeated(PlayerInteractEntityEvent event) {
        DonPollosBoss boss = bosses.get(event.getRightClicked().getUniqueId());
        if (boss == null || boss.defeat == null) {
            return;
        }
        event.setCancelled(true);
        DonPollosBoss.Defeat defeat = boss.defeat;
        if (!defeat.menuOpened || defeat.claimed) {
            return;
        }
        Player player = event.getPlayer();
        if (!DonPollosReward.canClaim(defeat.winner, player.getUniqueId())) {
            player.sendMessage(LEGACY.deserialize(
                DonPollosReward.notYoursMessage(Bukkit.getOfflinePlayer(defeat.winner).getName())));
            player.playSound(boss.ravager.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }
        rewardMenu.open(player, boss.ravager.getUniqueId());
    }

    /** Al reclamar el premio: el Don Pollo se va llorando. Devuelve false si ya se reclamo. */
    boolean claimReward(Player player, UUID bossId) {
        DonPollosBoss boss = bosses.get(bossId);
        if (boss == null || boss.defeat == null || !boss.ravager.isValid()
            || !DonPollosBossClaims.claim(boss.defeat, player.getUniqueId(),
            boss.ravager.getPersistentDataContainer(), claimedKey, ledger, bossId)) {
            return false;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> farewell(boss, player), 20L);
        return true;
    }

    void farewell(DonPollosBoss boss, Player player) {
        Ravager ravager = boss.ravager;
        World world = ravager.getWorld();
        Location at = ravager.getLocation().add(0, 1.5, 0);
        sounds.play(ravager);
        world.spawnParticle(Particle.CLOUD, at, 60, 1.2, 1.2, 1.2, 0.05, null, true);
        world.spawnParticle(Particle.FALLING_WATER, at, 80, 1.2, 1.2, 1.2, 0, null, true);
        world.playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 2f, 0.6f);
        Bukkit.broadcast(LEGACY.deserialize("§6§l¡" + player.getName() + " le gano al Don Pollo Boss! §7Se fue llorando..."));
        untrack.accept(ravager.getUniqueId());
        ravager.remove();
    }

    /** Cuantas veces le gano este jugador al boss (guardado en el jugador). */
    int bossWins(Player player) {
        return DonPollosBossLevels.winsOf(player.getPersistentDataContainer(), bossWinsKey);
    }
}
