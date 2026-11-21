package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import org.bukkit.entity.Item;

import java.util.UUID;

/**
 * The paid guitar dropped at the feet of a player with a full inventory: a heart was paid for it, so it never
 * despawns or burns (lava, fire, cactus) and only its owner picks it up. The fight also turns mob pickup off.
 */
final class PachamamaPaidDrop {

    private PachamamaPaidDrop() {
    }

    static void protect(Item item, UUID owner) {
        item.setOwner(owner);
        item.setUnlimitedLifetime(true);
        item.setInvulnerable(true);
        item.setPersistent(true);
    }
}
