package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeApi;
import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeEngine;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@link TideframeBridge} over the module's own engine ({@link TideframeApi}, implemented by the engine this module
 * creates). With {@link TideframeEventRelay}, the only module classes that name engine types; the rest use fakes in tests.
 */
final class TideframeApiBridge implements TideframeBridge {
    private final TideframeApi api;

    TideframeApiBridge(TideframeApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public Set<String> screenIds() {
        return api.screenIds();
    }

    @Override
    public boolean isPackLoaded(Player player) {
        return api instanceof TideframeEngine engine ? engine.isOwnPackLoaded(player) : api.isPackLoaded(player);
    }

    @Override
    public CompletableFuture<Boolean> openWhenReady(Player player, String screenId) {
        return api.openWhenReady(player, screenId);
    }

    @Override
    public Optional<String> currentScreen(Player player) {
        return api.currentScreen(player);
    }

    @Override
    public void closeScreen(Player player) {
        api.closeScreen(player);
    }

    @Override
    public void setVariable(Player player, String name, String value) {
        api.setVariable(player, name, value);
    }

    @Override
    public void setList(Player player, String source, List<Map<String, String>> rows) {
        api.setList(player, source, rows);
    }
}
