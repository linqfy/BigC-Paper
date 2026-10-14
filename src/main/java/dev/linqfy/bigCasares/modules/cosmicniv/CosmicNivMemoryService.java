package dev.linqfy.bigCasares.modules.cosmicniv;

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
public final class CosmicNivMemoryService {
    private final CosmicNivMemorySettings settings;

    public CosmicNivMemoryService(CosmicNivMemorySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public CosmicNivMemorySettings settings() {
        return settings;
    }

    /**
     * The memory after one check. {@code current} is the evaluation of what the player has right now.
     */
    public CosmicNivMemory observe(CosmicNivService service, CosmicNivMemory previous, CosmicNivResult current,
                                   long nowMillis) {
        if (!settings.enabled()) {
            return CosmicNivMemory.empty();
        }
        CosmicNivMemory memory = previous == null ? CosmicNivMemory.empty() : previous;
        Map<CosmicNivMemorySlot, CosmicNivMemory.Remembered> next = new EnumMap<>(CosmicNivMemorySlot.class);
        for (CosmicNivMemorySlot slot : CosmicNivMemorySlot.values()) {
            CosmicNivItem now = currentItem(slot, current);
            CosmicNivMemory.Remembered remembered = memory.get(slot);
            if (remembered != null && (expired(remembered, nowMillis)
                || strength(service, slot, remembered.item()) == Double.NEGATIVE_INFINITY)) {
                remembered = null;
            }
            if (now != null && strength(service, slot, now) >= 0.0) {
                double nowStrength = strength(service, slot, now);
                double rememberedStrength = remembered == null ? -1.0 : strength(service, slot, remembered.item());
                if (nowStrength > rememberedStrength) {
                    remembered = new CosmicNivMemory.Remembered(now, nowMillis);
                } else if (nowStrength == rememberedStrength) {
                    remembered = new CosmicNivMemory.Remembered(remembered.item(), nowMillis);
                }
            }
            if (remembered != null) {
                next.put(slot, remembered);
            }
        }
        return new CosmicNivMemory(next);
    }

    /**
     * What the real level is computed from: everything in {@code current} plus the remembered items as extra
     * candidates, so the real level is never below the current one.
     */
    public CosmicNivLoadout realLoadout(CosmicNivLoadout current, CosmicNivMemory memory) {
        CosmicNivLoadout.Builder builder = CosmicNivLoadout.builder();
        CosmicNivLoadout safeCurrent = current == null ? CosmicNivLoadout.empty() : current;
        safeCurrent.armor().forEach((slot, pieces) -> pieces.forEach(piece -> builder.armor(slot, piece)));
        safeCurrent.weapons().forEach(builder::weapon);
        safeCurrent.tools().forEach(builder::tool);
        if (settings.enabled() && memory != null) {
            memory.slots().forEach((slot, remembered) -> {
                if (slot.armorSlot() != null) {
                    builder.armor(slot.armorSlot(), remembered.item());
                } else if (slot == CosmicNivMemorySlot.WEAPON) {
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
    public CosmicNivMemory afterDeath(CosmicNivMemory memory) {
        return settings.forgetOnDeath() || memory == null ? CosmicNivMemory.empty() : memory;
    }

    /**
     * {@code memory} without the items whose forget time has passed, for reading a memory without a check.
     */
    public CosmicNivMemory withoutExpired(CosmicNivMemory memory, long nowMillis) {
        if (!settings.enabled() || memory == null) {
            return CosmicNivMemory.empty();
        }
        Map<CosmicNivMemorySlot, CosmicNivMemory.Remembered> kept = new EnumMap<>(CosmicNivMemorySlot.class);
        memory.slots().forEach((slot, remembered) -> {
            if (!expired(remembered, nowMillis)) {
                kept.put(slot, remembered);
            }
        });
        return new CosmicNivMemory(kept);
    }

    private boolean expired(CosmicNivMemory.Remembered remembered, long nowMillis) {
        return settings.forgetAfterMillis() > 0L && nowMillis - remembered.lastSeenMillis() >= settings.forgetAfterMillis();
    }

    private static CosmicNivItem currentItem(CosmicNivMemorySlot slot, CosmicNivResult current) {
        if (current == null) {
            return null;
        }
        if (slot.armorSlot() != null) {
            return current.armorPieces().get(slot.armorSlot());
        }
        return slot == CosmicNivMemorySlot.WEAPON ? current.weapon().item() : current.tool().item();
    }

    private static double strength(CosmicNivService service, CosmicNivMemorySlot slot, CosmicNivItem item) {
        if (slot.armorSlot() != null) {
            return service.categoryOf(item) == CosmicNivItemCategory.ARMOR ? service.itemPower(item) : Double.NEGATIVE_INFINITY;
        }
        CosmicNivItemCategory expected = slot == CosmicNivMemorySlot.WEAPON
            ? CosmicNivItemCategory.WEAPON
            : CosmicNivItemCategory.TOOL;
        return service.categoryOf(item) == expected ? service.heldScore(item) : Double.NEGATIVE_INFINITY;
    }
}
