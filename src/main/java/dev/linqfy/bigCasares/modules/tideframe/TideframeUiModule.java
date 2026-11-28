package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.module.PluginModule;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Offers the two Java panels to players with the active pack and supplies titles for the chest fallback. */
public final class TideframeUiModule implements PluginModule {
    public static final String ID = "tideframe-ui";

    private final Predicate<UUID> packLoaded;
    private final Predicate<UUID> bedrock;
    private volatile boolean enabled;
    private NativeMenus menus;

    /**
     * @param packLoaded true when the player has BigCasares' Java resource pack loaded
     * @param bedrock    true for a Bedrock player (Geyser), who gets forms or plain titles
     */
    public TideframeUiModule(Predicate<UUID> packLoaded, Predicate<UUID> bedrock) {
        this.packLoaded = Objects.requireNonNull(packLoaded, "packLoaded");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
    }

    public NativeMenus attach(org.bukkit.plugin.Plugin owner) {
        menus = new NativeMenus(owner, packLoaded, bedrock);
        return menus;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void onEnable() {
        enabled = true;
        if (menus != null) menus.enable();
    }

    @Override
    public void onDisable() {
        enabled = false;
        if (menus != null) menus.disable();
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
