package dev.linqfy.bigCasares.modules.celular;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The phone is a clock underneath (no vanilla recipe uses clocks) with one item model per video:
 * {@code celular:video_N} plays video N in the hands and shows the still phone everywhere else.
 */
final class CelularItems {

    static final String ITEM_ID = "celular";

    private final NamespacedKey idKey;
    private final NamespacedKey videoKey;
    private final CelularService service;

    CelularItems(Plugin plugin, CelularService service) {
        this.idKey = new NamespacedKey(plugin, "celular_item");
        this.videoKey = new NamespacedKey(plugin, "celular_video");
        this.service = service;
    }

    ItemStack create() {
        ItemStack stack = new ItemStack(Material.CLOCK);
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.editMeta(meta -> {
            meta.itemName(Component.text("Celular", NamedTextColor.WHITE));
            meta.lore(List.of(
                line("Hecho con 8 lingotes de hierro y un vidrio.", NamedTextColor.GRAY),
                line("En la mano: F pasa al siguiente video, Shift+F al anterior.", NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, ITEM_ID);
        });
        setVideo(stack, 0);
        return stack;
    }

    boolean isCelular(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return ITEM_ID.equals(stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    int video(ItemStack stack) {
        Integer index = stack.getItemMeta().getPersistentDataContainer().get(videoKey, PersistentDataType.INTEGER);
        return service.clamp(index == null ? 0 : index);
    }

    void setVideo(ItemStack stack, int index) {
        int clamped = service.clamp(index);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(CelularService.NAMESPACE, service.video(clamped).id()));
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(videoKey, PersistentDataType.INTEGER, clamped));
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
