package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los premios de vencer a Neguechen:
 * <ul>
 *   <li><b>Botas de Neguechen</b>: puestas, en el aire podes saltar otra vez (apretando saltar de nuevo): un doble
 *   salto por vez en el aire, que se recarga al tocar el piso (MutangoBootsJump). El salto pone la caida en cero.</li>
 *   <li><b>Cruz Andina</b>: un totem que te salva igual que el comun pero te deja con mas vida y con mas efectos, que
 *   duran mas (MutangoCrossEffects).</li>
 * </ul>
 * El doble salto usa el permiso de volar: con las botas puestas (en supervivencia o aventura) el jugador "puede volar",
 * y el intento de volar (apretar saltar en el aire) se cancela y se cambia por el salto. Los intentos de volar de
 * verdad (muchos seguidos, o volando con las botas) no los resuelve el plugin: se le avisan al sistema de moderacion
 * de BigC (MutangoModeration).
 */
final class MutangoRelics implements Listener {

    static final String BOOTS = "botas_neguechen";
    static final String CROSS = "cruz_andina";
    /** El modelo de las botas puestas: assets/mutango/equipment/neguechen_boots.json. */
    static final String WORN_MODEL = "neguechen_boots";
    /** Archivos del pack de Bedrock con los iconos (en el jar, carpeta mutango/relics-bedrock/). */
    static final List<String> BEDROCK_PACK_FILES = List.of("manifest.json", "textures/item_texture.json",
        "textures/item/botas_neguechen.png", "textures/item/cruz_andina.png");
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 70), 1.2f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 250, 230), 1.0f);

    private final JavaPlugin plugin;
    /** A donde van los intentos de cheat (no los resuelve el plugin). */
    private final MutangoModeration moderation;
    /** Cuenta los intentos de volar que no dieron un doble salto. */
    private final MutangoFlightWatch flightWatch = new MutangoFlightWatch();
    /** Que premio es un item. */
    private final NamespacedKey relicKey;
    /** En el jugador: el permiso de volar se lo dieron las botas (asi se le saca aunque se reinicie el servidor). */
    private final NamespacedKey flightKey;
    private final Map<UUID, MutangoBootsJump> jumps = new HashMap<>();

    private final MutangoTasks tasks;

    MutangoRelics(JavaPlugin plugin, MutangoModeration moderation, MutangoTasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.moderation = moderation;
        this.relicKey = new NamespacedKey(plugin, "premio");
        this.flightKey = new NamespacedKey(plugin, "vuelo_botas");
    }

    // ---------------------------------------------------------------- los items

    ItemStack boots() {
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.fromRGB(150, 95, 45));
        }
        meta.setItemModel(new NamespacedKey("mutango", BOOTS));
        // puestas se ven con su propio modelo (resourcepack/java/assets/mutango/equipment/neguechen_boots.json)
        EquippableComponent worn = meta.getEquippable();
        worn.setSlot(EquipmentSlot.FEET);
        worn.setModel(new NamespacedKey("mutango", WORN_MODEL));
        meta.setEquippable(worn);
        meta.setDisplayName("§6§lBotas de Neguechen");
        meta.setLore(List.of(
            "§7Saltá en el aire para saltar otra vez.",
            "§8Lo que la Minche Mapu te devuelve."));
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
        meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, BOOTS);
        boots.setItemMeta(meta);
        return boots;
    }

    ItemStack cross() {
        ItemStack cross = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = cross.getItemMeta();
        meta.setItemModel(new NamespacedKey("mutango", CROSS));
        meta.setDisplayName("§6§lCruz Andina");
        meta.setLore(List.of(
            "§7Como un tótem, pero mejor:",
            "§7te salva con más vida y más efectos,",
            "§7que duran más."));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, CROSS);
        cross.setItemMeta(meta);
        return cross;
    }

    boolean is(ItemStack item, String relic) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return relic.equals(item.getItemMeta().getPersistentDataContainer().get(relicKey, PersistentDataType.STRING));
    }

    /**
     * Le da los dos premios al jugador, en el inventario; lo que no entra cae al piso a sus pies (con la misma proteccion
     * de siempre: no se rompe ni desaparece). Devuelve true si algo no entro en el inventario.
     */
    boolean give(Player player) {
        boolean overflow = false;
        for (ItemStack item : List.of(boots(), cross())) {
            for (ItemStack left : player.getInventory().addItem(item).values()) {
                overflow = true;
                Item dropped = player.getWorld().dropItem(player.getLocation(), left, drop -> {
                    drop.setGlowing(true);
                    drop.setUnlimitedLifetime(true);
                    drop.setInvulnerable(true);
                    drop.setCanMobPickup(false);
                    drop.setOwner(player.getUniqueId());
                });
                player.getWorld().spawnParticle(Particle.END_ROD, dropped.getLocation(), 20, 0.2, 1.0, 0.2, 0.05);
            }
        }
        Location body = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.DUST, body, 60, 0.8, 1.0, 0.8, 0, GOLD);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, body, 80, 0.6, 1.0, 0.6, 0.3);
        player.getWorld().playSound(body, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.6f);
        player.getWorld().playSound(body, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
        return overflow;
    }

    // ---------------------------------------------------------------- las botas

    /** Cada tick: a quien tiene las botas puestas le deja saltar en el aire (y se recarga al tocar el piso). */
    void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean usable = wearingBoots(player) && survival(player);
            boolean granted = player.getPersistentDataContainer().has(flightKey, PersistentDataType.BYTE);
            if (!usable) {
                if (granted) {
                    revokeFlight(player);
                }
                jumps.remove(player.getUniqueId());
                continue;
            }
            MutangoBootsJump jump = jumps.computeIfAbsent(player.getUniqueId(), id -> new MutangoBootsJump());
            if (player.isOnGround()) {
                jump.land();
            }
            // con las botas siempre "puede volar": apretar saltar en el aire llega como intento de volar, que se
            // cancela y se cambia por el doble salto (onToggleFlight).
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                player.getPersistentDataContainer().set(flightKey, PersistentDataType.BYTE, (byte) 1);
            }
            if (player.isFlying() && granted) {
                // volando de verdad con las botas puestas: no lo resuelve el plugin, se avisa a moderacion
                reportFlight(player, "Volando con las Botas de Neguechen puestas");
            }
        }
    }

    /** Un intento de volar con las botas que se cancelo: si son muchos seguidos, se le avisa a moderacion. */
    private void flightAttempt(Player player) {
        if (flightWatch.attempt(player.getUniqueId(), System.currentTimeMillis())) {
            reportFlight(player, "Intentos de volar seguidos con las Botas de Neguechen (limite "
                + MutangoFlightWatch.LIMIT + " en " + MutangoFlightWatch.WINDOW_MILLIS / 1000 + " s)");
        }
    }

    /** Se lo manda al sistema de moderacion de BigC (rule mutango-boots-flight, 30 puntos), con lo que se vio. */
    private void reportFlight(Player player, String evidence) {
        moderation.report(player, "mutango-boots-flight", 30, evidence);
    }

    /** Apretar saltar en el aire con las botas: en vez de volar, el doble salto. */
    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || !wearingBoots(player) || !survival(player)
            || !player.getPersistentDataContainer().has(flightKey, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        MutangoBootsJump jump = jumps.computeIfAbsent(player.getUniqueId(), id -> new MutangoBootsJump());
        if (player.isOnGround() || !jump.doubleJump()) {
            flightAttempt(player); // no dio un doble salto: cuenta como intento de volar
            return;
        }
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() > 1e-4) {
            forward.normalize().multiply(MutangoBootsJump.JUMP_FORWARD);
        }
        player.setVelocity(new Vector(forward.getX(), MutangoBootsJump.JUMP_UP, forward.getZ()));
        player.setFallDistance(0);
        Location feet = player.getLocation();
        player.getWorld().spawnParticle(Particle.CLOUD, feet, 12, 0.3, 0.05, 0.3, 0.05);
        player.getWorld().spawnParticle(Particle.DUST, feet, 15, 0.4, 0.1, 0.4, 0, GOLD);
        player.getWorld().playSound(feet, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.3f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        jumps.remove(event.getPlayer().getUniqueId());
        flightWatch.forget(event.getPlayer().getUniqueId());
    }

    /**
     * Al apagar el plugin nadie se queda con el permiso de volar de las botas (a los que estan en creativo se les saca
     * cuando vuelvan a supervivencia: les queda la marca).
     */
    void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getPersistentDataContainer().has(flightKey, PersistentDataType.BYTE)) {
                revokeFlight(player);
            }
        }
        jumps.clear();
    }

    private void revokeFlight(Player player) {
        if (survival(player)) {
            player.getPersistentDataContainer().remove(flightKey);
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private boolean wearingBoots(Player player) {
        return is(player.getInventory().getBoots(), BOOTS);
    }

    private static boolean survival(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    // ---------------------------------------------------------------- la cruz

    /**
     * La Cruz Andina te salva como un totem (Minecraft la gasta y hace la animacion) y despues el plugin te deja con mas
     * vida y le suma sus efectos, mas fuertes y mas largos que los del totem.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getHand() == null
            || !is(player.getInventory().getItem(event.getHand()), CROSS)) {
            return;
        }
        // un tick despues: Minecraft ya puso los efectos del totem y le saco la cruz de la mano
        tasks.next("cross-effects", () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            for (MutangoCrossEffects.Effect effect : MutangoCrossEffects.CROSS) {
                PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(effect.key()));
                if (type != null) {
                    player.addPotionEffect(new PotionEffect(type, effect.ticks(), effect.amplifier()));
                }
            }
            var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                player.setHealth(Math.max(player.getHealth(), MutangoCrossEffects.healthAfter(maxHealth.getValue())));
            }
            Location body = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.DUST, body, 60, 0.6, 1, 0.6, 0, GOLD);
            player.getWorld().spawnParticle(Particle.END_ROD, body, 30, 0.4, 1, 0.4, 0.08);
            player.getWorld().playSound(body, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 1.2f);
            player.sendMessage("§6La Cruz Andina te protegió.");
        });
    }
}
