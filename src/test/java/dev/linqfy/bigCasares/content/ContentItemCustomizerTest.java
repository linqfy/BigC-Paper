package dev.linqfy.bigCasares.content;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentItemCustomizerTest {

    private static final NamespacedId ID = NamespacedId.of("test", "thing");

    @Test
    void itemsWithoutAHookKeepTheirBehaviour() {
        ContentItem item = ContentItem.builder(ID, "STICK").build();
        assertNull(item.customizer());
        assertEquals(64, item.maxStackSize());
        assertEquals(ID, item.itemModel());
    }

    @Test
    void hooksRunInTheOrderTheyWereAdded() {
        List<String> calls = new ArrayList<>();
        ContentItem item = ContentItem.builder(ID, "STICK")
            .customize(stack -> calls.add("first"))
            .customize(stack -> calls.add("second"))
            .build();
        item.customizer().accept(null);
        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void aHookIsRequired() {
        assertThrows(NullPointerException.class, () -> ContentItem.builder(ID, "STICK").customize(null));
    }
}
