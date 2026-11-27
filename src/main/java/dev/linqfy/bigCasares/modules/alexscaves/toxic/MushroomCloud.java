package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The mushroom cloud of a nuclear explosion (mod {@code MushroomCloudParticle} with {@code MushroomCloudModel}). The
 * mod draws it as a client particle; here it is the BetterModel model {@value #MODEL} on an invisible anchor, scaled
 * like the particle ({@code size + 0.2}) and playing the animation baked for its size by
 * {@code tools/alexscaves/mushroom_cloud.py}: a fireball, then the column, the clouds and the shock rings growing
 * for {@code ceil(133.33 * size)} ticks. Drawn at full brightness, as the mod's glowing render layers are.
 */
public final class MushroomCloud {
    public static final String MODEL = "alexscaves_mushroom_cloud";
    /** Every bone of the model, lit at full brightness. */
    static final List<String> BONES = List.of("mushroom_cloud", "lightBall", "lowerCloud", "lowerCloud_planes",
        "cube_r1", "cube_r2", "cube_r3", "cube_r4", "plume", "cube_r5", "cube_r6", "plumeBlast", "plumeRing",
        "upperCloud", "upperCloud1", "upperCloud_planes", "cube_r7", "cube_r8", "cube_r9", "cube_r10", "rings",
        "upperRing", "lowerRing");

    private MushroomCloud() {
    }

    /** Mod: {@code lifetime = ceil(133.33 * size)} ticks. */
    static int lifetime(float size) {
        return (int) Math.ceil(133.33F * size);
    }

    /** Mod: the particle is drawn {@code size + 0.2} times the model. */
    static float scale(float size) {
        return size + 0.2F;
    }

    /** The animation baked for that explosion size. */
    static String animation(float size) {
        return "explode_" + lifetime(size);
    }

    /** Shows the cloud over {@code center}; a server without the model (or BetterModel) just skips it. */
    static void show(ModMobService mobs, Location center, float size) {
        // Mod render: the model stands half a block below the explosion's position.
        Interaction anchor = center.getWorld().spawn(center.clone().subtract(0, 0.5, 0), Interaction.class, spawned -> {
            spawned.setInteractionWidth(0.1F);
            spawned.setInteractionHeight(0.1F);
            spawned.setResponsive(false);
            spawned.setPersistent(false);
        });
        JavaModelGateway models = mobs.models();
        JavaModelHandle handle;
        try {
            handle = models.attach(anchor, MODEL);
        } catch (RuntimeException missing) {
            anchor.remove();
            return;
        }
        AtomicBoolean ended = new AtomicBoolean();
        Runnable end = () -> {
            if (ended.compareAndSet(false, true)) {
                models.close(handle);
                anchor.remove();
            }
        };
        models.scale(handle, scale(size));
        models.brighten(handle, 15, 15, BONES);
        if (!models.animateOnce(handle, animation(size), end)) {
            end.run();
            return;
        }
        // In case the animation never reports its end (the anchor unloaded, BetterModel reloaded...).
        mobs.tasks().later(lifetime(size) + 40L, end, end);
    }
}
