package dev.linqfy.bigCasares.modules.mobscaling;

import dev.linqfy.bigCasares.modules.mutango.MutangoModule;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MobScalingListenerTest {

    private MobScalingListener listener;
    private MobScalingSettings settings;

    @BeforeEach
    void setUp() {
        settings = new MobScalingSettings(
                true,
                LocalDate.now().minusDays(10),
                0.05,
                1.1,
                1.0,
                0.0,
                0.0,
                0.0
        );
        listener = new MobScalingListener(null, settings, null);
    }

    @Test
    void vanillaMobIsNotAnEventMob() {
        TestEntity mob = new TestEntity();
        assertFalse(listener.isEventOrSystemMob(mob.proxy()));
    }

    @Test
    void mutangoEntitiesAreSystemMobsSoTheirHealthIsNotScaledAgain() {
        // la marca que pone el modulo mutango en todo lo que crea (peleadores, Neguechen, estatuas, animales)
        TestEntity mob = new TestEntity();
        mob.pdc.put("bigcasares:" + MutangoModule.SYSTEM_MOB_KEY, MutangoModule.SYSTEM_MOB_VALUE);
        assertTrue(listener.isEventOrSystemMob(mob.proxy()));
    }

    @Test
    void aSpawnedMutangoEntityIsNotScaledOrMutated() {
        TestEntity mutango = new TestEntity();
        mutango.pdc.put("bigcasares:" + MutangoModule.SYSTEM_MOB_KEY, MutangoModule.SYSTEM_MOB_VALUE);

        listener.onCreatureSpawn(new CreatureSpawnEvent(mutango.proxy(), CreatureSpawnEvent.SpawnReason.CUSTOM));

        assertFalse(mutango.pdc.containsKey("bigcasares:mob_scaled"), "Mutango entities must keep their own health");
    }

    @Test
    void theMutangoMarkIsNotJeremysValue() {
        // Jeremy solo toma como suyo el valor "jeremy"
        assertFalse("jeremy".equals(MutangoModule.SYSTEM_MOB_VALUE));
    }

    @Test
    void jeremyIsIdentifiedAsEventMob() {
        TestEntity mob = new TestEntity();
        mob.pdc.put("bigcasares:custom_entity", "jeremy");
        assertTrue(listener.isEventOrSystemMob(mob.proxy()));

        TestEntity targetMob = new TestEntity();
        targetMob.pdc.put("bigcasares:jeremy_target", UUID.randomUUID().toString());
        assertTrue(listener.isEventOrSystemMob(targetMob.proxy()));
    }

    @Test
    void bloodMoonMobIsIdentifiedAsEventMob() {
        TestEntity mob = new TestEntity();
        mob.pdc.put("bigcasares:blood_moon_extra", (byte) 1);
        assertTrue(listener.isEventOrSystemMob(mob.proxy()));

        TestEntity ownerMob = new TestEntity();
        ownerMob.pdc.put("bigcasares:blood_moon_owner", UUID.randomUUID().toString());
        assertTrue(listener.isEventOrSystemMob(ownerMob.proxy()));
    }

    @Test
    void acidRainMobIsIdentifiedAsEventMob() {
        TestEntity mob = new TestEntity();
        mob.pdc.put("bigcasares:acidrain_mob", (byte) 1);
        assertTrue(listener.isEventOrSystemMob(mob.proxy()));

        TestEntity typeMob = new TestEntity();
        typeMob.pdc.put("bigcasares:acidrain_mob_type", "CRAWLER");
        assertTrue(listener.isEventOrSystemMob(typeMob.proxy()));
    }

    @Test
    void airdropDefenderIsIdentifiedAsEventMob() {
        TestEntity mob = new TestEntity();
        mob.pdc.put("bigcasares:airdrop_defender", (byte) 1);
        assertTrue(listener.isEventOrSystemMob(mob.proxy()));

        TestEntity idMob = new TestEntity();
        idMob.pdc.put("bigcasares:airdrop_id", UUID.randomUUID().toString());
        assertTrue(listener.isEventOrSystemMob(idMob.proxy()));
    }

    @Test
    void pveBossAndSummonsAreIdentifiedAsEventMob() {
        TestEntity pveBoss = new TestEntity();
        pveBoss.pdc.put("bigcasares:pve_boss_id", UUID.randomUUID().toString());
        assertTrue(listener.isEventOrSystemMob(pveBoss.proxy()));

        TestEntity sahurBoss = new TestEntity();
        sahurBoss.pdc.put("bigcasares:sahur_boss_id", UUID.randomUUID().toString());
        assertTrue(listener.isEventOrSystemMob(sahurBoss.proxy()));

        TestEntity sahurBat = new TestEntity();
        sahurBat.pdc.put("bigcasares:sahur_bat_id", UUID.randomUUID().toString());
        assertTrue(listener.isEventOrSystemMob(sahurBat.proxy()));
    }

    @Test
    void shopAndNexusAreIdentifiedAsEventMob() {
        TestEntity shopNpc = new TestEntity();
        shopNpc.pdc.put("bigcasares:shop_npc_id", "blacksmith");
        assertTrue(listener.isEventOrSystemMob(shopNpc.proxy()));

        TestEntity nexus = new TestEntity();
        nexus.pdc.put("bigcasares:nexus_id", UUID.randomUUID().toString());
        assertTrue(listener.isEventOrSystemMob(nexus.proxy()));

        TestEntity tombstone = new TestEntity();
        tombstone.pdc.put("bigcasares:tombstone_id", UUID.randomUUID().toString());
        assertTrue(listener.isEventOrSystemMob(tombstone.proxy()));
    }

    @Test
    void onCreatureSpawnSkipsEventMobs() {
        TestEntity airdropMob = new TestEntity();
        airdropMob.pdc.put("bigcasares:airdrop_defender", (byte) 1);

        CreatureSpawnEvent event = new CreatureSpawnEvent(airdropMob.proxy(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        listener.onCreatureSpawn(event);

        assertFalse(airdropMob.pdc.containsKey("bigcasares:mob_scaled"), "Airdrop defender must not be scaled");

        TestEntity bloodMoonMob = new TestEntity();
        bloodMoonMob.pdc.put("bigcasares:blood_moon_extra", (byte) 1);

        CreatureSpawnEvent bmEvent = new CreatureSpawnEvent(bloodMoonMob.proxy(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        listener.onCreatureSpawn(bmEvent);

        assertFalse(bloodMoonMob.pdc.containsKey("bigcasares:mob_scaled"), "Blood moon mob must not be scaled");

        TestEntity acidRainMob = new TestEntity();
        acidRainMob.pdc.put("bigcasares:acidrain_mob", (byte) 1);

        CreatureSpawnEvent arEvent = new CreatureSpawnEvent(acidRainMob.proxy(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        listener.onCreatureSpawn(arEvent);

        assertFalse(acidRainMob.pdc.containsKey("bigcasares:mob_scaled"), "Acid rain mob must not be scaled");
    }

    private static class TestEntity {
        final Map<String, Object> pdc = new HashMap<>();

        Zombie proxy() {
            return (Zombie) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Zombie.class, Monster.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getPersistentDataContainer" -> persistentDataContainerProxy();
                        case "toString" -> "TestEntityZombie";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        PersistentDataContainer persistentDataContainerProxy() {
            return (PersistentDataContainer) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{PersistentDataContainer.class},
                    (proxy, method, args) -> {
                        NamespacedKey key = (NamespacedKey) args[0];
                        String keyStr = key.toString();
                        return switch (method.getName()) {
                            case "set" -> {
                                pdc.put(keyStr, args[2]);
                                yield null;
                            }
                            case "get" -> pdc.get(keyStr);
                            case "has" -> pdc.containsKey(keyStr);
                            default -> defaultValue(method.getReturnType());
                        };
                    }
            );
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) return false;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == double.class) return 0.0;
            if (returnType == float.class) return 0.0f;
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == char.class) return '\0';
            return null;
        }
    }
}
