package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.geyser.BedrockShopForm;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/** An input adapter for touch/gamepad players; combat remains in the existing ability handlers. */
final class LegendaryAbilityMenu {
    record Action(String id, String name, LegendaryWeapon weapon, int level,
                  LegendaryElementalSkill elemental, boolean secondary) { }

    private final BigCasares plugin;
    private final LegendaryPvpLevels levels;
    private final LegendaryAltarAbilities altar;
    private final LegendaryElementalAbilities elemental;
    private final RuntimeRegistrationScope scope;

    LegendaryAbilityMenu(BigCasares plugin, LegendaryPvpLevels levels, LegendaryAltarAbilities altar,
                         LegendaryElementalAbilities elemental, RuntimeRegistrationScope scope) {
        this.plugin = plugin;
        this.levels = levels;
        this.altar = altar;
        this.elemental = elemental;
        this.scope = scope;
    }

    static List<Action> actions(LegendaryWeapon weapon) {
        return switch (weapon) {
            case BLOODLUST -> List.of(action("blood-hook", "Blood Hook", weapon, false),
                    action("blood-trail", "Blood Trail", weapon, true));
            case SHADOW_BLADE -> List.of(action("shadow-daggers", "Shadow Daggers", weapon, false),
                    action("shadow-leap", "Shadow Leap", weapon, true));
            case EARTHBREAKER -> List.of(action("earthshatter", "Earthshatter", weapon, false),
                    action("stalwart-absorption", "Stalwart Absorption", weapon, true));
            case WINDWEAVER, THUNDER_SPEAR -> Arrays.stream(LegendaryElementalSkill.values())
                    .filter(skill -> skill.weapon == weapon)
                    .map(skill -> new Action(skill.id, skill.id.replace('-', ' '), weapon, skill.level, skill, false))
                    .toList();
            default -> List.of();
        };
    }

    private static Action action(String id, String name, LegendaryWeapon weapon, boolean secondary) {
        return new Action(id, name, weapon, LegendaryAltarAbilities.requiredLevel(weapon, secondary), null, secondary);
    }

    static Action resolve(LegendaryWeapon held, String id) {
        return actions(held).stream().filter(action -> action.id().equalsIgnoreCase(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Esa habilidad no pertenece al arma que sostenés."));
    }

    private LegendaryWeapon held(Player player) {
        return plugin.getCustomItemRegistry().resolveItemId(player.getInventory().getItemInMainHand())
                .flatMap(LegendaryWeapon::find)
                .orElseThrow(() -> new IllegalArgumentException("Sostené un arma legendaria en la mano principal."));
    }

    void cast(Player player, String id) {
        Action action = resolve(held(player), id);
        if (action.elemental() != null) elemental.cast(player, action.elemental());
        else altar.cast(player, action.weapon(), action.secondary());
    }

    void show(Player player) {
        LegendaryWeapon weapon = held(player);
        var actions = actions(weapon);
        int level = levels.level(player.getUniqueId(), weapon);
        var buttons = actions.stream().map(action -> new BedrockShopForm.Button(
                (level >= action.level() ? "§a" : "§8") + action.name() + "\nNivel " + action.level(), null)).toList();
        var form = new BedrockShopForm(weapon.displayName, "Nivel " + level + "/5. Elegí una habilidad.\n"
                + "Apuntá antes de abrir el menú. Para terminar un salto, activalo otra vez.", buttons,
                (uuid, index) -> scope.guard(() -> {
                    Player current = plugin.getServer().getPlayer(uuid);
                    if (current == null || !current.isOnline() || index < 0 || index >= actions.size()) return;
                    try {
                        // The form can outlive an inventory change or a full module reload.
                        if (held(current) != weapon) throw new IllegalArgumentException("Cambiaste de arma. Abrí el menú de nuevo.");
                        cast(current, actions.get(index).id());
                    } catch (IllegalArgumentException invalid) {
                        current.sendMessage("§c" + invalid.getMessage());
                    }
                }).run());
        var geyser = plugin.getGeyserIntegrationModule();
        if (geyser != null && geyser.shopForms().map(forms -> forms.show(player.getUniqueId(), form)).orElse(false)) return;
        player.sendMessage("§6" + weapon.displayName + " — nivel " + level + "/5");
        actions.forEach(action -> player.sendMessage("§e/weapons cast " + action.id() + " §7(nivel " + action.level() + ")"));
    }
}
