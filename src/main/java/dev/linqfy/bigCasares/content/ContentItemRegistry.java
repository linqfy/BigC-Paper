package dev.linqfy.bigCasares.content;

import dev.linqfy.bigCasares.dimension.NamespacedId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry of code-defined content items; ids are unique across modules. */
public final class ContentItemRegistry {

    private final Map<NamespacedId, ContentItem> items = new LinkedHashMap<>();

    public synchronized void register(ContentItem item) {
        if (items.putIfAbsent(item.id(), item) != null) {
            throw new IllegalArgumentException("Duplicate content item id: " + item.id());
        }
    }

    public synchronized void unregisterNamespace(String namespace) {
        items.keySet().removeIf(id -> id.namespace().equals(namespace));
    }

    public synchronized Optional<ContentItem> find(NamespacedId id) {
        return Optional.ofNullable(items.get(id));
    }

    public synchronized Optional<ContentItem> find(String id) {
        try {
            return find(NamespacedId.parse(id));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public synchronized Collection<ContentItem> items() {
        return List.copyOf(items.values());
    }
}
