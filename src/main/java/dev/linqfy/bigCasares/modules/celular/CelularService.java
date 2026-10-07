package dev.linqfy.bigCasares.modules.celular;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Video navigation and village loot rules, free of Bukkit so they can be unit tested. */
public final class CelularService {

    private final List<CelularVideo> videos;
    private final CelularSettings settings;

    public CelularService(List<CelularVideo> videos, CelularSettings settings) {
        if (videos.isEmpty()) {
            throw new IllegalArgumentException("The phone needs at least one video");
        }
        this.videos = List.copyOf(videos);
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public CelularSettings settings() {
        return settings;
    }

    public List<CelularVideo> videos() {
        return videos;
    }

    public int size() {
        return videos.size();
    }

    public CelularVideo video(int index) {
        return videos.get(clamp(index));
    }

    /** F: next video, wrapping after the last one. */
    public int next(int index) {
        return Math.floorMod(clamp(index) + 1, videos.size());
    }

    /** Shift + F: previous video, wrapping before the first one. */
    public int previous(int index) {
        return Math.floorMod(clamp(index) - 1, videos.size());
    }

    public int navigate(int index, boolean sneaking) {
        return sneaking ? previous(index) : next(index);
    }

    public static final String NAMESPACE = "celular";
    public static final String STILL_MODEL = "celular";
    public static final String HINT = "  ·  F siguiente, Shift+F anterior";
    public static final String BEDROCK_CRAFT_MESSAGE = "Dale bobi, no tenes java?, bancatela pibe";

    /** Bedrock shows the phone (through Geyser) with "Pobre" on the screen: no videos, no controls. */
    public static boolean worksFor(boolean bedrock) {
        return !bedrock;
    }

    /** Java item models of the phone; Geyser maps each one to a Bedrock item with the same identifier. */
    public List<String> itemModels() {
        List<String> models = new java.util.ArrayList<>();
        models.add(NAMESPACE + ":" + STILL_MODEL);
        videos.forEach(video -> models.add(NAMESPACE + ":" + video.id()));
        return List.copyOf(models);
    }

    /** An index saved in an old phone can fall out of range when videos are removed. */
    public int clamp(int index) {
        return index < 0 || index >= videos.size() ? 0 : index;
    }

    public String actionBar(int index) {
        return "▶ " + (clamp(index) + 1) + "/" + videos.size() + "  " + video(index).title();
    }

    public static boolean isVillageChest(String namespace, String path) {
        return "minecraft".equals(namespace) && path.toLowerCase(Locale.ROOT).startsWith("chests/village/");
    }

    /** Each village hands out a single phone: in the first chest somebody opens. */
    public boolean shouldAddVillagePhone(String lootNamespace, String lootPath, boolean villageAlreadyGavePhone) {
        return settings.villageLootEnabled()
            && isVillageChest(lootNamespace, lootPath)
            && !villageAlreadyGavePhone;
    }
}
