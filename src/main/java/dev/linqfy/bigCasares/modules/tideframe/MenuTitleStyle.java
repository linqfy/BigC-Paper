package dev.linqfy.bigCasares.modules.tideframe;

import org.bukkit.entity.Player;

/** Decides how a menu's title looks for one player: plain text, or with the drawn background. */
@FunctionalInterface
public interface MenuTitleStyle {
    MenuTitleStyle PLAIN = (player, screen, text) -> MenuTitle.plain(text);

    MenuTitle title(Player player, MenuScreen screen, String text);
}
