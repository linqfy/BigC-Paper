package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.modules.shop.ShopView;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

public record BedrockShopForm(
    String title,
    String content,
    List<Button> buttons,
    BiConsumer<UUID, Integer> responseHandler
) {
    public BedrockShopForm {
        buttons = List.copyOf(buttons);
    }

    /**
     * The shop as a form with the BigCasares look (same colours as its Tideframe screen): name and price per row, the
     * item's Bedrock icon when it has one, and the balance under the help text.
     */
    public static BedrockShopForm of(ShopView view, IntConsumer responseHandler) {
        return new BedrockShopForm(
            BedrockFormStyle.styled(view.title()),
            view.description() + "\n\n§fSaldo: §e" + view.balance(),
            view.items().stream().map(item -> new Button(
                item.name() + (item.price().isBlank() ? "" : "\n" + (item.available() ? "§e" : "§c") + item.price()),
                item.iconUrl(),
                item.iconPath()
            )).toList(),
            (ignored, index) -> responseHandler.accept(index)
        );
    }

    /** {@code iconPath}: a texture inside the Bedrock client's packs (for example textures/items/apple), or null. */
    public record Button(String text, String iconUrl, String iconPath) {
        public Button(String text, String iconUrl) {
            this(text, iconUrl, null);
        }
    }
}
