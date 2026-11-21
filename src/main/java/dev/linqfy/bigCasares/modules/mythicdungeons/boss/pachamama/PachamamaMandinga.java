package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Mandinga on Pachamama's back (phase 2): an invisible marker armor stand that follows her upper back every tick
 * and carries the {@code bigcasares_myth_pachamama_mandinga} model (coiled like a serpent, sitting with his guitar).
 * He is scenery: the shared health is hers, his guitar shows up as shockwaves. Bedrock players see a small wither
 * skeleton stand-in instead of the model.
 */
final class PachamamaMandinga {

    static final String MODEL = "bigcasares_myth_pachamama_mandinga";
    /** Where he sits relative to her feet: behind the shoulders. */
    private static final double BACK = 0.75;
    private static final double HEIGHT = 3.05;
    private static final double STAND_IN_SCALE = 0.75;

    private final MythicBossSupport support;
    private final UUID bossId;
    private ArmorStand anchor;
    private JavaModelHandle model;

    PachamamaMandinga(MythicBossSupport support, UUID bossId) {
        this.support = support;
        this.bossId = bossId;
    }

    void spawn(Location boss) {
        if (anchor != null) {
            return;
        }
        Location at = seat(boss);
        anchor = support.spawnOwned(bossId, at, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
        });
        try {
            model = support.models().attach(anchor, MODEL);
            animate("emerge");
        } catch (RuntimeException | LinkageError failure) {
            model = null;
            support.logger().warning("[Mazmorras] Pachamama: sin modelo " + MODEL + " (" + failure.getMessage() + ")");
        }
        try {
            WitherSkeleton standIn = support.spawnBedrockStandIn(bossId, anchor, WitherSkeleton.class, STAND_IN_SCALE);
            EntityEquipment equipment = standIn.getEquipment();
            if (equipment != null) {
                equipment.clear();
            }
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "[Mazmorras] Pachamama: sin doble de Bedrock para Mandinga", failure);
        }
    }

    /** Keeps him on her back; call every tick. */
    void follow(Location boss) {
        if (anchor != null && anchor.isValid()) {
            anchor.teleport(seat(boss));
        }
    }

    boolean present() {
        return anchor != null && anchor.isValid();
    }

    Location location() {
        return anchor == null ? null : anchor.getLocation();
    }

    void animate(String clip) {
        if (model == null) {
            return;
        }
        try {
            support.models().animate(model, clip);
        } catch (RuntimeException ignored) {
            // cosmetic
        }
    }

    void clear() {
        if (model != null) {
            try {
                support.models().close(model);
            } catch (RuntimeException ignored) {
                // already gone
            }
            model = null;
        }
        if (anchor != null && anchor.isValid()) {
            anchor.remove();
        }
        anchor = null;
    }

    private static Location seat(Location boss) {
        Vector facing = boss.getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0e-6) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Location at = boss.clone().subtract(facing.multiply(BACK)).add(0.0, HEIGHT, 0.0);
        at.setYaw(boss.getYaw());
        at.setPitch(0.0f);
        return at;
    }
}
