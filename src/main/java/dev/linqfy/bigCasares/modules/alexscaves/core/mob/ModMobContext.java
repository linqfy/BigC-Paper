package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import dev.linqfy.bigCasares.modules.alexscaves.core.ModTasks;
import dev.linqfy.bigCasares.modules.alexscaves.core.ability.CooldownLedger;
import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectService;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** Services a mob behaviour may use. */
public interface ModMobContext {
    Plugin plugin();

    ModEffectService effects();

    ModDamageTypes damageTypes();

    CooldownLedger cooldowns();

    JavaModelGateway models();

    /** Model handle of a tracked mob, when its BetterModel model is attached. */
    Optional<JavaModelHandle> model(UUID entity);

    ModMobService mobs();

    /** The module's tasks: everything delayed or repeating goes here, so turning the module off cleans it up. */
    ModTasks tasks();
}
