package dev.linqfy.bigCasares.modules.staffevents;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Max-health modifiers of the Lifesteal event. Event hearts live in their own modifier
 * tagged with the session id, so a crash or an offline player at the end never keeps
 * them; the permanent reward heart is a separate modifier that only grows.
 */
final class LifestealHearts {

    private final NamespacedKey eventKey;
    private final NamespacedKey permanentKey;
    private final NamespacedKey sessionKey;

    LifestealHearts(Plugin plugin) {
        this.eventKey = new NamespacedKey(plugin, "staff_event_lifesteal_hearts");
        this.permanentKey = new NamespacedKey(plugin, "staff_event_permanent_hearts");
        this.sessionKey = new NamespacedKey(plugin, "staff_event_lifesteal_session");
    }

    int hearts(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? 20.0 : attribute.getValue();
        return (int) Math.round(max / 2.0);
    }

    /** Adds (or with a negative value removes) event hearts and binds the player to this session. */
    void addEventHearts(Player player, int hearts, String session) {
        if (hearts == 0) {
            return;
        }
        player.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, session);
        setModifier(player, eventKey, modifierHearts(player, eventKey) + hearts);
        clampHealth(player);
    }

    /** Removes event hearts unless they belong to {@code activeSession}. */
    void clearUnlessSession(Player player, String activeSession) {
        String session = player.getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
        if (activeSession != null && activeSession.equals(session)) {
            return;
        }
        clear(player);
    }

    void clear(Player player) {
        player.getPersistentDataContainer().remove(sessionKey);
        setModifier(player, eventKey, 0);
        clampHealth(player);
    }

    int permanentHearts(Player player) {
        return modifierHearts(player, permanentKey);
    }

    void addPermanentHeart(Player player) {
        setModifier(player, permanentKey, permanentHearts(player) + 1);
    }

    private int modifierHearts(Player player, NamespacedKey key) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return 0;
        }
        AttributeModifier modifier = attribute.getModifier(key);
        return modifier == null ? 0 : (int) Math.round(modifier.getAmount() / 2.0);
    }

    private void setModifier(Player player, NamespacedKey key, int hearts) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(key);
        if (hearts != 0) {
            attribute.addModifier(new AttributeModifier(key, hearts * 2.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    private static void clampHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null && player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }
}
