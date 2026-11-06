package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.modules.shop.ShopView;
import dev.linqfy.bigCasares.modules.shop.ShopViewItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BedrockShopFormTest {

    @Test
    void shopFormCarriesTheLookTagIconsAndPriceColours() {
        AtomicInteger clicked = new AtomicInteger(-1);
        ShopView view = new ShopView("food", "&cComida", "§7Tocá un objeto para comprarlo", "$120", List.of(
            new ShopViewItem("bread", "Pan", "", "$40", null, true, "textures/items/bread"),
            new ShopViewItem("nuke", "Nuke", "", "$5000", null, false, null)));

        BedrockShopForm form = BedrockShopForm.of(view, clicked::set);

        assertEquals(BedrockFormStyle.TAG + "&cComida", form.title());
        assertEquals("§7Tocá un objeto para comprarlo\n\n§fSaldo: §e$120", form.content());
        assertEquals("Pan\n§e$40", form.buttons().get(0).text());
        assertEquals("textures/items/bread", form.buttons().get(0).iconPath());
        assertEquals("Nuke\n§c$5000", form.buttons().get(1).text());
        assertNull(form.buttons().get(1).iconPath());
        form.responseHandler().accept(UUID.randomUUID(), 1);
        assertEquals(1, clicked.get());
    }

    @Test
    void categoriesWithoutPriceShowOnlyTheirName() {
        ShopView view = new ShopView("categories", "Tienda", "Elegí", "$0",
            List.of(new ShopViewItem("blocks", "Bloques", "", "", null, true, "textures/blocks/stone")));

        assertEquals("Bloques", BedrockShopForm.of(view, index -> { }).buttons().get(0).text());
    }
}
