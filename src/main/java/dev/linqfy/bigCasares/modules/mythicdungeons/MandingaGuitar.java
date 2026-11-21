package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.BigCasares;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Level;

/**
 * The Mandinga guitar (catalog item {@code mandinga_guitar}, mechanic {@code mythic-guitar}) and Mandinga's deal.
 * The Pachamama encounter hands it out; {@link MandingaGuitarListener} makes it shoot notes. Both meet here: the
 * item factory, the item check, the "a note is hitting right now" marker that lets an encounter tell a note from a
 * melee hit, and the permanent heart of the deal (a {@code MAX_HEALTH} modifier with a fixed key plus a persistent
 * tag on the player, so it is taken only once and survives relog and death).
 */
public final class MandingaGuitar {

    public static final String ITEM_ID = "mandinga_guitar";
    public static final String MECHANIC = "mythic-guitar";
    /** Damage of one note (half hearts). */
    public static final double NOTE_DAMAGE = 12.0;

    /** The modifier and the tag of the deal; fixed so it is never applied twice. */
    public static final NamespacedKey PACT_KEY = new NamespacedKey("bigcasares", "mandinga_pact");
    /** Same key the item catalog writes on its stacks. */
    private static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("bigcasares", "item_id");

    private static int striking;

    private MandingaGuitar() {
    }

    // ----------------------------------------------------------------------- item

    /** Whether a stack is the guitar (by the catalog id tag, so a renamed or reconciled stack still counts). */
    public static boolean isGuitar(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        return ITEM_ID.equals(id);
    }

    /**
     * A new guitar from the item catalog of the running plugin, unbreakable. Empty when the catalog does not have
     * it (not seeded or removed by an admin): nobody must pay a heart for nothing.
     */
    public static Optional<ItemStack> create(JavaPlugin plugin) {
        if (!(plugin instanceof BigCasares bigCasares) || bigCasares.getCustomItemRegistry() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(unbreakable(bigCasares.getCustomItemRegistry().createItemStack(ITEM_ID, 1)));
        } catch (RuntimeException unavailable) {
            plugin.getLogger().log(Level.WARNING, "[Mazmorras] No se pudo crear la guitarra de Mandinga", unavailable);
            return Optional.empty();
        }
    }

    /** Marks a guitar stack unbreakable (the catalog has no such field); returns the same stack. */
    public static ItemStack unbreakable(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && !meta.isUnbreakable()) {
            meta.setUnbreakable(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ----------------------------------------------------------------------- note marker

    /**
     * Whether the damage being dealt right now comes from a guitar note. Notes hurt through a plain
     * {@code damage(amount, shooter)} call, so the damage event fires inside {@link #strike}; encounters read this
     * in their damage filter (server thread only).
     */
    public static boolean noteStriking() {
        return striking > 0;
    }

    /** Runs {@code hit} with {@link #noteStriking()} true. */
    static void strike(Runnable hit) {
        striking++;
        try {
            hit.run();
        } finally {
            striking--;
        }
    }

    // ----------------------------------------------------------------------- the deal

    public static boolean hasPaid(Player player) {
        return player.getPersistentDataContainer().has(PACT_KEY, PersistentDataType.BYTE) || hasModifier(player);
    }

    private static boolean hasModifier(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        return max != null && max.getModifier(PACT_KEY) != null;
    }

    private static double maxHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? Double.NaN : max.getValue();
    }

    /** What the deal would do for this player now. */
    public static MandingaGuitarPactRule.Outcome check(Player player) {
        return MandingaGuitarPactRule.decide(hasPaid(player), maxHealth(player));
    }

    /**
     * Takes the heart if the rule allows it: the persistent tag first, then the modifier, then the current health
     * is clamped to the new maximum. Returns the outcome; only {@code ACCEPT} changed anything.
     */
    public static MandingaGuitarPactRule.Outcome payHeart(Player player) {
        MandingaGuitarPactRule.Outcome outcome = check(player);
        if (outcome != MandingaGuitarPactRule.Outcome.ACCEPT) {
            return outcome;
        }
        player.getPersistentDataContainer().set(PACT_KEY, PersistentDataType.BYTE, (byte) 1);
        applyModifier(player);
        return outcome;
    }

    /** Puts the lost heart back on a player who paid and lost the modifier (see the rule); idempotent. */
    public static void restoreHeart(Player player) {
        boolean paid = player.getPersistentDataContainer().has(PACT_KEY, PersistentDataType.BYTE);
        if (MandingaGuitarPactRule.restore(paid, hasModifier(player), maxHealth(player))) {
            applyModifier(player);
        }
    }

    private static void applyModifier(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || max.getModifier(PACT_KEY) != null) {
            return;
        }
        max.addModifier(new AttributeModifier(PACT_KEY, -MandingaGuitarPactRule.HEART,
            AttributeModifier.Operation.ADD_NUMBER));
        if (player.getHealth() > max.getValue()) {
            player.setHealth(max.getValue());
        }
    }
}
