package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum DonPollosProduct {
    POLLO_FRITO("pollo-frito", "pollo_frito", "§6§lPollo Frito", null, 0, 0,
        List.of("§7Crocante por fuera, jugoso por dentro.")),
    SALSA("salsa", "salsa", "§f§lSalsa", "strength", 0, 10,
        List.of("§7Tomala y sentite fuerte.", "§c❤ Fuerza I §7durante §f10s")),
    PICANTE("picante", "picante", "§c§lPicante", "speed", 2, 10,
        List.of("§7Pica tanto que salis corriendo.", "§b⚡ Velocidad III §7durante §f10s"));

    private final String id;
    private final String modelKey;
    private final String displayName;
    private final String effect;
    private final int amplifier;
    private final int seconds;
    private final List<String> lore;

    DonPollosProduct(String id, String modelKey, String displayName, String effect,
                     int amplifier, int seconds, List<String> lore) {
        this.id = id;
        this.modelKey = modelKey;
        this.displayName = displayName;
        this.effect = effect;
        this.amplifier = amplifier;
        this.seconds = seconds;
        this.lore = lore;
    }

    public String id() {
        return id;
    }

    public String modelKey() {
        return modelKey;
    }

    public String displayName() {
        return displayName;
    }

    public String effect() {
        return effect;
    }

    public int amplifier() {
        return amplifier;
    }

    public int seconds() {
        return seconds;
    }

    public List<String> lore() {
        return lore;
    }

    public static Optional<DonPollosProduct> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(product -> product.id.equals(normalized)).findFirst();
    }
}
