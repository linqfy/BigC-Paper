package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyMemoryServiceTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private final ToffySettings settings = ToffySettingsLoaderTest.bundledSettings();
    private final ToffyService service = new ToffyService(settings);
    private final ToffyMemoryService rules = new ToffyMemoryService(
        new ToffyMemorySettings(true, HOUR, true, 20));

    private static ToffyLoadout.Builder netherite() {
        ToffyLoadout.Builder builder = ToffyLoadout.builder();
        for (ToffyArmorSlot slot : ToffyArmorSlot.values()) {
            builder.armor(slot, ToffyItem.of("netherite_" + slot.configKey()));
        }
        return builder;
    }

    private ToffyMemory observe(ToffyMemory previous, ToffyLoadout now, long time) {
        return rules.observe(service, previous, service.evaluate(now), time);
    }

    private double real(ToffyMemory memory, ToffyLoadout now) {
        return service.evaluate(rules.realLoadout(now, memory)).value();
    }

    private static String remembered(ToffyMemory memory, ToffyMemorySlot slot) {
        return memory.get(slot) == null ? null : memory.get(slot).item().key();
    }

    @Test
    void theFirstCheckRemembersWhatThePlayerHas() {
        ToffyMemory memory = observe(ToffyMemory.empty(), netherite()
            .weapon(ToffyItem.of("diamond_sword"))
            .tool(ToffyItem.of("iron_pickaxe")).build(), 1_000L);

        assertEquals("minecraft:netherite_helmet", remembered(memory, ToffyMemorySlot.HELMET));
        assertEquals("minecraft:netherite_boots", remembered(memory, ToffyMemorySlot.BOOTS));
        assertEquals("minecraft:diamond_sword", remembered(memory, ToffyMemorySlot.WEAPON));
        assertEquals("minecraft:iron_pickaxe", remembered(memory, ToffyMemorySlot.TOOL));
        assertEquals(1_000L, memory.get(ToffyMemorySlot.HELMET).lastSeenMillis());
    }

    @Test
    void takingAPieceOffKeepsItRememberedAndTheRealLevelUnchanged() {
        ToffyLoadout dressed = netherite().weapon(ToffyItem.of("diamond_sword")).build();
        ToffyMemory memory = observe(ToffyMemory.empty(), dressed, 0L);
        double before = service.evaluate(dressed).value();

        ToffyLoadout naked = ToffyLoadout.builder().build();
        ToffyMemory after = observe(memory, naked, MINUTE);

        assertEquals(0.0, service.evaluate(naked).value());
        assertEquals("minecraft:netherite_chestplate", remembered(after, ToffyMemorySlot.CHESTPLATE));
        assertEquals("minecraft:diamond_sword", remembered(after, ToffyMemorySlot.WEAPON));
        assertEquals(0L, after.get(ToffyMemorySlot.CHESTPLATE).lastSeenMillis());
        assertEquals(before, real(after, naked));
    }

    @Test
    void swappingForSomethingWorseKeepsTheBetterOne() {
        ToffyMemory memory = observe(ToffyMemory.empty(), netherite().build(), 0L);
        ToffyLoadout leather = ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("leather_helmet")).build();

        ToffyMemory after = observe(memory, leather, MINUTE);

        assertEquals("minecraft:netherite_helmet", remembered(after, ToffyMemorySlot.HELMET));
        assertEquals(service.evaluate(netherite().build()).value(), real(after, leather));
    }

    @Test
    void somethingBetterOrEqualReplacesTheRememberedItem() {
        ToffyMemory diamond = observe(ToffyMemory.empty(), ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("diamond_helmet")).build(), 0L);
        ToffyItem enchanted = ToffyItem.of("netherite_helmet", Map.of("protection", 4));

        ToffyMemory upgraded = observe(diamond, ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, enchanted).build(), MINUTE);
        ToffyMemory degraded = observe(upgraded, ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("netherite_helmet")).build(), 2 * MINUTE);

        assertEquals(enchanted, upgraded.get(ToffyMemorySlot.HELMET).item());
        assertEquals(MINUTE, upgraded.get(ToffyMemorySlot.HELMET).lastSeenMillis());
        assertEquals(enchanted, degraded.get(ToffyMemorySlot.HELMET).item());
    }

    @Test
    void weaponsAreComparedByTheirScoreAgainstTheirOwnFamily() {
        ToffyMemory sword = observe(ToffyMemory.empty(), ToffyLoadout.builder()
            .weapon(ToffyItem.of("netherite_sword")).build(), 0L);

        ToffyMemory plainMace = observe(sword, ToffyLoadout.builder()
            .weapon(ToffyItem.of("mace")).build(), MINUTE);
        ToffyMemory fullMace = observe(plainMace, ToffyLoadout.builder()
            .weapon(settings.maxReference().weapons().get("mace")).build(), 2 * MINUTE);

        assertEquals("minecraft:netherite_sword", remembered(plainMace, ToffyMemorySlot.WEAPON));
        assertEquals("minecraft:mace", remembered(fullMace, ToffyMemorySlot.WEAPON));
    }

    @Test
    void theBestToolIsRememberedLikeTheWeapon() {
        ToffyMemory memory = observe(ToffyMemory.empty(), ToffyLoadout.builder()
            .tool(ToffyItem.of("diamond_pickaxe")).build(), 0L);

        ToffyMemory after = observe(memory, ToffyLoadout.builder()
            .tool(ToffyItem.of("wooden_shovel")).build(), MINUTE);

        assertEquals("minecraft:diamond_pickaxe", remembered(after, ToffyMemorySlot.TOOL));
    }

    @Test
    void aPieceNotSeenForTheForgetTimeIsForgotten() {
        ToffyMemory memory = observe(ToffyMemory.empty(), netherite().build(), 0L);
        ToffyLoadout naked = ToffyLoadout.empty();

        ToffyMemory almost = observe(memory, naked, HOUR - 1);
        ToffyMemory forgotten = observe(almost, naked, HOUR);

        assertEquals("minecraft:netherite_helmet", remembered(almost, ToffyMemorySlot.HELMET));
        assertTrue(forgotten.isEmpty());
        assertEquals(0.0, real(forgotten, naked));
    }

    @Test
    void stillHavingThePieceKeepsItFresh() {
        ToffyMemory memory = observe(ToffyMemory.empty(), netherite().build(), 0L);
        ToffyMemory seen = observe(memory, netherite().build(), 50 * MINUTE);

        ToffyMemory later = observe(seen, ToffyLoadout.empty(), 100 * MINUTE);

        assertEquals("minecraft:netherite_helmet", remembered(later, ToffyMemorySlot.HELMET));
    }

    @Test
    void aForgetTimeOfZeroOnlyForgetsOnDeath() {
        ToffyMemoryService never = new ToffyMemoryService(new ToffyMemorySettings(true, 0L, true, 20));
        ToffyMemory memory = never.observe(service, ToffyMemory.empty(),
            service.evaluate(netherite().build()), 0L);

        ToffyMemory years = never.observe(service, memory, service.evaluate(ToffyLoadout.empty()),
            100L * 365 * 24 * HOUR);

        assertEquals("minecraft:netherite_helmet", remembered(years, ToffyMemorySlot.HELMET));
        assertTrue(never.afterDeath(years).isEmpty());
    }

    @Test
    void dyingForgetsUnlessDisabled() {
        ToffyMemory memory = observe(ToffyMemory.empty(), netherite().build(), 0L);
        ToffyMemoryService keep = new ToffyMemoryService(new ToffyMemorySettings(true, HOUR, false, 20));

        assertTrue(rules.afterDeath(memory).isEmpty());
        assertEquals(memory, keep.afterDeath(memory));
    }

    @Test
    void theRealLevelIsNeverBelowTheCurrentOne() {
        ToffyMemory leather = observe(ToffyMemory.empty(), ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("leather_helmet")).build(), 0L);
        ToffyLoadout now = netherite().weapon(ToffyItem.of("iron_sword")).build();

        assertEquals(service.evaluate(now).value(), real(leather, now));
    }

    @Test
    void itemsThatLostTheirValueAreForgotten() {
        ToffyMemory stale = new ToffyMemory(Map.of(ToffyMemorySlot.HELMET,
            new ToffyMemory.Remembered(ToffyItem.of("stick"), 0L)));

        assertNull(observe(stale, ToffyLoadout.empty(), MINUTE).get(ToffyMemorySlot.HELMET));
    }

    @Test
    void whenDisabledNothingIsRememberedAndTheRealLevelIsTheCurrentOne() {
        ToffyMemoryService off = new ToffyMemoryService(new ToffyMemorySettings(false, HOUR, true, 20));
        ToffyMemory memory = observe(ToffyMemory.empty(), netherite().build(), 0L);

        assertTrue(off.observe(service, memory, service.evaluate(ToffyLoadout.empty()), MINUTE).isEmpty());
        assertEquals(0.0, service.evaluate(off.realLoadout(ToffyLoadout.empty(), memory)).value());
    }

    @Test
    void piecesWorthLessThanNothingAreNeverRememberedAndNeverLowerTheRealLevel() {
        ToffyItem cursed = ToffyItem.of("leather_helmet", Map.of("binding_curse", 1));
        ToffyLoadout carryingCursed = ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, cursed).build();

        ToffyMemory memory = observe(ToffyMemory.empty(), carryingCursed, 0L);

        assertNull(memory.get(ToffyMemorySlot.HELMET));
        assertEquals(0.0, service.evaluate(carryingCursed).value());
        assertEquals(0.0, real(memory, ToffyLoadout.empty()));
    }

    @Test
    void anEquallyStrongItemKeepsTheRememberedOneButCountsAsSeen() {
        ToffyItem fullSword = settings.maxReference().weapons().get("sword");
        ToffyItem fullMace = settings.maxReference().weapons().get("mace");
        ToffyMemory sword = observe(ToffyMemory.empty(), ToffyLoadout.builder().weapon(fullSword).build(), 0L);

        ToffyMemory after = observe(sword, ToffyLoadout.builder().weapon(fullMace).build(), 30 * MINUTE);

        assertEquals(fullSword, after.get(ToffyMemorySlot.WEAPON).item());
        assertEquals(30 * MINUTE, after.get(ToffyMemorySlot.WEAPON).lastSeenMillis());
        assertTrue(sword.sameItems(after));
    }

    @Test
    void readingWithoutACheckLeavesOutForgottenItems() {
        ToffyMemory memory = observe(ToffyMemory.empty(), netherite().build(), 0L);

        assertEquals(memory, rules.withoutExpired(memory, HOUR - 1));
        assertTrue(rules.withoutExpired(memory, HOUR).isEmpty());
        assertTrue(new ToffyMemoryService(new ToffyMemorySettings(false, HOUR, true, 20))
            .withoutExpired(memory, 0L).isEmpty());
    }

    @Test
    void sameItemsIgnoresWhenTheyWereSeen() {
        ToffyMemory first = observe(ToffyMemory.empty(), netherite().build(), 0L);
        ToffyMemory second = observe(first, netherite().build(), MINUTE);

        assertTrue(first.sameItems(second));
        assertTrue(!first.sameItems(observe(first, netherite()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("netherite_helmet", Map.of("protection", 1)))
            .build(), MINUTE)));
    }
}
