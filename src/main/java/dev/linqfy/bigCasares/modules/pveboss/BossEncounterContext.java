package dev.linqfy.bigCasares.modules.pveboss;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/** What a {@link BossEncounterController} may see and do on the boss instance it belongs to. */
public interface BossEncounterContext {

    JavaPlugin plugin();

    UUID bossInstanceId();

    AbyssGuardianDefinition definition();

    /** The authoritative anchor. Its vanilla health is not the boss health; use {@link #health()}. */
    Warden boss();

    JavaModelGateway models();

    JavaModelHandle model();

    /** Requests a named animation of the boss model (cosmetic; never wait for it). */
    void animate(String animation);

    /** Living, non-spectator players inside the audience radius. */
    List<Player> players();

    boolean isBedrock(UUID playerId);

    double health();

    double maximumHealth();

    /** Damages the health pool directly, crediting {@code attributedTo} when it is not {@code null}. */
    void damage(double amount, Player attributedTo);

    void heal(double amount);

    /** Ends the fight as a defeat: ranking, rewards callback and cleanup run as for a normal kill. */
    void defeat();
}
