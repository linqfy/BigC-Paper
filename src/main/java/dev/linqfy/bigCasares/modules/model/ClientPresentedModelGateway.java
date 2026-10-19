package dev.linqfy.bigCasares.modules.model;

import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A model gateway that also tells other clients which model a body wears. Each attached model is recorded in
 * {@link ClientEntityPresentationRegistry} under the body's id, Java type and the model key, which is how the
 * Geyser integration swaps the vanilla body for a Bedrock entity drawing that model (Geyser does not show the
 * display entities a BetterModel model is made of).
 *
 * <p>A server sends an entity to the players around it as soon as the entity joins the world, which is before a
 * model can be attached to it, so after recording a body {@link OtherClients#respawn} sends it to those clients
 * again. Bedrock clients do not draw invisible entities, so a model on an invisible anchor (a marker armor stand)
 * is recorded on a stand-in instead: an entity only those clients see.</p>
 */
public final class ClientPresentedModelGateway implements JavaModelGateway {

    /** The clients other than Java ones (Bedrock players through Geyser). */
    public interface OtherClients {
        /**
         * A visible entity at an invisible anchor, shown to the other clients only, or empty when none is needed.
         * It is not shown to anyone yet: {@link #respawn} shows it once it is recorded.
         */
        Optional<Entity> openStandIn(Entity anchor);

        void closeStandIn(Entity standIn);

        /** Sends the body again to the other clients that can see it, so they get it as recorded now. */
        void respawn(Entity body);
    }

    private final JavaModelGateway delegate;
    private final OtherClients otherClients;
    private final Map<JavaModelHandle, Presented> presented = new HashMap<>();

    public ClientPresentedModelGateway(JavaModelGateway delegate, OtherClients otherClients) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.otherClients = Objects.requireNonNull(otherClients, "otherClients");
    }

    @Override
    public JavaModelHandle attach(Entity anchor, String modelKey) {
        JavaModelHandle handle = delegate.attach(anchor, modelKey);
        if (presented.containsKey(handle)) {
            return handle;
        }
        Optional<Entity> standIn = invisible(anchor) ? otherClients.openStandIn(anchor) : Optional.empty();
        Entity body = standIn.orElse(anchor);
        ClientEntityPresentationRegistry.register(body.getUniqueId(), body.getType().name(), modelKey);
        presented.put(handle, new Presented(body, standIn.isPresent()));
        otherClients.respawn(body);
        return handle;
    }

    @Override
    public boolean animate(JavaModelHandle handle, String animationKey) {
        return delegate.animate(handle, animationKey);
    }

    @Override
    public boolean animateOnce(JavaModelHandle handle, String animationKey, Runnable onEnd) {
        return delegate.animateOnce(handle, animationKey, onEnd);
    }

    @Override
    public boolean stopAnimation(JavaModelHandle handle, String animationKey) {
        return delegate.stopAnimation(handle, animationKey);
    }

    @Override
    public boolean scale(JavaModelHandle handle, float factor) {
        return delegate.scale(handle, factor);
    }

    @Override
    public boolean brighten(JavaModelHandle handle, int block, int sky, Collection<String> bones) {
        return delegate.brighten(handle, block, sky, bones);
    }

    @Override
    public void close(JavaModelHandle handle) {
        delegate.close(handle);
        Presented body = presented.remove(handle);
        if (body == null) {
            return;
        }
        ClientEntityPresentationRegistry.unregister(body.entity().getUniqueId(), handle.modelKey());
        if (body.standIn()) {
            otherClients.closeStandIn(body.entity());
        }
    }

    private static boolean invisible(Entity anchor) {
        return anchor instanceof LivingEntity living && living.isInvisible();
    }

    private record Presented(Entity entity, boolean standIn) {
    }
}
