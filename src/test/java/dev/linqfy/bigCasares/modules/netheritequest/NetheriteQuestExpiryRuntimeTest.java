package dev.linqfy.bigCasares.modules.netheritequest;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetheriteQuestExpiryRuntimeTest {
    @Test
    void checkingStatusExpiresOnlyTheViewersQuestsAndPersistsTheChange(@TempDir Path directory) throws Exception {
        UUID owner = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        NetheriteQuestSettings settings = NetheriteQuestSettings.defaults();
        NetheriteQuestBook book = new NetheriteQuestBook(settings);
        book.activate(owner, ArmorQuestPiece.HELMET, List.of("mutango"), 0, 0L);
        NetheriteQuestStore store = new NetheriteQuestStore(directory.resolve("quests.yml"));
        store.save(book);
        List<String> messages = new ArrayList<>();
        Player online = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> {
                if (method.getName().equals("sendMessage") && args != null && args.length == 1
                    && args[0] instanceof String message) {
                    messages.add(message);
                }
                return null;
            });
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Server.class},
            (proxy, method, args) -> method.getName().equals("getPlayer") && owner.equals(args[0]) ? online : null);
        NetheriteQuestRuntime runtime = new NetheriteQuestRuntime(server, Logger.getAnonymousLogger(), book,
            store, null, settings::durationMillis);

        runtime.status(viewer);
        assertTrue(book.hasActive(owner, ArmorQuestPiece.HELMET));
        assertTrue(messages.isEmpty());

        runtime.status(owner);
        runtime.saveIfDirty();

        assertEquals(1, messages.size());
        NetheriteQuestBook restored = new NetheriteQuestBook(settings);
        store.loadInto(restored);
        assertFalse(restored.hasActive(owner, ArmorQuestPiece.HELMET));
    }
}
