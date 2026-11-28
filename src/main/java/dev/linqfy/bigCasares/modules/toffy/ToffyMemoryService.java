package dev.linqfy.bigCasares.modules.toffy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rules of the equipment memory, without Bukkit. Each check compares what the player has now with what is
 * remembered, slot by slot:
 * <ul>
 *     <li>something better than the remembered item replaces it; something exactly as strong keeps the remembered
 *     item but counts as seeing it now, so equal items never churn the saved file;</li>
 *     <li>an empty slot or something worse keeps the remembered item;</li>
 *     <li>a remembered item not seen for {@code forget-after-minutes} is forgotten.</li>
 * </ul>
 * Armor is compared by power; weapons and tools by their score against their own family, so a full mace and a full
 * sword compare as equals.
 */
public final class ToffyMemoryService {
    private final ToffyMemorySettings settings;

    public ToffyMemoryService(ToffyMemorySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public ToffyMemorySettings settings() {
        return settings;
    }

    /**
     * The memory after one check. {@code current} is the evaluation of what the player has right now.
     */
    public ToffyMemory observe(ToffyService service, ToffyMemory previous, ToffyResult current,
                                   long nowMillis) {
        if (!settings.enabled()) {
            return ToffyMemory.empty();
        }
        ToffyMemory memory = previous == null ? ToffyMemory.empty() : previous;
        Map<ToffyMemorySlot, ToffyMemory.Remembered> next = new EnumMap<>(ToffyMemorySlot.class);
        for (ToffyMemorySlot slot : ToffyMemorySlot.values()) {
            ToffyItem now = currentItem(slot, current);
            ToffyMemory.Remembered remembered = memory.get(slot);
            if (remembered != null && (expired(remembered, nowMillis)
                || strength(service, slot, remembered.item()) == Double.NEGATIVE_INFINITY)) {
                remembered = null;
            }
            if (now != null && strength(service, slot, now) >= 0.0) {
                double nowStrength = strength(service, slot, now);
                double rememberedStrength = remembered == null ? -1.0 : strength(service, slot, remembered.item());
                if (nowStrength > rememberedStrength) {
                    remembered = new ToffyMemory.Remembered(now, nowMillis);
                } else if (nowStrength == rememberedStrength) {
                    remembered = new ToffyMemory.Remembered(remembered.item(), nowMillis);
                }
            }
            if (remembered != null) {
                next.put(slot, remembered);
            }
        }
        return new ToffyMemory(next);
    }

    /**
     * What the real level is computed from: everything in {@code current} plus the remembered items as extra
     * candidates, so the real level is never below the current one.
     */
    public ToffyLoadout realLoadout(ToffyLoadout current, ToffyMemory memory) {
        ToffyLoadout.Builder builder = ToffyLoadout.builder();
        ToffyLoadout safeCurrent = current == null ? ToffyLoadout.empty() : current;
        safeCurrent.armor().forEach((slot, pieces) -> pieces.forEach(piece -> builder.armor(slot, piece)));
        safeCurrent.weapons().forEach(builder::weapon);
        safeCurrent.tools().forEach(builder::tool);
        if (settings.enabled() && memory != null) {
            memory.slots().forEach((slot, remembered) -> {
                if (slot.armorSlot() != null) {
                    builder.armor(slot.armorSlot(), remembered.item());
                } else if (slot == ToffyMemorySlot.WEAPON) {
                    builder.weapon(remembered.item());
                } else {
                    builder.tool(remembered.item());
                }
            });
        }
        return builder.build();
    }

    /**
     * The memory right after dying: forgotten when {@code forget-on-death}, unchanged otherwise.
     */
    public ToffyMemory afterDeath(ToffyMemory memory) {
        return settings.forgetOnDeath() || memory == null ? ToffyMemory.empty() : memory;
    }

    /**
     * {@code memory} without the items whose forget time has passed, for reading a memory without a check.
     */
    public ToffyMemory withoutExpired(ToffyMemory memory, long nowMillis) {
        if (!settings.enabled() || memory == null) {
            return ToffyMemory.empty();
        }
        Map<ToffyMemorySlot, ToffyMemory.Remembered> kept = new EnumMap<>(ToffyMemorySlot.class);
        memory.slots().forEach((slot, remembered) -> {
            if (!expired(remembered, nowMillis)) {
                kept.put(slot, remembered);
            }
        });
        return new ToffyMemory(kept);
    }

    private boolean expired(ToffyMemory.Remembered remembered, long nowMillis) {
        return settings.forgetAfterMillis() > 0L && nowMillis - remembered.lastSeenMillis() >= settings.forgetAfterMillis();
    }

    private static ToffyItem currentItem(ToffyMemorySlot slot, ToffyResult current) {
        if (current == null) {
            return null;
        }
        if (slot.armorSlot() != null) {
            return current.armorPieces().get(slot.armorSlot());
        }
        return slot == ToffyMemorySlot.WEAPON ? current.weapon().item() : current.tool().item();
    }

    private static double strength(ToffyService service, ToffyMemorySlot slot, ToffyItem item) {
        if (slot.armorSlot() != null) {
            return service.categoryOf(item) == ToffyItemCategory.ARMOR ? service.itemPower(item) : Double.NEGATIVE_INFINITY;
        }
        ToffyItemCategory expected = slot == ToffyMemorySlot.WEAPON
            ? ToffyItemCategory.WEAPON
            : ToffyItemCategory.TOOL;
        return service.categoryOf(item) == expected ? service.heldScore(item) : Double.NEGATIVE_INFINITY;
    }
}
