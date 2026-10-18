package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.DimensionRules;
import dev.linqfy.bigCasares.dimension.DimensionTypeSpec;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.environment.TimelineTrack;
import dev.linqfy.bigCasares.dimension.runtime.DimensionExtension;
import dev.linqfy.bigCasares.dimension.travel.CoordinateMapping;
import dev.linqfy.bigCasares.dimension.travel.PortalDefinition;
import dev.linqfy.bigCasares.dimension.travel.TravelRules;
import dev.linqfy.bigCasares.dimension.worldgen.EnvironmentAttributes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds The Sift's {@link DimensionDefinition} from the imported mod data plus
 * the port's own configuration (seed salt, tides, travel).
 */
public final class SiftDimension {

    public static final DimensionKey KEY = DimensionKey.of(SiftBlocks.NS, "the_sift");
    public static final String DAY_NIGHT = "day_night";
    public static final String TIDES = "tides";
    /** The mod's day/night period (two halves of 12 060 ticks). */
    public static final int DAY_NIGHT_PERIOD = 24_120;
    /** Portal definition id of the Ancient City frame. */
    public static final String ANCIENT_CITY_PORTAL = "ancient_city";
    /** Portal definition id of the portal inside the Sift's main portal structure, which leads out of the dimension. */
    public static final String MAIN_PORTAL = "main_portal";

    private SiftDimension() {
    }

    public static DimensionDefinition create(SiftSettings settings, SiftWorldgen worldgen, List<DimensionExtension> extensions) {
        SiftData data = worldgen.data();
        DimensionDefinition.Builder builder = DimensionDefinition.builder(KEY)
            .displayName("The Sift")
            .seedSalt(settings.seedSalt())
            .type(dimensionType(data))
            .terrain(worldgen::terrain)
            .biomes(worldgen::biomes)
            .cycle(dayNight(data))
            .contributor((context, datapack) -> {
                worldgen.contribute(datapack);
                if (settings.ancientCityRitual()) {
                    SiftAncientCity.contribute(data, datapack);
                }
                SiftAdvancements.contribute(data, settings, datapack);
            })
            .rules(rules(settings))
            .travel(travel(settings));
        if (settings.tides().enabled()) {
            builder.cycle(tides(settings.tides()));
        }
        extensions.forEach(builder::extension);
        return builder.build();
    }

