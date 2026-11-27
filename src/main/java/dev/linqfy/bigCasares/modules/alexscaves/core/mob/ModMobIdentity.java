package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import java.util.Optional;

/**
 * Identity of a module mob stored in its PDC. The shared
 * {@code bigcasares:custom_entity} key holds {@code alexscaves:<id>} so other
 * BigCasares systems (Mob Scaling, Blood Moon) recognise and skip it.
 */
public final class ModMobIdentity {
    public static final String CUSTOM_ENTITY_KEY = "custom_entity";
    public static final String TAG_PREFIX = "alexscaves:";

    private ModMobIdentity() {
    }

    public static void tag(TagStore store, ModMobType type) {
        store.setString(CUSTOM_ENTITY_KEY, type.customEntityTag());
    }

    public static Optional<String> mobId(TagStore store) {
        String tag = store.getString(CUSTOM_ENTITY_KEY);
        if (tag == null || !tag.startsWith(TAG_PREFIX) || tag.length() == TAG_PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(tag.substring(TAG_PREFIX.length()));
    }

    /** Minimal view of a PDC so the identity logic can be tested with an in-memory map. */
    public interface TagStore {
        String getString(String key);

        void setString(String key, String value);
    }
}
