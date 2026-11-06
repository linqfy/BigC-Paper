package dev.linqfy.bigCasares.modules.tideframe;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The part of Tideframe's API BigCasares uses, in BigCasares types. {@link TideframeApiBridge} delegates to the real
 * {@code dev.linqfy.bigCasares.modules.tideframe.engine.TideframeApi} service; tests use fakes. Nothing here installs screens or rebuilds Tideframe's pack.
 */
public interface TideframeBridge {
    Set<String> screenIds();

    /** True once the player loaded Tideframe's own resource pack (its UUID), not just any pack. */
    boolean isPackLoaded(Player player);

    /** Opens the screen once the pack is ready; false when it could not (pack refused, player gone, timeout). */
    CompletableFuture<Boolean> openWhenReady(Player player, String screenId);

    Optional<String> currentScreen(Player player);

    void closeScreen(Player player);

    void setVariable(Player player, String name, String value);

    void setList(Player player, String source, List<Map<String, String>> rows);
}
