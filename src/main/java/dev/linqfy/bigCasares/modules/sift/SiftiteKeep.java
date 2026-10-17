package dev.linqfy.bigCasares.modules.sift;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which items the Siftite recovery takes out of a death: the content ids of the mod's item tag
 * {@code the_sift:siftite_items} and the gamerule exception. Generic over the stack type so it is tested
 * without Bukkit stacks.
 *
 * <p>The mod's {@code SiftiteRecoveryManager.captureOnDeath} runs when a player's death is confirmed and
 * walks the inventory slots; with the {@code keepInventory} gamerule it does nothing, otherwise every
 * stack tagged {@code the_sift:siftite_items} is copied to the player's pending list and removed from the
 * inventory so that it is not dropped. The tag lists the Siftite ore block item, nugget, ingot, upgrade
 * smithing template, and the ten pieces of gear.</p>
 *
 * <p>Differences: stacks are taken from the death drops ({@code PlayerDeathEvent#getDrops()}), not from the
 * inventory slots, so a stack that vanilla would destroy (Curse of Vanishing) or that another plugin removed
 * from the drops is not kept, where the mod keeps whatever sits in the inventory; the inventory slots are
 * emptied as well, so that plugins reading the inventory (the tombstone module) do not take them. The
 * {@code keepInventory} check uses the event's flag, which starts as the gamerule.</p>
 */
final class SiftiteKeep {

    /** Content ids of {@code the_sift:siftite_items}, in the mod's order. */
    static final Set<String> PROTECTED_IDS = Set.of(
        "the_sift:siftslate_siftite_ore",
        "the_sift:siftite_nugget",
        "the_sift:siftite_ingot",
        "the_sift:siftite_upgrade_smithing_template",
        "the_sift:siftite_sword",
        "the_sift:siftite_shovel",
        "the_sift:siftite_pickaxe",
        "the_sift:siftite_axe",
        "the_sift:siftite_hoe",
        "the_sift:siftite_spear",
        "the_sift:siftite_helmet",
        "the_sift:siftite_chestplate",
        "the_sift:siftite_leggings",
        "the_sift:siftite_boots");

    private SiftiteKeep() {
    }

    static boolean isProtected(String contentId) {
        return contentId != null && PROTECTED_IDS.contains(contentId);
    }

    /** The drops to keep, in order; none when the inventory is kept anyway. Does not change {@code drops}. */
    static <T> List<T> select(List<T> drops, boolean keepInventory, Predicate<T> isKept) {
        if (keepInventory) {
            return List.of();
        }
        return drops.stream().filter(drop -> drop != null && isKept.test(drop)).toList();
    }

    /** Removes the kept drops from the drop list. */
    static <T> void remove(List<T> drops, Predicate<T> isKept) {
        drops.removeIf(drop -> drop != null && isKept.test(drop));
    }
}
