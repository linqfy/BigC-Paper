package dev.linqfy.bigCasares.modules.tombstone;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class TombstoneLedger {
    private final Map<UUID, TombstoneRecord> records = new LinkedHashMap<>();

    void restore(Collection<TombstoneRecord> restored, Instant now) {
        records.clear();
        restored.stream()
            .filter(record -> record.expiresAt().isAfter(now))
            .filter(record -> TombstoneItemLayout.hasItems(record.items()))
            .forEach(record -> records.put(record.id(), record));
    }

    void put(TombstoneRecord record) {
        records.put(record.id(), record);
    }

    void putPersisted(TombstoneRecord record, TombstoneStorage storage) {
        TombstoneRecord previous = records.put(record.id(), record);
        try {
            storage.saveAll(all());
        } catch (RuntimeException failure) {
            if (previous == null) records.remove(record.id());
            else records.put(record.id(), previous);
            throw failure;
        }
    }

    Optional<TombstoneRecord> find(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    void remove(UUID id) {
        records.remove(id);
    }

    List<TombstoneRecord> all() {
        return List.copyOf(records.values());
    }
}
