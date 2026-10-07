package dev.linqfy.bigCasares.modules.celular;

import java.util.Objects;

public record CelularVideo(String id, String title, int frames) {

    public static final int FPS = 10;

    public CelularVideo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid video id: " + id);
        }
        if (frames <= 0) {
            throw new IllegalArgumentException("Video " + id + " needs at least one frame");
        }
    }

    public double seconds() {
        return frames / (double) FPS;
    }
}
