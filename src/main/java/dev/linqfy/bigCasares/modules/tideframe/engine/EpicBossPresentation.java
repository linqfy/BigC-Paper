package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.PassiveHudState;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets;
import java.util.Objects;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Original presentation of supplied boss health. Caller owns the player thread. */
final class EpicBossPresentation {
    private EpicBossPresentation() { }
    /** Ticks a hit on the boss lasts: the fill flashes white for the first two, the bar shakes by these GUI pixels. */
    static final int HIT_TICKS=6;
    private static final int[] SHAKE={-2,1,-1,1,-1,0};
    static void apply(BossBar bar,PassiveHudState.Snapshot value) { apply(bar,value,-1,false,false); }
    /** {@code hit}: ticks since the boss last lost health (-1: no hit); {@code flash} / {@code shake}: config.yml boss-bar. */
    static void apply(BossBar bar,PassiveHudState.Snapshot value,int hit,boolean flash,boolean shake) {
        Objects.requireNonNull(bar);Objects.requireNonNull(value);
        float progress=value.bossProgress();
        if(!Float.isFinite(progress)||progress<0||progress>1)
            throw new IllegalArgumentException("Boss progress must be finite and between 0 and 1.");
        String supplied=Objects.requireNonNull(value.bossTitle());
        String name=supplied.length()>18?supplied.substring(0,15)+"...":supplied;
        int percent=progress==0?0:Math.max(1,Math.round(progress*100));
        boolean enraged=progress<=.30f;
        String label=name+" | "+percent+"%"+(enraged?" | ENRAGED":"");
        // White preserves the artwork's authored colors; no shader or vanilla asset override.
        boolean lit=flash&&hit>=0&&hit<2;
        int dx=shake&&hit>=0&&hit<SHAKE.length?SHAKE[hit]:0;
        var title=Component.text(BossBarAssets.title(label,progress,dx,lit),NamedTextColor.WHITE);
        // Build/validate the full presentation before mutating the native bar.
        bar.name(title.font(Key.key("tideframe:boss")).decoration(TextDecoration.BOLD,false)
            .decoration(TextDecoration.ITALIC,false));
        bar.progress(progress);bar.color(enraged?BossBar.Color.PURPLE:BossBar.Color.PINK);
        bar.overlay(BossBar.Overlay.PROGRESS);
        // No darkness, fog, music, entity, camera or movement side effects.
        // Zero is a ratio, not an authoritative death/victory signal.
    }
}
