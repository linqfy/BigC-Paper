package dev.linqfy.bigCasares.modules.villagermood;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Encounter ownership and cooldowns, independent of the server's entity implementation. */
final class VillagerMafiaService {

    record Source(UUID id, boolean confined, long confinedTicks) {
    }

    interface Crew {
        /** False when the target left, or the crew was defeated. */
        boolean tick(long now);

        void remove();
    }

    interface Spawner {
        /** Null means no safe place to spawn a crew. */
        Crew spawn(UUID player, int size, boolean driveBy);
    }

    private static final int MAX_CREWS = 4;
    private final VillagerMafiaSettings settings;
    private final DoubleSupplier random;
    private final Map<UUID, Encounter> encounters = new HashMap<>();
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private final Map<UUID, Long> villagerCooldowns = new HashMap<>();

    VillagerMafiaService(VillagerMafiaSettings settings, DoubleSupplier random) {
        this.settings = settings;
        this.random = random;
    }

    boolean consider(UUID player, List<Source> nearbyVillagers, long now, Spawner spawner) {
        playerCooldowns.values().removeIf(until -> until <= now);
        villagerCooldowns.values().removeIf(until -> until <= now);
        List<UUID> sources = nearbyVillagers.stream()
            .filter(source -> source.confined() && source.confinedTicks() >= settings.minConfinedTicks())
            .map(Source::id).distinct().toList();
        if (!settings.enabled() || encounters.size() >= MAX_CREWS || encounters.containsKey(player)
            || playerCooldowns.containsKey(player) || sources.size() < settings.minBoxedVillagers()
            || sources.stream().anyMatch(villagerCooldowns::containsKey)
            || random.getAsDouble() >= settings.chancePerCheck()) {
            return false;
        }
        Crew crew = spawner.spawn(player, settings.crewSize(), random.getAsDouble() < settings.driveByChance());
        if (crew == null) {
            return false;
        }
        long expires = now + settings.durationTicks();
        long cooldown = expires + settings.cooldownTicks();
        encounters.put(player, new Encounter(crew, expires));
        playerCooldowns.put(player, cooldown);
        sources.forEach(id -> villagerCooldowns.put(id, cooldown));
        return true;
    }

    void tick(long now) {
        Iterator<Encounter> iterator = encounters.values().iterator();
        while (iterator.hasNext()) {
            Encounter encounter = iterator.next();
            if (now >= encounter.expires || !encounter.crew.tick(now)) {
                iterator.remove();
                encounter.crew.remove();
            }
        }
    }

    void forget(UUID player) {
        Encounter encounter = encounters.remove(player);
        if (encounter != null) {
            encounter.crew.remove();
        }
        // Retain the source and player cooldowns: relogging must not summon another crew.
    }

    void shutdown() {
        encounters.values().forEach(encounter -> encounter.crew.remove());
        encounters.clear();
        playerCooldowns.clear();
        villagerCooldowns.clear();
    }

    private record Encounter(Crew crew, long expires) {
    }
}
