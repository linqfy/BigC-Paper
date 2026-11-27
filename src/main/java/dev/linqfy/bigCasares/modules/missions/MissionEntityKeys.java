package dev.linqfy.bigCasares.modules.missions;

/** Pure rules for naming custom entities in {@code KILL_ENTITY_IN_BIOME} missions. */
final class MissionEntityKeys {
    private static final int MAX_LENGTH = 96;

    private MissionEntityKeys() {
    }

    /** A {@code namespace:id} tag from {@code bigcasares:custom_entity}; anything else is not a mission key. */
    static boolean isCustomKey(String tag) {
        if (tag == null || tag.isBlank() || tag.length() > MAX_LENGTH) {
            return false;
        }
        int colon = tag.indexOf(':');
        return colon > 0 && colon < tag.length() - 1;
    }
}
