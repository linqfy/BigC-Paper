package dev.linqfy.bigCasares.testsupport;

import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A Bukkit player made of a few switchable states (online, ground, gliding...), recording the chat it receives. */
public final class FakePlayer {
    public final UUID id;
    public boolean online = true;
    public boolean dead;
    public boolean vehicle;
    public boolean gliding;
    public boolean onGround = true;
    public boolean flying;
    public int ping;
    public final List<String> messages = new ArrayList<>();
    public final Player player;

    public FakePlayer(String name) {
        this.id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getName" -> name;
            case "isOnline" -> online;
            case "isDead" -> dead;
            case "isInsideVehicle" -> vehicle;
            case "isGliding" -> gliding;
            case "isOnGround" -> onGround;
            case "isFlying" -> flying;
            case "getPing" -> ping;
            case "sendMessage" -> {
                if (args != null && args.length == 1 && args[0] instanceof String text) messages.add(text);
                yield null;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "FakePlayer " + name;
            default -> throw new AssertionError("Unexpected player call: " + method.getName());
        });
    }
}
