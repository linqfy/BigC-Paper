package dev.linqfy.bigCasares.modules.legendary;

public final class LegendaryControls {
    public enum Decision { NATIVE_ATTACK, ACTIVATE_SPECIAL, BLOCK_SPECIAL }
    private LegendaryControls() {}

    public static boolean denied(org.bukkit.event.player.PlayerInteractEvent event) {
        return event.useItemInHand() == org.bukkit.event.Event.Result.DENY
            || (event.getClickedBlock() != null && event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY);
    }

    public static Decision decide(LegendaryWeapon weapon, boolean rightClick, boolean mainHand, boolean denied) {
        if (weapon == null || !rightClick) return Decision.NATIVE_ATTACK;
        if (!weapon.isAvailable()) return Decision.BLOCK_SPECIAL;
        return mainHand && !denied ? Decision.ACTIVATE_SPECIAL : Decision.BLOCK_SPECIAL;
    }
}
