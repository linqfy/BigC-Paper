package dev.linqfy.bigCasares.modules.customcrossbow;

import java.util.List;

public enum CrossbowEnchantment {
    SNIPER("sniper_book", "Francotirador", 1024, List.of(
        "§7Agachate para usar la mira integrada.",
        "§7Carga 1 corazón cada 1,5 s; máximo 6 (9 s).",
        "§7Mantené clic derecho para cargar; soltá para disparar.",
        "§7Levantate para cancelar sin gastar vida.",
        "§7Ignora armadura; revela y ralentiza al objetivo."
    )),
    MULTICHARGE("multicharge_book", "Multicarga", 1025, List.of(
        "§7Carga hasta 5 flechas para disparos consecutivos.",
        "§7Cada disparo consume una flecha y durabilidad.",
        "§7Sin cargador para cohetes, eco o amatista."
    ));

    private final String bookId;
    private final String displayName;
    private final int modelData;
    private final List<String> description;

    CrossbowEnchantment(String bookId, String displayName, int modelData, List<String> description) {
        this.bookId = bookId;
        this.displayName = displayName;
        this.modelData = modelData;
        this.description = description;
    }

    public String bookId() { return bookId; }
    public String displayName() { return displayName; }
    public int modelData() { return modelData; }
    public List<String> description() { return description; }
    public String loreLine() { return "§7" + displayName + " I"; }
}
