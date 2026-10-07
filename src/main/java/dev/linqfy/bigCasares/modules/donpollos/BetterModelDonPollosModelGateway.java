package dev.linqfy.bigCasares.modules.donpollos;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.entity.Entity;

final class BetterModelDonPollosModelGateway implements DonPollosModelGateway {

    /** A quien se le muestra el modelo 3D (a los de Bedrock no: ven el mob de abajo, porque Bedrock no los dibuja). */
    private final java.util.function.Predicate<java.util.UUID> showModelTo;

    BetterModelDonPollosModelGateway(java.util.function.Predicate<java.util.UUID> showModelTo) {
        this.showModelTo = showModelTo;
    }

    @Override
    public ModelHandle attachScaled(Entity anchor, String modelKey) {
        ModelHandle handle = attach(anchor, modelKey);
        BetterModel.registry(BukkitAdapter.adapt(anchor))
            .flatMap(registry -> java.util.Optional.ofNullable(registry.tracker(modelKey)))
            .ifPresent(tracker -> tracker.scaler(kr.toxicity.model.api.tracker.ModelScaler.entity()));
        return handle;
    }

    /**
     * Solo a quien {@code showModelTo} acepta se le manda el modelo (a los de Bedrock no: ven el mob de abajo).
     * {@code EntityTracker.spawnCondition} llego en BetterModel 3.5.0; BigCasares compila contra 3.2.0, asi que se
     * llama si esta disponible en el servidor.
     */
    private void restrictViewers(EntityTracker tracker) {
        java.util.function.Predicate<kr.toxicity.model.api.platform.PlatformPlayer> condition =
            player -> showModelTo.test(player.uuid());
        try {
            tracker.getClass().getMethod("spawnCondition", java.util.function.Predicate.class).invoke(tracker, condition);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // BetterModel sin spawnCondition: todos ven el modelo
        }
    }

    @Override
    public ModelHandle attach(Entity anchor, String modelKey) {
        EntityTracker tracker = BetterModel.model(modelKey)
            .map(renderer -> renderer.create(BukkitAdapter.adapt(anchor)))
            .orElseThrow(() -> new IllegalStateException("BetterModel model not loaded: " + modelKey));
        // sin modelo para ese jugador, BetterModel tampoco le esconde la entidad: el de Bedrock ve el mob
        restrictViewers(tracker);
        return new ModelHandle() {
            @Override
            public boolean animate(String animation) {
                return tracker.animate(animation);
            }

            @Override
            public boolean playOnce(String animation, Runnable onEnd) {
                return tracker.animate(animation, AnimationModifier.DEFAULT_WITH_PLAY_ONCE, onEnd);
            }

            @Override
            public void stop(String animation) {
                tracker.stopAnimation(animation);
            }

            @Override
            public void close() {
                tracker.close();
            }
        };
    }
}
