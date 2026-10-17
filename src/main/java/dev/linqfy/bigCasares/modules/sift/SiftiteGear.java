package dev.linqfy.bigCasares.modules.sift;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stats of the mod's Siftite gear ({@code ModItems.SIFTITE_*}), computed with vanilla's own formulas and
 * without any Bukkit type so they can be tested without a server. {@link SiftiteGearComponents} writes the
 * result onto the stacks; {@link SiftItems} registers one content item per constant, based on the netherite
 * counterpart.
 *
 * <p>The mod derives two materials from netherite. The tool material has netherite's durability + 470,
 * mining speed x 1.2, attack damage bonus + 1 and netherite's enchantability (15). The armour material has
 * netherite's durability factor + 4 (37 to 41), the same defence per slot (3/8/6/3), toughness 3 and
 * enchantability 15, and 0.15 knockback resistance per piece (netherite 0.1). Every piece is fire
 * resistant. Vanilla turns a tool material into components like this ({@code ToolMaterial}):</p>
 * <ul>
 *   <li>durability: the material's durability;</li>
 *   <li>attack damage: {@code baseline + attackDamageBonus} and attack speed: {@code baseline}, both
 *       main-hand {@code ADD_VALUE} modifiers on the player's base values, as {@code float} arithmetic;</li>
 *   <li>mining tools (pickaxe, axe, shovel, hoe): a tool component with two rules, no drops for the blocks of
 *       {@code #incorrect_for_netherite_tool} and, for the blocks of the tool's own {@code #mineable/...} tag,
 *       the material's speed with drops, with a default speed of 1 and one damage per block;</li>
 *   <li>swords keep the sword tool component, which does not depend on the material;</li>
 *   <li>spears use {@code attackDamageBonus} alone as attack damage, {@code 1 / swingSeconds - 4} as attack
 *       speed, and the kinetic weapon timings and thresholds the mod passes to {@code spear(...)}.</li>
 * </ul>
 * <p>Armour pieces get a durability of {@code unitDurability x factor} (helmet 11, chestplate 16, leggings
 * 15, boots 13) and armour, toughness and knockback resistance modifiers keyed {@code minecraft:armor.<slot>}
 * on their own slot.</p>
 *
 * <p>Differences: the pieces are netherite items with these components overridden, not new item types.
 * Anything a component does not cover is netherite's: the repair rule (any netherite ingot repairs a
 * Siftite piece, including Siftite ingots, which are netherite-ingot based; the mod accepts only Siftite
 * ingots), trimming, and the netherite sound for breaking. The vanilla constants of the netherite
 * material (2031, 9.0, 4.0, 15; 37 and 3.0) are written down here rather than read from the server. The
 * kinetic weapon thresholds of the spear are stored as the mod gives them (seconds converted to ticks,
 * speeds unchanged); the netherite spear's contact cooldown, forward movement and sounds are kept.</p>
 */
public enum SiftiteGear {
    SWORD("siftite_sword", "NETHERITE_SWORD", Kind.SWORD, 3.0F, -2.4F),
    SHOVEL("siftite_shovel", "NETHERITE_SHOVEL", Kind.SHOVEL, 1.5F, -3.0F),
    PICKAXE("siftite_pickaxe", "NETHERITE_PICKAXE", Kind.PICKAXE, 1.0F, -2.8F),
    AXE("siftite_axe", "NETHERITE_AXE", Kind.AXE, 5.0F, -3.0F),
    HOE("siftite_hoe", "NETHERITE_HOE", Kind.HOE, -4.0F, 0.0F),
    SPEAR("siftite_spear", "NETHERITE_SPEAR", Kind.SPEAR, 0.0F, 0.0F),
    HELMET("siftite_helmet", "NETHERITE_HELMET", Kind.HELMET, 0.0F, 0.0F),
    CHESTPLATE("siftite_chestplate", "NETHERITE_CHESTPLATE", Kind.CHESTPLATE, 0.0F, 0.0F),
    LEGGINGS("siftite_leggings", "NETHERITE_LEGGINGS", Kind.LEGGINGS, 0.0F, 0.0F),
    BOOTS("siftite_boots", "NETHERITE_BOOTS", Kind.BOOTS, 0.0F, 0.0F);

    // --- vanilla's netherite material, the base the mod derives from -------------------------------

    static final int NETHERITE_TOOL_DURABILITY = 2031;
    static final float NETHERITE_TOOL_SPEED = 9.0F;
    static final float NETHERITE_ATTACK_DAMAGE_BONUS = 4.0F;
    static final int NETHERITE_ARMOR_DURABILITY_FACTOR = 37;

    // --- the mod's materials ---------------------------------------------------------------------

