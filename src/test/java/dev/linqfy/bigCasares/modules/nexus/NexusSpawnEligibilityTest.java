package dev.linqfy.bigCasares.modules.nexus;

import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusSpawnEligibilityTest {
    @Test
    void hostileAwaitingWorldInsertionIsEligibleForSpawnBlocking() throws Exception {
        Zombie zombie = (Zombie) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Zombie.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "isValid" -> false;
                case "customName" -> null;
                default -> null;
            });
        var method = NexusModule.class.getDeclaredMethod("isRepellable", org.bukkit.entity.Entity.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(null, zombie));
    }
}
