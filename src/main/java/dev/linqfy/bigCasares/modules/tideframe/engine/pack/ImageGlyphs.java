package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;

/** Stable pack-local image glyphs. Construct once before pack generation, off owner threads. */
public final class ImageGlyphs {
    private static final int MAX_IMAGES = 128;
    private static final ImageGlyphs EMPTY = new ImageGlyphs(List.of());
    private final List<ImageAsset> images;
    private final Map<ImageAsset, Character> glyphs;

    private ImageGlyphs(List<ImageAsset> images) {
        this.images = List.copyOf(images);
        var mapped = new LinkedHashMap<ImageAsset, Character>();
        for (int index = 0; index < images.size(); index++) mapped.put(images.get(index), (char) (0xe100 + index));
        glyphs = Map.copyOf(mapped);
    }

    public static ImageGlyphs create(Collection<ImageAsset> assets) {
        if (assets == null) throw new IllegalArgumentException("Images must be specified");
        var sorted = new TreeMap<String, ImageAsset>();
        for (var image : assets) {
            if (image == null) throw new IllegalArgumentException("Image must be specified");
            var previous = sorted.putIfAbsent(image.sha256(), image);
            if (previous != null && !previous.equals(image)) throw new IllegalArgumentException("Image SHA-256 collision");
            if (sorted.size() > MAX_IMAGES) throw new IllegalArgumentException("At most " + MAX_IMAGES + " unique images are supported");
        }
        return sorted.isEmpty() ? EMPTY : new ImageGlyphs(List.copyOf(sorted.values()));
    }

    public static ImageGlyphs empty() { return EMPTY; }
    public char glyph(ImageAsset image) {
        Character glyph = image == null ? null : glyphs.get(image);
        if (glyph == null) throw new IllegalArgumentException("Image is absent from this resource pack");
        return glyph;
    }
    public boolean contains(ImageAsset image) { return image != null && glyphs.containsKey(image); }
    public List<ImageAsset> images() { return images; }
    public int size() { return images.size(); }
}