    /** {@code SIFTITE_TOOL_MATERIAL.durability()}: netherite + 470. */
    public static final int TOOL_DURABILITY = NETHERITE_TOOL_DURABILITY + 470;
    /** {@code SIFTITE_TOOL_MATERIAL.speed()}: netherite x 1.2 (float arithmetic, as in the mod). */
    public static final float TOOL_SPEED = NETHERITE_TOOL_SPEED * 1.2F;
    /** {@code SIFTITE_TOOL_MATERIAL.attackDamageBonus()}: netherite + 1. */
    public static final float TOOL_ATTACK_DAMAGE_BONUS = NETHERITE_ATTACK_DAMAGE_BONUS + 1.0F;
    /** Enchantment value of both materials (netherite's). */
    public static final int ENCHANTMENT_VALUE = 15;
    /** {@code SIFTITE_ARMOR_MATERIAL.durability()}: the factor armour pieces multiply by their unit durability. */
    public static final int ARMOR_DURABILITY_FACTOR = NETHERITE_ARMOR_DURABILITY_FACTOR + 4;
    public static final float ARMOR_TOUGHNESS = 3.0F;
    public static final float ARMOR_KNOCKBACK_RESISTANCE = 0.15F;

    /** The equipment asset every armour piece renders with ({@code ModItems.SIFTITE_EQUIPMENT_ASSET}). */
    public static final String EQUIPMENT_ASSET = "the_sift:siftite";
    /** {@code ModSounds.SIFTITE_ARMOR_EQUIP}; the pack maps it to netherite's equip sound. */
    public static final String EQUIP_SOUND = "the_sift:item.siftite_armor.equip";
    /** Block tag of blocks a netherite-tier tool does not drop. */
    public static final String INCORRECT_BLOCKS_TAG = "minecraft:incorrect_for_netherite_tool";
    public static final String BASE_ATTACK_DAMAGE_KEY = "minecraft:base_attack_damage";
    public static final String BASE_ATTACK_SPEED_KEY = "minecraft:base_attack_speed";

    /** The mod's {@code spear(SIFTITE_TOOL_MATERIAL, 1.25, 1.2, 0.4, 2.5, 9.0, 5.5, 5.1, 8.75, 4.6)}. */
    public static final SpearStats SPEAR_STATS = new SpearStats(1.25F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F);

    /** What a piece is. */
    public enum Kind {
        SWORD, SHOVEL, PICKAXE, AXE, HOE, SPEAR, HELMET, CHESTPLATE, LEGGINGS, BOOTS;

        public boolean isArmor() {
            return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
        }
    }

    /** Attributes the gear modifies. */
    public enum Attr {
        ATTACK_DAMAGE, ATTACK_SPEED, ARMOR, ARMOR_TOUGHNESS, KNOCKBACK_RESISTANCE
    }

    /** Equipment slot groups a modifier applies in. */
    public enum Slot {
        MAINHAND, HEAD, CHEST, LEGS, FEET
    }

    /** An {@code ADD_VALUE} attribute modifier; {@code key} is the modifier id. */
    public record Modifier(Attr attribute, String key, double amount, Slot slot) {
    }

    /**
     * One entry of a tool component. {@code speed} is null for rules that only decide drops;
     * {@code correctForDrops} says whether the blocks drop with this tool.
     */
    public record ToolRule(String blockTag, Float speed, boolean correctForDrops) {
    }

    /**
     * The arguments of vanilla's {@code Item.Properties.spear}: swing and charge times in seconds and the
     * speed thresholds (blocks per second) of the dismount, knockback and damage effects of a charged lunge.
     */
    public record SpearStats(
        float swingSeconds, float damageMultiplier, float delaySeconds,
        float dismountSeconds, float dismountMinSpeed,
        float knockbackSeconds, float knockbackMinSpeed,
        float damageSeconds, float damageMinRelativeSpeed
    ) {
        /** Vanilla's {@code (int) (seconds * 20.0F)}. */
        static int ticks(float seconds) {
            return (int) (seconds * 20.0F);
        }

        public int swingTicks() {
            return ticks(swingSeconds);
        }

        public int delayTicks() {
            return ticks(delaySeconds);
        }

        public int dismountTicks() {
            return ticks(dismountSeconds);
        }

        public int knockbackTicks() {
            return ticks(knockbackSeconds);
        }

        public int damageTicks() {
            return ticks(damageSeconds);
        }

        /** Attack speed modifier: the player's base is 4, the spear swings once per {@code swingSeconds}. */
        public double attackSpeedModifier() {
            return 1.0F / swingSeconds - 4.0F;
        }
    }

    private final String path;
    private final String baseMaterial;
    private final Kind kind;
    private final float attackDamageBaseline;
    private final float attackSpeedBaseline;

    SiftiteGear(String path, String baseMaterial, Kind kind, float attackDamageBaseline, float attackSpeedBaseline) {
        this.path = path;
        this.baseMaterial = baseMaterial;
        this.kind = kind;
        this.attackDamageBaseline = attackDamageBaseline;
        this.attackSpeedBaseline = attackSpeedBaseline;
    }

