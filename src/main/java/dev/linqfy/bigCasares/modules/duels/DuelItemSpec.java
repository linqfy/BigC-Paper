package dev.linqfy.bigCasares.modules.duels;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One kit item as written in the config: {@code MATERIAL[*amount] [enchantment:level ...]}, for example
 * {@code DIAMOND_SWORD sharpness:2 unbreaking:1} or {@code GOLDEN_APPLE*6}.
 */
record DuelItemSpec(String material, int amount, Map<String, Integer> enchantments) {

    DuelItemSpec {
        enchantments = Map.copyOf(enchantments);
    }

    static DuelItemSpec parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Item vacío.");
        }
        String[] parts = raw.trim().split("\\s+");
        String head = parts[0];
        int amount = 1;
        int star = head.indexOf('*');
        if (star >= 0) {
            try {
                amount = Integer.parseInt(head.substring(star + 1));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Cantidad inválida en '" + raw + "'.");
            }
            head = head.substring(0, star);
        }
        if (head.isEmpty() || amount < 1 || amount > 64) {
            throw new IllegalArgumentException("Item inválido: '" + raw + "'.");
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String[] pair = parts[i].split(":");
            if (pair.length != 2) {
                throw new IllegalArgumentException("Encantamiento inválido '" + parts[i] + "' en '" + raw + "'.");
            }
            try {
                int level = Integer.parseInt(pair[1]);
                if (level < 1 || level > 255) {
                    throw new IllegalArgumentException("Nivel fuera de rango en '" + raw + "'.");
                }
                enchantments.put(pair[0].toLowerCase(Locale.ROOT), level);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Nivel inválido '" + parts[i] + "' en '" + raw + "'.");
            }
        }
        return new DuelItemSpec(head.toUpperCase(Locale.ROOT), amount, enchantments);
    }
}