    /** The mod's dimension type; every attribute it sets exists in 26.2. */
    static DimensionTypeSpec dimensionType(SiftData data) {
        JsonObject json = data.json("dimension_type/the_sift.json");
        EnvironmentAttributes attributes = EnvironmentAttributes.create();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("attributes").entrySet()) {
            String path = NamespacedId.parse(entry.getKey()).path();
            if (EnvironmentAttributes.KNOWN_26_2.contains(path)) {
                attributes.set(path, entry.getValue());
            }
        }
        JsonObject spawnLight = json.getAsJsonObject("monster_spawn_light_level");
        return DimensionTypeSpec.builder()
            .heightRange(json.get("min_y").getAsInt(), json.get("height").getAsInt())
            .logicalHeight(json.get("logical_height").getAsInt())
            .ambientLight(json.get("ambient_light").getAsFloat())
            .coordinateScale(json.get("coordinate_scale").getAsDouble())
            .skylight(json.get("has_skylight").getAsBoolean())
            .ceiling(json.get("has_ceiling").getAsBoolean())
            .infiniburn(json.get("infiniburn").getAsString())
            .monsterSpawnLight(spawnLight.get("min_inclusive").getAsInt(), spawnLight.get("max_inclusive").getAsInt(),
                json.get("monster_spawn_block_light_limit").getAsInt())
            .attributes(attributes)
            .ownClock(true)
            .build();
    }

    /** The mod's sift_day_night timeline as a two-phase cycle with identical tracks. */
    static EnvironmentCycle dayNight(SiftData data) {
        JsonObject timeline = data.json("timeline/sift_day_night.json");
        int period = timeline.get("period_ticks").getAsInt();
        int half = period / 2;
        EnvironmentCycle.Builder cycle = EnvironmentCycle.builder(DAY_NIGHT)
            .phase("day", half)
            .phase("night", period - half)
            // Sleeping fast-forwards the Sift clock to its day, like the mod.
            .marker("minecraft:wake_up_from_sleep", 0);
        for (Map.Entry<String, JsonElement> entry : timeline.getAsJsonObject("tracks").entrySet()) {
            JsonObject track = entry.getValue().getAsJsonObject();
            String attribute = NamespacedId.parse(entry.getKey()).path();
            TimelineTrack.Builder builder = TimelineTrack.of(attribute);
            if (track.has("modifier")) {
                builder.modifier(track.get("modifier").getAsString());
            }
            if (track.has("ease")) {
                builder = withEase(builder, track.get("ease"));
            }
            for (JsonElement keyframe : track.getAsJsonArray("keyframes")) {
                JsonObject frame = keyframe.getAsJsonObject();
                builder.at(frame.get("ticks").getAsInt(), frame.get("value"));
            }
            cycle.track(builder.build());
        }
        return cycle.build();
    }

    private static TimelineTrack.Builder withEase(TimelineTrack.Builder builder, JsonElement ease) {
        if (ease.isJsonPrimitive() && ease.getAsString().equals("constant")) {
            return builder.constant();
        }
        return builder.smooth();
    }

    /**
     * Shifting tides: an original interpretation of the announced mechanic, using
     * the Flow &rarr; Endure &rarr; Thrive order described for Minecraft Dungeons II.
     * Each phase spans whole Sift days on the same clock as the day/night timeline,
     * and tints fog/sky multiplicatively so the mod's day/night colours remain.
     */
    static EnvironmentCycle tides(SiftSettings.Tides tides) {
        int flow = tides.flowTicks();
        int endure = tides.endureTicks();
        int thrive = tides.thriveTicks();
        int period = flow + endure + thrive;
        int fade = Math.min(1200, Math.min(flow, Math.min(endure, thrive)) / 4);
        return EnvironmentCycle.builder(TIDES)
            .phase("flow", flow, Map.of("effect", "none"))
            .phase("endure", endure, Map.of("effect", "endure"))
            .phase("thrive", thrive, Map.of("effect", "thrive"))
            .track(TimelineTrack.of("visual/fog_color").modifier("multiply").smooth()
                .at(0, "#ffffff")
                .at(flow - fade, "#ffffff")
                .at(flow, "#b39ad6")
                .at(flow + endure - fade, "#b39ad6")
                .at(flow + endure, "#fff0c2")
                .at(period - fade, "#fff0c2")
                .at(period - 1, "#ffffff")
                .build())
            .track(TimelineTrack.of("visual/fog_end_distance").modifier("multiply").smooth()
                .at(0, 1.0)
                .at(flow - fade, 1.0)
                .at(flow, 0.6)
                .at(flow + endure - fade, 0.6)
                .at(flow + endure, 1.0)
                .at(period - 1, 1.0)
                .build())
            .build();
    }

    static DimensionRules rules(SiftSettings settings) {
        DimensionRules.Builder rules = DimensionRules.builder()
            // The mod's Ichor "does not create infinite sources"; Ichor is water here.
            .gameRule("water_source_conversion", false)
            .spawnSearchRadius(256)
            .worldBorder(0, 0, settings.worldBorderDiameter());
        return rules.build();
    }

    static TravelRules travel(SiftSettings settings) {
        List<PortalDefinition> portals = new ArrayList<>();
        if (settings.craftedPortal().enabled()) {
            portals.add(new PortalDefinition("rift_frame", Set.of(settings.craftedPortal().frameBlock()),
                settings.craftedPortal().activationItem(), true, 2, 21, 3, 21, true, 11,
                "bigcasares:sift_portal_surface", "minecraft:reverse_portal"));
        }
        if (settings.ancientCityRitual()) {
            // The mod's entrance: the Ancient City frame, lit by the Sonorous Deepslate ritual
            // (SiftRitual), never by an item.
            portals.add(new PortalDefinition(ANCIENT_CITY_PORTAL, Set.of("minecraft:reinforced_deepslate"), "", false,
                3, 21, 3, 21, true, 11, "bigcasares:sift_portal_surface", "minecraft:reverse_portal"));
        }
        if (settings.mainPortal()) {
            // The portal of the main portal structure (SiftMainPortal): lit by code once the structure is built,
            // inside the Sift, so walking into it leaves through the framework (return point). Its frame is
            // reinforced siftslate, which shares its mushroom block with other Sift stone: the detector only
            // matches the block type, which is enough because nothing but code lights this definition.
            String frame = SiftBlocks.remapType("reinforced_siftslate").orElseThrow();
            portals.add(new PortalDefinition(MAIN_PORTAL, Set.of(frame), "", false, 3, 21, 3, 21, false, 12,
                "bigcasares:sift_portal_surface", "minecraft:reverse_portal"));
        }
        // The mod keeps Overworld and Sift coordinates 1:1 for portals other than the main portal's entrance
        // (SiftMainPortal sends the Ancient City portal to the main portal instead).
        return new TravelRules(CoordinateMapping.IDENTITY, settings.entryPermission(),
            Duration.ofSeconds(settings.travelCooldownSeconds()), true, 48, portals);
    }
}
