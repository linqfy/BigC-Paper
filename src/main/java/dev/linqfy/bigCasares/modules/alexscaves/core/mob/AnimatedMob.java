package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import org.bukkit.entity.LivingEntity;

import java.util.function.Function;

/** A mob without abilities of its own whose model only follows its state: idle, walk and one state loop. */
public class AnimatedMob implements ModMobBehaviour {
    private final MobAnimator animation = new MobAnimator();
    private final Function<LivingEntity, String> state;

    /** @param state the state loop to play now, or null for idle/walk */
    public AnimatedMob(Function<LivingEntity, String> state) {
        this.state = state;
    }

    protected MobAnimator animation() {
        return animation;
    }

    @Override
    public void tick(LivingEntity entity, ModMobContext context, int ticks) {
        animation.update(entity, context, ticks, state.apply(entity));
    }
}
