package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

final class DonPollosModelBinder {

    private final DonPollosModelGateway models;
    private final Logger logger;
    private final Map<UUID, DonPollosModelGateway.ModelHandle> handles = new HashMap<>();
    private final Set<String> missingModelWarnings = new HashSet<>();

    DonPollosModelBinder(DonPollosModelGateway models, Logger logger) {
        this.models = models;
        this.logger = logger;
    }

    void bind(LivingEntity anchor, DonPollosVariantSettings settings) {
        if (handles.containsKey(anchor.getUniqueId())) {
            return;
        }
        DonPollosModelGateway.ModelHandle handle;
        try {
            handle = models.attach(anchor, settings.modelKey());
        } catch (IllegalStateException missingModel) {
            anchor.setInvisible(false);
            if (missingModelWarnings.add(settings.modelKey())) {
                logger.warning("Modelo no disponible para " + settings.variant().id() + " ("
                    + missingModel.getMessage() + "). Se mostrara como aldeano hasta instalar "
                    + "plugins/BetterModel/models/" + settings.modelKey() + ".bbmodel");
            }
            return;
        }
        handles.put(anchor.getUniqueId(), handle);
        anchor.setInvisible(false);
        if (settings.animation() != null) {
            handle.animate(settings.animation());
        }
    }

    /** Engancha un modelo cualquiera (el boss) que sigue la escala de la entidad. */
    boolean bindModel(LivingEntity anchor, String modelKey) {
        return bindModel(anchor, modelKey, true);
    }

    /** Con {@code followScale} en false el modelo queda de su tamano aunque la entidad este achicada. */
    boolean bindModel(LivingEntity anchor, String modelKey, boolean followScale) {
        if (handles.containsKey(anchor.getUniqueId())) {
            return true;
        }
        try {
            handles.put(anchor.getUniqueId(), followScale
                ? models.attachScaled(anchor, modelKey)
                : models.attach(anchor, modelKey));
            return true;
        } catch (IllegalStateException missingModel) {
            if (missingModelWarnings.add(modelKey)) {
                logger.warning("Modelo no disponible: " + modelKey + " (" + missingModel.getMessage() + ")");
            }
            return false;
        }
    }

    /** Cambia la animacion en loop: corta la anterior y arranca la nueva. */
    boolean switchAnimation(UUID anchorId, String from, String to) {
        DonPollosModelGateway.ModelHandle handle = handles.get(anchorId);
        if (handle == null) {
            return false;
        }
        if (from != null) {
            handle.stop(from);
        }
        return handle.animate(to);
    }

    boolean playOnce(UUID anchorId, String animation, Runnable onEnd) {
        DonPollosModelGateway.ModelHandle handle = handles.get(anchorId);
        return handle != null && handle.playOnce(animation, onEnd);
    }

    void stopAnimation(UUID anchorId, String animation) {
        DonPollosModelGateway.ModelHandle handle = handles.get(anchorId);
        if (handle != null) {
            handle.stop(animation);
        }
    }

    void release(UUID anchorId) {
        DonPollosModelGateway.ModelHandle handle = handles.remove(anchorId);
        if (handle != null) {
            handle.close();
        }
    }

    void releaseAll() {
        for (UUID anchorId : new ArrayList<>(handles.keySet())) {
            release(anchorId);
        }
    }
}
