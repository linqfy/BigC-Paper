package dev.linqfy.bigCasares.modules.cosmicniv;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicNivMemoryServiceTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private final CosmicNivSettings settings = CosmicNivSettingsLoaderTest.bundledSettings();
    private final CosmicNivService service = new CosmicNivService(settings);
    private final CosmicNivMemoryService rules = new CosmicNivMemoryService(
        new CosmicNivMemorySettings(true, HOUR, true, 20));

    private static CosmicNivLoadout.Builder netherite() {
        CosmicNivLoadout.Builder builder = CosmicNivLoadout.builder();
        for (CosmicNivArmorSlot slot : CosmicNivArmorSlot.values()) {
            builder.armor(slot, CosmicNivItem.of("netherite_" + slot.configKey()));
        }
        return builder;
    }

    private CosmicNivMemory observe(CosmicNivMemory previous, CosmicNivLoadout now, long time) {
        return rules.observe(service, previous, service.evaluate(now), time);
    }

    private double real(CosmicNivMemory memory, CosmicNivLoadout now) {
        return service.evaluate(rules.realLoadout(now, memory)).value();
    }

    private static String remembered(CosmicNivMemory memory, CosmicNivMemorySlot slot) {
        return memory.get(slot) == null ? null : memory.get(slot).item().key();
    }

    @Test
    void theFirstCheckRemembersWhatThePlayerHas() {
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), netherite()
            .weapon(CosmicNivItem.of("diamond_sword"))
            .tool(CosmicNivItem.of("iron_pickaxe")).build(), 1_000L);

        assertEquals("minecraft:netherite_helmet", remembered(memory, CosmicNivMemorySlot.HELMET));
        assertEquals("minecraft:netherite_boots", remembered(memory, CosmicNivMemorySlot.BOOTS));
        assertEquals("minecraft:diamond_sword", remembered(memory, CosmicNivMemorySlot.WEAPON));
        assertEquals("minecraft:iron_pickaxe", remembered(memory, CosmicNivMemorySlot.TOOL));
        assertEquals(1_000L, memory.get(CosmicNivMemorySlot.HELMET).lastSeenMillis());
    }

    @Test
    void takingAPieceOffKeepsItRememberedAndTheRealLevelUnchanged() {
        CosmicNivLoadout dressed = netherite().weapon(CosmicNivItem.of("diamond_sword")).build();
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), dressed, 0L);
        double before = service.evaluate(dressed).value();

        CosmicNivLoadout naked = CosmicNivLoadout.builder().build();
        CosmicNivMemory after = observe(memory, naked, MINUTE);

        assertEquals(0.0, service.evaluate(naked).value());
        assertEquals("minecraft:netherite_chestplate", remembered(after, CosmicNivMemorySlot.CHESTPLATE));
        assertEquals("minecraft:diamond_sword", remembered(after, CosmicNivMemorySlot.WEAPON));
        assertEquals(0L, after.get(CosmicNivMemorySlot.CHESTPLATE).lastSeenMillis());
        assertEquals(before, real(after, naked));
    }

    @Test
    void swappingForSomethingWorseKeepsTheBetterOne() {
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), netherite().build(), 0L);
        CosmicNivLoadout leather = CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("leather_helmet")).build();

        CosmicNivMemory after = observe(memory, leather, MINUTE);

        assertEquals("minecraft:netherite_helmet", remembered(after, CosmicNivMemorySlot.HELMET));
        assertEquals(service.evaluate(netherite().build()).value(), real(after, leather));
    }

    @Test
    void somethingBetterOrEqualReplacesTheRememberedItem() {
        CosmicNivMemory diamond = observe(CosmicNivMemory.empty(), CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("diamond_helmet")).build(), 0L);
        CosmicNivItem enchanted = CosmicNivItem.of("netherite_helmet", Map.of("protection", 4));

        CosmicNivMemory upgraded = observe(diamond, CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, enchanted).build(), MINUTE);
        CosmicNivMemory degraded = observe(upgraded, CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("netherite_helmet")).build(), 2 * MINUTE);

        assertEquals(enchanted, upgraded.get(CosmicNivMemorySlot.HELMET).item());
        assertEquals(MINUTE, upgraded.get(CosmicNivMemorySlot.HELMET).lastSeenMillis());
        assertEquals(enchanted, degraded.get(CosmicNivMemorySlot.HELMET).item());
    }

    @Test
    void weaponsAreComparedByTheirScoreAgainstTheirOwnFamily() {
        CosmicNivMemory sword = observe(CosmicNivMemory.empty(), CosmicNivLoadout.builder()
            .weapon(CosmicNivItem.of("netherite_sword")).build(), 0L);

        CosmicNivMemory plainMace = observe(sword, CosmicNivLoadout.builder()
            .weapon(CosmicNivItem.of("mace")).build(), MINUTE);
        CosmicNivMemory fullMace = observe(plainMace, CosmicNivLoadout.builder()
            .weapon(settings.maxReference().weapons().get("mace")).build(), 2 * MINUTE);

        assertEquals("minecraft:netherite_sword", remembered(plainMace, CosmicNivMemorySlot.WEAPON));
        assertEquals("minecraft:mace", remembered(fullMace, CosmicNivMemorySlot.WEAPON));
    }

    @Test
    void theBestToolIsRememberedLikeTheWeapon() {
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), CosmicNivLoadout.builder()
            .tool(CosmicNivItem.of("diamond_pickaxe")).build(), 0L);

        CosmicNivMemory after = observe(memory, CosmicNivLoadout.builder()
            .tool(CosmicNivItem.of("wooden_shovel")).build(), MINUTE);

        assertEquals("minecraft:diamond_pickaxe", remembered(after, CosmicNivMemorySlot.TOOL));
    }

    @Test
    void aPieceNotSeenForTheForgetTimeIsForgotten() {
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), netherite().build(), 0L);
        CosmicNivLoadout naked = CosmicNivLoadout.empty();

        CosmicNivMemory almost = observe(memory, naked, HOUR - 1);
        CosmicNivMemory forgotten = observe(almost, naked, HOUR);

        assertEquals("minecraft:netherite_helmet", remembered(almost, CosmicNivMemorySlot.HELMET));
        assertTrue(forgotten.isEmpty());
        assertEquals(0.0, real(forgotten, naked));
    }

    @Test
    void stillHavingThePieceKeepsItFresh() {
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), netherite().build(), 0L);
        CosmicNivMemory seen = observe(memory, netherite().build(), 50 * MINUTE);

        CosmicNivMemory later = observe(seen, CosmicNivLoadout.empty(), 100 * MINUTE);

        assertEquals("minecraft:netherite_helmet", remembered(later, CosmicNivMemorySlot.HELMET));
    }

    @Test
    void aForgetTimeOfZeroOnlyForgetsOnDeath() {
        CosmicNivMemoryService never = new CosmicNivMemoryService(new CosmicNivMemorySettings(true, 0L, true, 20));
        CosmicNivMemory memory = never.observe(service, CosmicNivMemory.empty(),
            service.evaluate(netherite().build()), 0L);

        CosmicNivMemory years = never.observe(service, memory, service.evaluate(CosmicNivLoadout.empty()),
            100L * 365 * 24 * HOUR);

        assertEquals("minecraft:netherite_helmet", remembered(years, CosmicNivMemorySlot.HELMET));
        assertTrue(never.afterDeath(years).isEmpty());
    }

    @Test
    void dyingForgetsUnlessDisabled() {
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), netherite().build(), 0L);
        CosmicNivMemoryService keep = new CosmicNivMemoryService(new CosmicNivMemorySettings(true, HOUR, false, 20));

        assertTrue(rules.afterDeath(memory).isEmpty());
        assertEquals(memory, keep.afterDeath(memory));
    }

    @Test
    void theRealLevelIsNeverBelowTheCurrentOne() {
        CosmicNivMemory leather = observe(CosmicNivMemory.empty(), CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("leather_helmet")).build(), 0L);
        CosmicNivLoadout now = netherite().weapon(CosmicNivItem.of("iron_sword")).build();

        assertEquals(service.evaluate(now).value(), real(leather, now));
    }

    @Test
    void itemsThatLostTheirValueAreForgotten() {
        CosmicNivMemory stale = new CosmicNivMemory(Map.of(CosmicNivMemorySlot.HELMET,
            new CosmicNivMemory.Remembered(CosmicNivItem.of("stick"), 0L)));

        assertNull(observe(stale, CosmicNivLoadout.empty(), MINUTE).get(CosmicNivMemorySlot.HELMET));
    }

    @Test
    void whenDisabledNothingIsRememberedAndTheRealLevelIsTheCurrentOne() {
        CosmicNivMemoryService off = new CosmicNivMemoryService(new CosmicNivMemorySettings(false, HOUR, true, 20));
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), netherite().build(), 0L);

        assertTrue(off.observe(service, memory, service.evaluate(CosmicNivLoadout.empty()), MINUTE).isEmpty());
        assertEquals(0.0, service.evaluate(off.realLoadout(CosmicNivLoadout.empty(), memory)).value());
    }

    @Test
    void piecesWorthLessThanNothingAreNeverRememberedAndNeverLowerTheRealLevel() {
        CosmicNivItem cursed = CosmicNivItem.of("leather_helmet", Map.of("binding_curse", 1));
        CosmicNivLoadout carryingCursed = CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, cursed).build();

        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), carryingCursed, 0L);

        assertNull(memory.get(CosmicNivMemorySlot.HELMET));
        assertEquals(0.0, service.evaluate(carryingCursed).value());
        assertEquals(0.0, real(memory, CosmicNivLoadout.empty()));
    }

    @Test
    void anEquallyStrongItemKeepsTheRememberedOneButCountsAsSeen() {
        CosmicNivItem fullSword = settings.maxReference().weapons().get("sword");
        CosmicNivItem fullMace = settings.maxReference().weapons().get("mace");
        CosmicNivMemory sword = observe(CosmicNivMemory.empty(), CosmicNivLoadout.builder().weapon(fullSword).build(), 0L);

        CosmicNivMemory after = observe(sword, CosmicNivLoadout.builder().weapon(fullMace).build(), 30 * MINUTE);

        assertEquals(fullSword, after.get(CosmicNivMemorySlot.WEAPON).item());
        assertEquals(30 * MINUTE, after.get(CosmicNivMemorySlot.WEAPON).lastSeenMillis());
        assertTrue(sword.sameItems(after));
    }

    @Test
    void readingWithoutACheckLeavesOutForgottenItems() {
        CosmicNivMemory memory = observe(CosmicNivMemory.empty(), netherite().build(), 0L);

        assertEquals(memory, rules.withoutExpired(memory, HOUR - 1));
        assertTrue(rules.withoutExpired(memory, HOUR).isEmpty());
        assertTrue(new CosmicNivMemoryService(new CosmicNivMemorySettings(false, HOUR, true, 20))
            .withoutExpired(memory, 0L).isEmpty());
    }

    @Test
    void sameItemsIgnoresWhenTheyWereSeen() {
        CosmicNivMemory first = observe(CosmicNivMemory.empty(), netherite().build(), 0L);
        CosmicNivMemory second = observe(first, netherite().build(), MINUTE);

        assertTrue(first.sameItems(second));
        assertTrue(!first.sameItems(observe(first, netherite()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("netherite_helmet", Map.of("protection", 1)))
            .build(), MINUTE)));
    }
}
