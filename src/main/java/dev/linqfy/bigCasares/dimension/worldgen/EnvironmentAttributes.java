package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Minecraft 26.x environment attributes ({@code minecraft:visual/*}, {@code audio/*},
 * {@code gameplay/*}). They are layered dimension type &rarr; biome &rarr; timeline
 * and synced to vanilla clients, so sky, fog, particles and music need no mod.
 *
 * <p>Only attribute ids present in the 26.2 registry are accepted; unknown ids
 * would make the server reject the datapack at startup.</p>
 */
public final class EnvironmentAttributes implements JsonSerializable {

    /** {@code environment_attribute} registry of Minecraft 26.2 (data version 4903). */
    public static final Set<String> KNOWN_26_2 = Set.of(
        "audio/ambient_sounds", "audio/background_music", "audio/firefly_bush_sounds", "audio/music_volume",
        "gameplay/baby_villager_activity", "gameplay/bed_rule", "gameplay/bees_stay_in_hive",
        "gameplay/can_pillager_patrol_spawn", "gameplay/can_start_raid", "gameplay/cat_waking_up_gift_chance",
        "gameplay/creaking_active", "gameplay/eyeblossom_open", "gameplay/fast_lava",
        "gameplay/increased_fire_burnout", "gameplay/monsters_burn", "gameplay/nether_portal_spawns_piglin",
        "gameplay/piglins_zombify", "gameplay/respawn_anchor_works", "gameplay/sky_light_level",
        "gameplay/snow_golem_melts", "gameplay/surface_slime_spawn_chance", "gameplay/turtle_egg_hatch_chance",
        "gameplay/villager_activity", "gameplay/water_evaporates", "visual/ambient_light_color",
        "visual/ambient_particles", "visual/block_light_tint", "visual/cloud_color",
        "visual/cloud_fog_end_distance", "visual/cloud_height", "visual/default_dripstone_particle",
        "visual/fog_color", "visual/fog_end_distance", "visual/fog_start_distance", "visual/moon_angle",
        "visual/moon_phase", "visual/night_vision_color", "visual/sky_color", "visual/sky_fog_end_distance",
        "visual/sky_light_color", "visual/sky_light_factor", "visual/star_angle", "visual/star_brightness",
        "visual/sun_angle", "visual/sunrise_sunset_color", "visual/water_fog_color",
        "visual/water_fog_end_distance", "visual/water_fog_start_distance"
    );

    private final TreeMap<String, JsonElement> values = new TreeMap<>();

    public static EnvironmentAttributes create() {
        return new EnvironmentAttributes();
    }

    public static NamespacedId attributeId(String path) {
        if (!KNOWN_26_2.contains(path)) {
            throw new IllegalArgumentException("Unknown 26.2 environment attribute: " + path);
        }
        return NamespacedId.minecraft(path);
    }

    /** Sets a raw attribute value; prefer the typed helpers below. */
    public EnvironmentAttributes set(String path, Object value) {
        values.put(attributeId(path).asString(), Json.of(value));
        return this;
    }

    public EnvironmentAttributes skyColor(int rgb) { return set("visual/sky_color", Json.rgb(rgb)); }
    public EnvironmentAttributes fogColor(int rgb) { return set("visual/fog_color", Json.rgb(rgb)); }
    public EnvironmentAttributes waterFogColor(int rgb) { return set("visual/water_fog_color", Json.rgb(rgb)); }
    public EnvironmentAttributes skyLightColor(int rgb) { return set("visual/sky_light_color", Json.rgb(rgb)); }
    public EnvironmentAttributes ambientLightColor(int rgb) { return set("visual/ambient_light_color", Json.rgb(rgb)); }
    public EnvironmentAttributes cloudColor(long argb) { return set("visual/cloud_color", Json.argb(argb)); }
    public EnvironmentAttributes cloudHeight(double y) { return set("visual/cloud_height", y); }
    public EnvironmentAttributes skyLightFactor(double factor) { return set("visual/sky_light_factor", factor); }
    public EnvironmentAttributes fogStartDistance(double blocks) { return set("visual/fog_start_distance", blocks); }
    public EnvironmentAttributes fogEndDistance(double blocks) { return set("visual/fog_end_distance", blocks); }
    public EnvironmentAttributes waterFogEndDistance(double blocks) { return set("visual/water_fog_end_distance", blocks); }
    public EnvironmentAttributes starBrightness(double value) { return set("visual/star_brightness", value); }
    public EnvironmentAttributes musicVolume(double volume) { return set("audio/music_volume", volume); }
    public EnvironmentAttributes respawnAnchorWorks(boolean works) { return set("gameplay/respawn_anchor_works", works); }
    public EnvironmentAttributes waterEvaporates(boolean evaporates) { return set("gameplay/water_evaporates", evaporates); }
    public EnvironmentAttributes canStartRaid(boolean allowed) { return set("gameplay/can_start_raid", allowed); }
    public EnvironmentAttributes canPillagerPatrolSpawn(boolean allowed) { return set("gameplay/can_pillager_patrol_spawn", allowed); }
    public EnvironmentAttributes monstersBurn(boolean burn) { return set("gameplay/monsters_burn", burn); }

    /** Ambient particles, e.g. {@code ambientParticle("minecraft:white_ash", 0.01)}. */
    public EnvironmentAttributes ambientParticles(Map<String, Double> particleChances) {
        JsonArray array = new JsonArray();
        new TreeMap<>(particleChances).forEach((particle, chance) -> {
            if (chance <= 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("Particle probability must be in (0, 1]: " + particle);
            }
            array.add(Json.object()
                .put("particle", Json.object().put("type", NamespacedId.parse(particle)).build())
                .put("probability", chance)
                .build());
        });
        values.put(attributeId("visual/ambient_particles").asString(), array);
        return this;
    }

    public EnvironmentAttributes backgroundMusic(String sound, int minDelay, int maxDelay, boolean replaceCurrent) {
        if (minDelay < 0 || maxDelay < minDelay) {
            throw new IllegalArgumentException("Invalid music delay range");
        }
        JsonObject music = Json.object()
            .put("max_delay", maxDelay)
            .put("min_delay", minDelay)
            .putIfPresent("replace_current_music", replaceCurrent ? Boolean.TRUE : null)
            .put("sound", NamespacedId.parse(sound))
            .build();
        values.put(attributeId("audio/background_music").asString(), Json.object().put("default", music).build());
        return this;
    }

    public EnvironmentAttributes ambientSounds(String loop, String moodSound, String additionsSound, double additionsChance) {
        Json.ObjectBuilder builder = Json.object();
        if (additionsSound != null) {
            builder.put("additions", Json.object()
                .put("sound", NamespacedId.parse(additionsSound))
                .put("tick_chance", additionsChance)
                .build());
        }
        if (loop != null) {
            builder.put("loop", NamespacedId.parse(loop));
        }
        builder.put("mood", Json.object()
            .put("block_search_extent", 8)
            .put("offset", 2.0)
            .put("sound", NamespacedId.parse(moodSound == null ? "minecraft:ambient.cave" : moodSound))
            .put("tick_delay", 6000)
            .build());
        values.put(attributeId("audio/ambient_sounds").asString(), builder.build());
        return this;
    }

    /** Bed behaviour: {@code canSleep}/{@code canSetSpawn} take "always", "when_dark" or "never". */
    public EnvironmentAttributes bedRule(String canSleep, String canSetSpawn, boolean explodes, String errorTranslationKey) {
        Json.ObjectBuilder builder = Json.object()
            .put("can_set_spawn", requireBedMode(canSetSpawn))
            .put("can_sleep", requireBedMode(canSleep));
        if (errorTranslationKey != null) {
            builder.put("error_message", Json.object().put("translate", errorTranslationKey).build());
        }
        if (explodes) {
            builder.put("explodes", true);
        }
        values.put(attributeId("gameplay/bed_rule").asString(), builder.build());
        return this;
    }

    private static String requireBedMode(String mode) {
        if (!Set.of("always", "when_dark", "never").contains(mode)) {
            throw new IllegalArgumentException("Unknown bed rule mode: " + mode);
        }
        return mode;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, JsonElement> values() {
        TreeMap<String, JsonElement> copy = new TreeMap<>();
        values.forEach((key, value) -> copy.put(key, value.deepCopy()));
        return copy;
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        values.forEach((key, value) -> object.add(key, value.deepCopy()));
        return object;
    }
}
