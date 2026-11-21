package dev.linqfy.bigCasares.modules.alexscaves;

import java.util.List;

/** Attribution required by the original mod's LGPL licence. Mirrors licenses/alexscaves/CREDITS.txt. */
public final class AlexsCavesCredits {
    public static final String MOD_NAME = "Alex's Caves";
    public static final String MOD_VERSION = "2.0.2";
    public static final String AUTHORS = "Alexthe668 (AlexModGuy), Noonyeyz";
    public static final String CONTRIBUTORS =
        "LudoCrypt, _Ninni, PrismaticPinky, RenRen, Gatetoh, arby698, PlummetStudios, HolidayRaptor";
    public static final String LICENSE = "GNU LGPL (declarada en mods.toml del mod)";
    public static final String MODRINTH_URL = "https://modrinth.com/mod/alexs-caves";
    public static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods/alexs-caves";
    public static final String SOURCE_URL = "https://github.com/AlexModGuy/AlexsCaves";

    private AlexsCavesCredits() {
    }

    public static List<String> lines() {
        return List.of(
            "§6§l" + MOD_NAME + " §7(recreación no oficial para BigCasares)",
            "§7Mod original " + MOD_VERSION + " por §f" + AUTHORS,
            "§7Colaboradores: §f" + CONTRIBUTORS,
            "§7Música de los discos: §f_Ninni",
            "§7Licencia del mod y de sus assets: §f" + LICENSE,
            "§7Mod: §b" + MODRINTH_URL,
            "§7Código: §b" + SOURCE_URL,
            "§8Módulo hecho para un servidor privado; no está afiliado al autor."
        );
    }

    /** One-line version for pack descriptions. */
    public static String shortLine() {
        return "Alex's Caves por AlexModGuy (LGPL). Recreación no oficial. Ver licenses/alexscaves/CREDITS.txt";
    }
}
