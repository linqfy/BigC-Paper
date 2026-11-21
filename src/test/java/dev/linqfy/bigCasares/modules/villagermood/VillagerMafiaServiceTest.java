package dev.linqfy.bigCasares.modules.villagermood;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerMafiaServiceTest {

    private final UUID player = UUID.randomUUID();
    private final List<VillagerMafiaService.Source> boxed = List.of(source(), source(), source(),
        source(), source(), source());

    @Test
    void releasedVillagersAndRecentlyBoxedVillagersDoNotCount() {
        VillagerMafiaService service = service(0.0);
        List<VillagerMafiaService.Source> nearby = new ArrayList<>(boxed.subList(0, 5));
        nearby.add(new VillagerMafiaService.Source(UUID.randomUUID(), false, 42_000L));
        nearby.add(new VillagerMafiaService.Source(UUID.randomUUID(), true, 5_999L));
        FakeSpawner spawner = new FakeSpawner();
        assertFalse(service.consider(player, nearby, 0L, spawner));
        nearby.add(new VillagerMafiaService.Source(UUID.randomUUID(), true, 6_000L));
        assertTrue(service.consider(player, nearby, 0L, spawner));
    }

    @Test
    void requiresEnoughDistinctBoxedVillagersAndSuccessfulChanceRoll() {
        FakeSpawner spawner = new FakeSpawner();
        VillagerMafiaService service = service(0.0);
        assertFalse(service.consider(player, boxed.subList(0, 5), 0L, spawner));
        assertFalse(service.consider(player, List.of(boxed.getFirst(), boxed.getFirst(), boxed.getFirst(),
            boxed.getFirst(), boxed.getFirst(), boxed.getFirst()), 0L, spawner));
        assertFalse(service(0.99).consider(player, boxed, 0L, spawner));
        assertTrue(service.consider(player, boxed, 0L, spawner));
        assertEquals(1, spawner.crews.size());
        assertEquals(3, spawner.size);
        assertTrue(spawner.driveBy);
    }

    @Test
    void disablingMafiaOrDriveBysIsRespected() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("villager-mood.mafia.enabled", false);
        FakeSpawner spawner = new FakeSpawner();
        VillagerMafiaService disabled = new VillagerMafiaService(VillagerMafiaSettings.load(config), () -> 0.0);
        assertFalse(disabled.consider(player, boxed, 0L, spawner));
        config.set("villager-mood.mafia.enabled", true);
        config.set("villager-mood.mafia.drive-by-chance", 0.0);
        assertTrue(new VillagerMafiaService(VillagerMafiaSettings.load(config), () -> 0.0)
            .consider(player, boxed, 0L, spawner));
        assertFalse(spawner.driveBy);
    }

    @Test
    void sameFarmCannotSummonMultipleCrewsAndLogoutKeepsCooldown() {
        FakeSpawner spawner = new FakeSpawner();
        VillagerMafiaService service = service(0.0);
        assertTrue(service.consider(player, boxed, 0L, spawner));
        assertFalse(service.consider(UUID.randomUUID(), boxed, 1L, spawner));
        service.forget(player);
        assertTrue(spawner.crews.getFirst().removed);
        assertFalse(service.consider(player, boxed, 2L, spawner));
        assertFalse(service.consider(UUID.randomUUID(), boxed, 25_199L, spawner));
        assertTrue(service.consider(player, boxed, 25_200L, spawner));
    }

    @Test
    void failedSpawnDoesNotConsumeFarmCooldown() {
        VillagerMafiaService service = service(0.0);
        assertFalse(service.consider(player, boxed, 0L, (target, size, driveBy) -> null));
        assertTrue(service.consider(player, boxed, 1L, new FakeSpawner()));
    }

    @Test
    void expiresCrewsAndCleansDefeatedOrInvalidEncounters() {
        FakeSpawner spawner = new FakeSpawner();
        VillagerMafiaService service = service(0.0);
        service.consider(player, boxed, 10L, spawner);
        FakeCrew crew = spawner.crews.getFirst();
        service.tick(1_209L);
        assertFalse(crew.removed);
        service.tick(1_210L);
        assertTrue(crew.removed);

        VillagerMafiaService defeated = service(0.0);
        FakeSpawner second = new FakeSpawner();
        defeated.consider(player, boxed, 0L, second);
        second.crews.getFirst().active = false;
        defeated.tick(10L);
        assertTrue(second.crews.getFirst().removed);
    }

    @Test
    void capsConcurrentCrewsAndShutdownRemovesEveryCrew() {
        FakeSpawner spawner = new FakeSpawner();
        VillagerMafiaService service = service(0.0);
        for (int i = 0; i < 4; i++) {
            List<VillagerMafiaService.Source> farm = new ArrayList<>();
            for (int n = 0; n < 6; n++) {
                farm.add(source());
            }
            assertTrue(service.consider(UUID.randomUUID(), farm, 0L, spawner));
        }
        assertFalse(service.consider(player, boxed, 0L, spawner));
        service.shutdown();
        assertTrue(spawner.crews.stream().allMatch(crew -> crew.removed));
        assertTrue(service.consider(player, boxed, 0L, spawner));
    }

    @Test
    void loadsMafiaSettingsAndRejectsUnboundedOrNonFiniteValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("villager-mood.mafia.min-boxed-villagers", 9);
        config.set("villager-mood.mafia.damage", 6.0);
        assertEquals(9, VillagerMafiaSettings.load(config).minBoxedVillagers());
        assertEquals(6.0, VillagerMafiaSettings.load(config).damage());
        for (Object value : List.of(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 65.0)) {
            config.set("villager-mood.mafia.radius", value);
            assertThrows(IllegalArgumentException.class, () -> VillagerMafiaSettings.load(config));
        }
    }

    private static VillagerMafiaService service(double roll) {
        return new VillagerMafiaService(VillagerMafiaSettings.defaults(), () -> roll);
    }

    private static VillagerMafiaService.Source source() {
        return new VillagerMafiaService.Source(UUID.randomUUID(), true, 6_000L);
    }

    private static final class FakeSpawner implements VillagerMafiaService.Spawner {
        private final List<FakeCrew> crews = new ArrayList<>();
        private int size;
        private boolean driveBy;

        @Override
        public VillagerMafiaService.Crew spawn(UUID target, int size, boolean driveBy) {
            this.size = size;
            this.driveBy = driveBy;
            FakeCrew crew = new FakeCrew();
            crews.add(crew);
            return crew;
        }
    }

    private static final class FakeCrew implements VillagerMafiaService.Crew {
        private boolean active = true;
        private boolean removed;

        @Override
        public boolean tick(long now) {
            return active;
        }

        @Override
        public void remove() {
            removed = true;
        }
    }
}
