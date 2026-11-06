package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.bukkit.configuration.ConfigurationSection;

/** Menu feedback sounds (vanilla sound ids or resource-pack sounds). */
public record SoundSettings(boolean enabled,String hover,String click,float volume) {
    private static final String ID="[a-z0-9_.-]+:[a-z0-9_./-]+";
    public SoundSettings {
        if(hover==null||click==null||!hover.matches(ID)||!click.matches(ID)||!Float.isFinite(volume)||volume<0||volume>2)
            throw new IllegalArgumentException("Invalid sounds configuration");
    }
    public static SoundSettings defaults() {return new SoundSettings(true,"minecraft:block.note_block.hat","minecraft:ui.button.click",.6f);}
    /** {@code y}: the engine's settings ({@link EngineConfig#read()}). */
    public static SoundSettings load(ConfigurationSection y) {
        var d=defaults();
        return new SoundSettings(y.getBoolean("sounds.enabled",d.enabled),y.getString("sounds.hover",d.hover),
            y.getString("sounds.click",d.click),(float)y.getDouble("sounds.volume",d.volume));
    }
}
