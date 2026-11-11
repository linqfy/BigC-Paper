package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.module.PluginModule;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Draws the missions and shop chest menus over a background from BigCasares' own resource pack. Only the inventory
 * title changes: menus, clicks, purchases and claims stay in their modules. A Java player without the loaded pack,
 * or a Bedrock player, sees the plain title as before, and so does everyone while this module is off.
 */
public final class TideframeUiModule implements PluginModule {
    public static final String ID = "tideframe-ui";

    private final Predicate<UUID> packLoaded;
    private final Predicate<UUID> bedrock;
    private volatile boolean enabled;

    /**
     * @param packLoaded true when the player has BigCasares' Java resource pack loaded
     * @param bedrock    true for a Bedrock player (Geyser), who gets forms or plain titles
     */
    public TideframeUiModule(Predicate<UUID> packLoaded, Predicate<UUID> bedrock) {
        this.packLoaded = Objects.requireNonNull(packLoaded, "packLoaded");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    /** The title style for menus; asks for each player when the menu opens. */
    public MenuTitleStyle style() {
        return this::title;
    }

    MenuTitle title(Player player, MenuScreen screen, String text) {
        return title(player == null ? null : player.getUniqueId(), screen, text);
    }

    MenuTitle title(UUID playerId, MenuScreen screen, String text) {
        if (!enabled || playerId == null || screen == null || bedrock.test(playerId) || !packLoaded.test(playerId)) {
            return MenuTitle.plain(text);
        }
        return MenuTitle.drawn(screen, text);
    }
}
