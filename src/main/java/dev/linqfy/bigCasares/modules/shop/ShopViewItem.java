package dev.linqfy.bigCasares.modules.shop;

/** {@code iconPath}: the Bedrock texture of the item (see BedrockItemIcons), or null. */
public record ShopViewItem(
    String id,
    String name,
    String description,
    String price,
    String iconUrl,
    boolean available,
    String iconPath
) {
    public ShopViewItem(String id, String name, String description, String price, String iconUrl, boolean available) {
        this(id, name, description, price, iconUrl, available, null);
    }
}