    /** Path of the content id {@code the_sift:<path>}, also the item model and the {@code item.the_sift.<path>} name key. */
    public String path() {
        return path;
    }

    /** Bukkit material name of the netherite counterpart. */
    public String baseMaterial() {
        return baseMaterial;
    }

    public Kind kind() {
        return kind;
    }

    /** Durability: the tool material's, or the armour factor times the piece's unit durability. */
    public int durability() {
        return switch (kind) {
            case HELMET -> 11 * ARMOR_DURABILITY_FACTOR;
            case CHESTPLATE -> 16 * ARMOR_DURABILITY_FACTOR;
            case LEGGINGS -> 15 * ARMOR_DURABILITY_FACTOR;
            case BOOTS -> 13 * ARMOR_DURABILITY_FACTOR;
            default -> TOOL_DURABILITY;
        };
    }

    /** Armour points of the piece (the vanilla netherite values, which the mod keeps); 0 for tools. */
    public int defense() {
        return switch (kind) {
            case HELMET -> 3;
            case CHESTPLATE -> 8;
            case LEGGINGS -> 6;
            case BOOTS -> 3;
            default -> 0;
        };
    }

    /** The slot an armour piece goes in, or the main hand for tools. */
    public Slot slot() {
        return switch (kind) {
            case HELMET -> Slot.HEAD;
            case CHESTPLATE -> Slot.CHEST;
            case LEGGINGS -> Slot.LEGS;
            case BOOTS -> Slot.FEET;
            default -> Slot.MAINHAND;
        };
    }

    /** Total attack damage modifier of a tool (the player's own 1 is added by the game); 0 for armour. */
    public double attackDamageModifier() {
        if (kind.isArmor()) {
            return 0.0;
        }
        // Vanilla adds two floats and widens the sum.
        return kind == Kind.SPEAR ? TOOL_ATTACK_DAMAGE_BONUS : attackDamageBaseline + TOOL_ATTACK_DAMAGE_BONUS;
    }

    /** Attack speed modifier of a tool (the player's base is 4); 0 for armour. */
    public double attackSpeedModifier() {
        if (kind.isArmor()) {
            return 0.0;
        }
        return kind == Kind.SPEAR ? SPEAR_STATS.attackSpeedModifier() : attackSpeedBaseline;
    }

    /** The attribute modifiers that replace the netherite item's own. */
    public List<Modifier> modifiers() {
        List<Modifier> modifiers = new ArrayList<>();
        if (kind.isArmor()) {
            String key = "minecraft:armor." + armorName();
            modifiers.add(new Modifier(Attr.ARMOR, key, defense(), slot()));
            modifiers.add(new Modifier(Attr.ARMOR_TOUGHNESS, key, ARMOR_TOUGHNESS, slot()));
            modifiers.add(new Modifier(Attr.KNOCKBACK_RESISTANCE, key, ARMOR_KNOCKBACK_RESISTANCE, slot()));
        } else {
            modifiers.add(new Modifier(Attr.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE_KEY, attackDamageModifier(), Slot.MAINHAND));
            modifiers.add(new Modifier(Attr.ATTACK_SPEED, BASE_ATTACK_SPEED_KEY, attackSpeedModifier(), Slot.MAINHAND));
        }
        return List.copyOf(modifiers);
    }

    /**
     * The rules of the tool component of a mining tool: the netherite-incorrect blocks do not drop, the tool's
     * own blocks are mined at {@link #TOOL_SPEED} and drop. Empty for swords, spears and armour.
     */
    public List<ToolRule> toolRules() {
        String mineable = switch (kind) {
            case SHOVEL -> "minecraft:mineable/shovel";
            case PICKAXE -> "minecraft:mineable/pickaxe";
            case AXE -> "minecraft:mineable/axe";
            case HOE -> "minecraft:mineable/hoe";
            default -> null;
        };
        if (mineable == null) {
            return List.of();
        }
        return List.of(new ToolRule(INCORRECT_BLOCKS_TAG, null, false), new ToolRule(mineable, TOOL_SPEED, true));
    }

    /** Whether the piece replaces the item's tool component (mining tools only). */
    public boolean hasToolRules() {
        return !toolRules().isEmpty();
    }

    /** Spear timings, if this is the spear. */
    public Optional<SpearStats> spear() {
        return kind == Kind.SPEAR ? Optional.of(SPEAR_STATS) : Optional.empty();
    }

    /** Content id as a string, {@code the_sift:<path>}. */
    public String id() {
        return "the_sift:" + path;
    }

    private String armorName() {
        return switch (kind) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
            default -> throw new IllegalStateException(this + " is not armour");
        };
    }
}
