package dev.linqfy.bigCasares.modules.mutango;

import kr.toxicity.model.api.BetterModel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Los golpes a los Mutangos y a Neguechen (que los protege, que los deja caidos, cuando los vence un jugador y cuando
 * se mueren), la armadura que tienen segun el nivel, las espinas y los mensajes de muerte de los jugadores que matan.
 */
final class MutangoCombat implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> ENVIRONMENT = EnumSet.of(
        EntityDamageEvent.DamageCause.FALL, EntityDamageEvent.DamageCause.SUFFOCATION,
        EntityDamageEvent.DamageCause.DROWNING, EntityDamageEvent.DamageCause.FIRE,
        EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.LAVA,
        EntityDamageEvent.DamageCause.HOT_FLOOR, EntityDamageEvent.DamageCause.CONTACT,
        EntityDamageEvent.DamageCause.CRAMMING, EntityDamageEvent.DamageCause.FREEZE,
        EntityDamageEvent.DamageCause.LIGHTNING, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);

    private final JavaPlugin plugin;
    private final MutangoKeys keys;
    private final MutangoFights fights;
    private final MutangoRewards rewards;
    private final MutangoShrine shrine;
    private final MutangoTasks tasks;

    MutangoCombat(JavaPlugin plugin, MutangoKeys keys, MutangoFights fights, MutangoRewards rewards,
                  MutangoShrine shrine, MutangoTasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.keys = keys;
        this.fights = fights;
        this.rewards = rewards;
        this.shrine = shrine;
    }

    /**
     * No se ahogan, no se caen, no se queman ni los lastiman otros mobs; los golpes de jugadores (tambien los que
     * llegan por la hitbox b_cuerpo de BetterModel) suenan a piedra.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!isMutango(event.getEntity())) {
            return;
        }
        if (shrine.isPart(event.getEntity())) {
            // al santuario no se lo lastima; pegarle al cubo tambien lo mueve
            event.setCancelled(true);
            if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player
                && shrine.isCube(event.getEntity())) {
                shrine.click(player, event.getEntity());
            }
            return;
        }
        boolean fromMob = event instanceof EntityDamageByEntityEvent byEntity && !fromPlayer(byEntity.getDamager());
        if (fromMob || ENVIRONMENT.contains(event.getCause())) {
            event.setCancelled(true);
            return;
        }
        Entity mutango = event.getEntity();
        MutangoFight fight = fights.fightOf(mutango);
        if (event instanceof EntityDamageByEntityEvent byEntity && attacker(byEntity.getDamager()) instanceof Player hitter) {
            if (fight.fighters.add(hitter.getUniqueId())) {
                fights.saveFighters(fight);
            }
        }
        protect(event, mutango);
        if (fight.phaseThree.owns(mutango.getUniqueId())) {
            if (mutango.getUniqueId().equals(fight.phaseThree.neguechen()) && !fight.phaseThree.dying()
                && mutango instanceof Mob neguechen && event.getFinalDamage() >= neguechen.getHealth()
                && !fight.phaseThree.isProtected(neguechen.getUniqueId())) {
                // la vencieron: no muere, frena y sube a decir sus ultimos versos
                event.setCancelled(true);
                defeatNeguechen(fight, neguechen);
                return;
            }
            if (fight.phaseThree.isProtected(mutango.getUniqueId())) {
                // mientras tiene al Sol y a la Luna (y sus particulas) a Neguechen no le podes pegar (al Sol y a la
                // Luna nunca)
                event.setCancelled(true);
                if (mutango.getUniqueId().equals(fight.phaseThree.neguechen())) {
                    mutango.getWorld().playSound(mutango.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.6f);
                }
                return;
            }
            mutango.getWorld().playSound(mutango.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.5f, 0.7f);
            Location body = mutango.getLocation().add(0, 3.5, 0);
            mutango.getWorld().spawnParticle(Particle.CRIT, body, 25, 1.0, 1.8, 1.0, 0.3);
            mutango.getWorld().spawnParticle(Particle.ENCHANTED_HIT, body, 15, 1.0, 1.8, 1.0, 0.2);
            return;
        }
        if (fight.battle.isProtected(mutango.getUniqueId())) {
            // apilandose no reciben dano
            event.setCancelled(true);
            mutango.getWorld().playSound(mutango.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.6f);
            return;
        }
        if (mutango instanceof Mob mob && event.getFinalDamage() >= mob.getHealth() && fight.battle.onLethal(mob)) {
            // en la fase 1 no se mueren: quedan caidos hasta que caen los tres
            event.setCancelled(true);
            return;
        }
        fight.battle.onDamaged(mutango.getUniqueId(), event.getFinalDamage());
        mutango.getWorld().playSound(mutango.getLocation(), Sound.BLOCK_STONE_HIT, 1.2f, 0.7f);
    }

    /**
     * Neguechen vencida: frena y sube un poco en el aire; salen los versos del final de la fase 3 y sus ultimas palabras
     * y despues se deshace en luz y deja las Botas de Neguechen y la Cruz Andina para reclamar.
     */
    private void defeatNeguechen(MutangoFight fight, Mob neguechen) {
        rewards.registerVictory(fight);
        neguechen.getPersistentDataContainer().remove(keys.phase);
        neguechen.setHealth(1.0);
        fight.phaseThree.beginDeath();
        // la barra se va ya: si no, queda vacia todo el rato que habla y parece que ya murio
        fights.bossBars().remove(neguechen.getUniqueId());
        Location where = neguechen.getLocation();
        fight.cinematic.endOfPhaseThree(where);
        fight.cinematic.farewell(where);
        UUID id = neguechen.getUniqueId();
        fight.cinematic.then(() -> {
            // si mientras hablaba la sacaron (/mutango quitar...), no deja nada
            if (!id.equals(fight.phaseThree.neguechen())) {
                return;
            }
            // se hunde por una grieta y cuando se cierra quedan los premios arriba
            fight.phaseThree.leave(ground -> dropRewards(fight, ground));
        });
    }

    /**
     * La pelea termino de verdad (se cerro la grieta por la que se fue Neguechen): les da los premios a quienes
     * pelearon y avisa en pantalla.
     */
    private void dropRewards(MutangoFight fight, Location ground) {
        rewards.deliverRewards(fight);
        ground.getWorld().playSound(ground, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
        fight.battle.announce(ground, "§6§l¡Derrotaste a Neguechen!",
            "§7Recibiste las §6Botas de Neguechen §7y la §6Cruz Andina", Sound.BLOCK_BEACON_DEACTIVATE);
    }

    /**
     * Si te mato un Mutango o Neguechen (o el Sol, la Luna, una mano o un animal que invoco), el mensaje de muerte es de
     * ellos (MutangoDeathMessages): en la pantalla de muerte lo lees tal cual y en el chat sale con tu nombre.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        Entity killer = byEntity.getDamager();
        var data = killer.getPersistentDataContainer();
        String model = data.get(keys.model, PersistentDataType.STRING);
        String message;
        int roll = ThreadLocalRandom.current().nextInt(1000);
        if (model != null && MutangoModels.MODELS.contains(model)) {
            message = MutangoDeathMessages.byMutango(MutangoBossBars.of(model).name(), roll);
        } else if (model != null || data.has(keys.summon, PersistentDataType.BYTE)) {
            message = MutangoDeathMessages.byNeguechen(roll); // Neguechen, el Sol, la Luna, las manos o sus animales
        } else {
            return;
        }
        // murio por el mega rayo de Warden de Neguechen: se metio en una zona protegida para que no lo ataque
        boolean cagon = byEntity.getDamageSource().getDamageType() == DamageType.SONIC_BOOM;
        if (cagon) {
            message = MutangoDeathMessages.COWARD;
        }
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        event.deathScreenMessageOverride(legacy.deserialize("§c" + message));
        event.deathMessage(legacy.deserialize("§c" + event.getEntity().getName() + "§7: §f" + message));
        if (cagon) {
            // y despues, un aviso para todo el servidor
            String name = event.getEntity().getName();
            tasks.next("coward-announcement", () ->
                plugin.getServer().broadcast(legacy.deserialize("§e" + MutangoDeathMessages.cowardAnnouncement(name))));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity mutango = event.getEntity();
        if (mutango.getPersistentDataContainer().has(keys.summon, PersistentDataType.BYTE)) {
            // los animales que invoca Neguechen no sueltan nada
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        if (!isMutango(mutango)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        UUID id = mutango.getUniqueId();
        MutangoFight fight = fights.fightOf(mutango);
        fights.bossBars().remove(id);
        fights.confirmDeath(id);
        fights.releaseTracker(id);
        if (id.equals(fight.phaseThree.neguechen())) {
            if (fight.phaseThree.dying()) {
                // la mataron de otra forma (/kill) mientras decia sus ultimas palabras: los premios quedan igual
                fight.phaseThree.defeated();
                Location ground = mutango.getLocation();
                ground.setY(MutangoGround.y(ground) + 1);
                dropRewards(fight, ground);
                return;
            }
            fight.phaseThree.defeated();
            fight.cinematic.endOfPhaseThree(mutango.getLocation());
            mutango.getWorld().playSound(mutango.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
            fight.battle.announce(mutango.getLocation(), "§6§l¡Derrotaste a Neguechen!", "§7Fin de la pelea",
                Sound.BLOCK_BEACON_DEACTIVATE);
            return;
        }
        if (!MutangoModels.MODELS.contains(mutango.getPersistentDataContainer().get(keys.model, PersistentDataType.STRING))) {
            return; // el Sol, la Luna o una mano: no son Lef, Lihuen ni Lican
        }
        mutango.getWorld().playSound(mutango.getLocation(), Sound.BLOCK_STONE_BREAK, 2f, 0.5f);
        mutango.getWorld().playSound(mutango.getLocation(), Sound.ENTITY_GOAT_SCREAMING_DEATH, 1.5f, 0.6f);
        String name = mutango.getCustomName() == null ? "Mutango" : mutango.getCustomName();
        // el ultimo es cuando ya no queda ninguno en la lista, tambien de los que no estan cargados
        boolean lastOne = fight.roster.isEmpty();
        // solo a los que estan cerca de la pelea (en un servidor publico no se avisa a todo el mundo)
        for (Player player : MutangoTargets.near(mutango.getLocation(), MutangoBattle.MESSAGE_RADIUS)) {
            player.sendMessage("§6" + name + " §7fue derrotado.");
            if (lastOne) {
                player.sendMessage("§a§l¡Derrotaste a Lef, Lihuen y Lican!");
            }
        }
        // cuando caen los tres en la fase 2, aparece Neguechen donde cayo el ultimo (2 segundos despues)
        if (lastOne && fight.battle.phase() == 2 && !fight.phaseThree.active()) {
            Location where = mutango.getLocation();
            fight.cinematic.endOfPhaseTwo(where);
            fight.starting = true;
            tasks.later("phase-three-start", () -> {
                fight.starting = false;
                // si mientras tanto la sacaron (/mutango quitar), no aparece
                if (fights.get(fight.id) == fight && !fight.phaseThree.active()) {
                    fights.startPhaseThree(fight, where);
                }
            }, 40L);
        }
    }

    /**
     * La armadura de su nivel: puntos de armadura, dureza y (con netherite) resistencia al empuje como un juego completo
     * de ese material. A los caballos tambien se les ve la armadura de caballo.
     */
    static void armor(LivingEntity entity, int level) {
        MutangoArmor.Tier tier = MutangoArmor.tier(level);
        if (tier == MutangoArmor.Tier.NONE) {
            return;
        }
        setAtLeast(entity, Attribute.ARMOR, tier.armor);
        setAtLeast(entity, Attribute.ARMOR_TOUGHNESS, tier.toughness);
        setAtLeast(entity, Attribute.KNOCKBACK_RESISTANCE, tier.knockbackResistance);
        if (entity instanceof Horse horse) {
            Material plate = Material.matchMaterial(MutangoArmor.horseArmor(level));
            horse.getInventory().setArmor(new ItemStack(
                plate == null ? Material.IRON_HORSE_ARMOR : plate));
        }
    }

    private static void setAtLeast(LivingEntity entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null && value > instance.getBaseValue()) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Proteccion (con netherite, desde la sexta victoria) que frena el golpe y Espinas (despues de Proteccion V) que se
     * lo devuelven a quien pego. Los puntos de armadura ya los cuenta Minecraft.
     */
    private void protect(EntityDamageEvent event, Entity mutango) {
        Integer level = mutango.getPersistentDataContainer().get(keys.level, PersistentDataType.INTEGER);
        if (level == null || level <= 0) {
            return;
        }
        event.setDamage(event.getDamage() * MutangoArmor.protectionMultiplier(level));
    }

    /**
     * Espinas: solo cuando el golpe le llego de verdad (no si lo freno el escudo, la pila o el golpe final) y solo a quien
     * pega cuerpo a cuerpo: una flecha no, como las espinas de Minecraft.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThorns(EntityDamageByEntityEvent event) {
        Entity mutango = event.getEntity();
        if (!isMutango(mutango) || shrine.isPart(mutango)) {
            return;
        }
        Integer level = mutango.getPersistentDataContainer().get(keys.level, PersistentDataType.INTEGER);
        if (level == null || ThreadLocalRandom.current().nextDouble() >= MutangoArmor.thornsChance(level)) {
            return;
        }
        if (event.getDamager() instanceof Player player) {
            MutangoDamage.hurt(player, 1 + ThreadLocalRandom.current().nextInt(4), mutango);
            player.getWorld().playSound(player.getLocation(), Sound.ENCHANT_THORNS_HIT, 1f, 1f);
        }
    }

    /** Quien pego de verdad (si fue una flecha, quien la tiro). */
    private static Entity attacker(Entity damager) {
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
            ? shooter : damager;
    }

    private boolean isMutango(Entity entity) {
        return entity.getPersistentDataContainer().has(keys.model, PersistentDataType.STRING);
    }

    private static boolean fromPlayer(Entity damager) {
        return damager instanceof Player
            || damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }

    /** Los bloques que revolean las ondas de las manos de Neguechen son de adorno: no se apoyan ni rompen nada. */
    @EventHandler(ignoreCancelled = true)
    public void onDebrisLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock debris
            && debris.getPersistentDataContainer().has(keys.debris, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            debris.remove();
        }
    }

    /** Sin ordenarlos, alimentarlos ni nada de lo que se le hace a una vaca. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        var data = event.getRightClicked().getPersistentDataContainer();
        if (data.has(keys.model, PersistentDataType.STRING) || data.has(keys.summon, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
        if (shrine.isCube(event.getRightClicked())) {
            shrine.click(event.getPlayer(), event.getRightClicked());
        }
    }
}
