package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.pack.ImageGlyphs;
import java.util.Objects;
import java.util.function.Consumer;

/** An immutable startup pack registry gates every later screen replacement. */
public final class ImagePolicy {
    public static final String RESTART_REQUIRED="New images require a server restart.";
    private ImagePolicy() {}

    public static void requireKnown(Screen candidate,ImageGlyphs images) {
        Objects.requireNonNull(candidate,"candidate");Objects.requireNonNull(images,"images");
        for(var element:candidate.elements())
            if(element.image()!=null && !images.contains(element.image()))
                throw new IllegalArgumentException(RESTART_REQUIRED);
    }

    /** Validate the complete candidate before a caller can mutate or reopen anything. */
    public static void publish(Screen candidate,ImageGlyphs images,Consumer<Screen> publish) {
        Objects.requireNonNull(publish,"publish");
        requireKnown(candidate,images);
        publish.accept(candidate);
    }
}
