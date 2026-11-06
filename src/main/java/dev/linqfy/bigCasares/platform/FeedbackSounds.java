package dev.linqfy.bigCasares.platform;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Short vanilla sounds that confirm an operation to the player who made it: shop purchases and sales, and mission
 * rewards. Only that player hears them; Geyser plays the Bedrock equivalents.
 */
public final class FeedbackSounds {
    private FeedbackSounds() {
    }

    /** A purchase or sale that went through, or one that was refused. */
    public static void result(Player player, boolean success) {
        if (success) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
        }
    }

    /** Rewards paid. */
    public static void reward(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
    }
}
