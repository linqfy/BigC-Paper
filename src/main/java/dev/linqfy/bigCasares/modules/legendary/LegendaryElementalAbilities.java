package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.BigCasares;

import net.kyori.adventure.text.Component;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;

/** Input and lifecycle only. Each weapon's mechanics live in its own class. */
final class LegendaryElementalAbilities implements Listener, Runnable {
    private final BigCasares plugin;
    private final LegendaryStateStore store;
    private final LegendaryPvpLevels levels;
    private final LegendaryModelAnimations animations;
    private final LegendaryElementalContext context;
    private final Predicate<Player> blocked;
    private final List<LegendaryAbilityCast> casts = new ArrayList<>();
    private boolean stopped;

    LegendaryElementalAbilities(
            BigCasares plugin,
            LegendaryStateStore store,
            LegendaryPvpLevels levels,
            LegendaryPvpProgression progression,
            LegendaryModelAnimations animations,
            Predicate<Player> blocked,
            BukkitRuntimeRegistrations registrations) {
        this.plugin = plugin;
        this.store = store;
        this.levels = levels;
        this.animations = animations;
        this.blocked = blocked;
        context =
                registrations.registerListener(
                        "legendary-elemental-damage",
                        new LegendaryElementalContext(plugin, progression));
    }

    private LegendaryWeapon held(Player player) {
        return plugin.getCustomItemRegistry()
                .resolveItemId(player.getInventory().getItemInMainHand())
                .flatMap(LegendaryWeapon::find)
                .orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || stopped || blocked.test(event.getPlayer()))
            return;
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!right && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        LegendaryWeapon weapon = held(player);
        if (weapon == null) return;
        var skill =
                LegendaryElementalSkill.click(
                        weapon,
                        levels.level(player.getUniqueId(), weapon),
                        player.isSneaking(),
                        right,
                        action == Action.LEFT_CLICK_AIR,
                        player.getPitch());
        if (skill == null) return;
        // Paper reports LEFT/RIGHT_CLICK_AIR as cancelled when vanilla has no item use.
        // Region protections are checked independently before every activation and hit.
        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY
                && event.getClickedBlock() != null) return;
        event.setCancelled(true);
        activate(player, skill);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        LegendaryWeapon weapon = held(player);
        if (!player.isSneaking()
                || stopped
                || blocked.test(player)
                || (weapon != LegendaryWeapon.THUNDER_SPEAR
                        && weapon != LegendaryWeapon.WINDWEAVER)) return;
        event.setCancelled(true);
        activate(
                player,
                weapon == LegendaryWeapon.THUNDER_SPEAR
                        ? LegendaryElementalSkill.WRATH_OF_ZEUS
                        : LegendaryElementalSkill.HEAVENS_FALL);
    }

    private void activate(Player player, LegendaryElementalSkill skill) {
        if (stopped || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return;
        for (var cast : casts)
            if (cast.owner.equals(player.getUniqueId())
                    && cast.skill == skill
                    && cast instanceof LegendaryWindAbilities.Flight
                    && !cast.actionDone) {
                cast.recast(player);
                return;
            }
        if (levels.level(player.getUniqueId(), skill.weapon) < skill.level) {
            player.sendActionBar(
                    Component.text(
                            "Necesitas nivel " + skill.level + " de " + skill.weapon.displayName));
            return;
        }
        long now = System.currentTimeMillis(),
                remaining = store.abilityRemaining(player.getUniqueId(), skill, now);
        if (remaining > 0) {
            player.sendActionBar(
                    Component.text(skill.id + " — " + ((remaining + 999) / 1000) + " s"));
            return;
        }
        if (casts.size() >= 32
                || !context.policy.allowed(
                        player, player.getLocation(), null, LegendaryProtectionEvent.Action.MOVE))
            return;
        if (skill.weapon == LegendaryWeapon.WINDWEAVER
                && casts.stream()
                        .anyMatch(c -> c.owner.equals(player.getUniqueId()) && c.fallProtected())) {
            player.sendActionBar(
                    Component.text("Termina el impulso actual antes de iniciar otro."));
            return;
        }
        var settings = skill.settings(plugin.getConfig());
        LegendaryAbilityCast cast =
                skill.weapon == LegendaryWeapon.THUNDER_SPEAR
                        ? LegendaryThunderAbilities.create(player, skill, settings, context)
                        : LegendaryWindAbilities.create(player, skill, settings, context);
        if (cast == null) {
            player.sendActionBar(
                    Component.text("Apunta a un objetivo o una superficie despejada."));
            return;
        }
        try {
            if (!store.beginAbilityCooldown(
                    player.getUniqueId(), skill, now, settings.cooldownMillis())) return;
            casts.add(cast);
            animations.ability(player, skill.weapon);
            player.getWorld()
                    .playSound(
                            player.getLocation(),
                            skill.weapon == LegendaryWeapon.THUNDER_SPEAR
                                    ? org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_IMPACT
                                    : org.bukkit.Sound.ENTITY_WIND_CHARGE_WIND_BURST,
                            .6f,
                            1.5f);
            player.sendActionBar(Component.text(skill.id + " — nivel " + skill.level));
        } catch (IOException failure) {
            cast.close(player);
            plugin.getLogger()
                    .log(Level.SEVERE, "No se pudo guardar el cooldown de " + skill.id, failure);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void melee(EntityDamageByEntityEvent event) {
        if (context.progression.inAbilityDamage()
                || event.getFinalDamage() <= 0
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player owner)
                || !(event.getEntity() instanceof LivingEntity target)
                || held(owner) != LegendaryWeapon.THUNDER_SPEAR
                || !context.allowed(owner, target)) return;
        for (var cast : casts)
            if (cast.owner.equals(owner.getUniqueId())
                    && cast instanceof LegendaryThunderAbilities.Mark mark) mark.melee(target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void movement(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;
        Player player = event.getPlayer();
        if (casts.stream()
                .noneMatch(c -> c.owner.equals(player.getUniqueId()) && c.fallProtected())) return;
        if (!context.pathAllowed(
                player, event.getFrom(), event.getTo(), LegendaryProtectionEvent.Action.MOVE)) {
            event.setTo(event.getFrom());
            player.setVelocity(new org.bukkit.util.Vector());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) return;
        for (var cast : new ArrayList<>(casts))
            if (cast.owner.equals(player.getUniqueId()) && cast.fallProtected()) {
                event.setCancelled(true);
                cast.fall(player, context);
                return;
            }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        clear(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void milk(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == org.bukkit.Material.MILK_BUCKET)
            casts.forEach(c -> c.effects.forget(event.getPlayer().getUniqueId()));
    }

    private void clear(Player player) {
        casts.removeIf(
                cast -> {
                    cast.targetGone(player.getUniqueId());
                    if (!cast.owner.equals(player.getUniqueId())) return false;
                    cast.close(player);
                    return true;
                });
        context.clean(player.getUniqueId());
    }

    public void run() {
        if (stopped) return;
        for (var cast : new ArrayList<>(casts)) {
            Player owner = plugin.getServer().getPlayer(cast.owner);
            try {
                cast.age++;
                if (owner == null
                        || !owner.isOnline()
                        || owner.isDead()
                        || !owner.getWorld().getUID().equals(cast.world)
                        || cast.age > cast.endAt) {
                    casts.remove(cast);
                    cast.close(owner);
                    continue;
                }
                cast.effects.prune(cast.age);
                cast.tick(owner, context);
            } catch (RuntimeException failure) {
                casts.remove(cast);
                cast.close(owner);
                plugin.getLogger()
                        .log(Level.SEVERE, "Habilidad cancelada: " + cast.skill.id, failure);
            }
        }
        context.prune();
    }

    void shutdown() {
        stopped = true;
        for (var cast : casts) cast.close(plugin.getServer().getPlayer(cast.owner));
        casts.clear();
        context.close();
    }
}
