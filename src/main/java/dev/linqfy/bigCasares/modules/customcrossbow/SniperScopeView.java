package dev.linqfy.bigCasares.modules.customcrossbow;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

interface SniperScopeView {
    boolean show(Player player, EquipmentSlot hand);
    void using(Player player, EquipmentSlot hand, boolean loading);
    boolean isUsing(Player player, EquipmentSlot hand);
    void hide(Player player);
    void charge(Player player, int hearts);
}
