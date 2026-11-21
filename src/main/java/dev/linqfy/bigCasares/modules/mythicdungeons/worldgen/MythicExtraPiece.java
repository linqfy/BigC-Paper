package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.util.Objects;

/**
 * One extra template for a culture's dungeon, see {@link MythicTemplateSource}.
 *
 * <p>{@code name} becomes part of the template id ({@code bigc_myth:<culture>/extra/<role>/<name>})
 * and must be a lowercase resource path, unique per culture and role. {@code weight} (1..150) is the
 * pool weight next to the built-in pieces ({@link MythicDungeonDatapack#BUILT_IN_WEIGHT} each).
 * Connector names are the {@code name} field of jigsaw blocks inside the template.</p>
 *
 * <p>Roles and how jigsaws are rewritten (horizontal = facing north/south/east/west). Where a piece goes
 * depends on the culture's {@link MythicDungeonTheme.Layout}; extra pieces never join the spine (the
 * guaranteed path to the arena), only branches and starts:</p>
 * <ul>
 *     <li>{@link Role#SIDE_ROOM}: dead-end room in the branch pool. {@code entryConnector} must be a
 *     horizontal jigsaw; it becomes the entry. Along the face that holds the entry, the template may extend
 *     at most {@link MythicDungeonDatapack#SIDE_ROOM_HALF_WIDTH} blocks to either side of the connector (so
 *     at most 9 wide). Every other jigsaw is sealed. Side rooms carry no loot (chests are removed).</li>
 *     <li>{@link Role#CORRIDOR}: branch corridor. {@code entryConnector} and {@code exitConnector} must be
 *     horizontal jigsaws facing opposite ways, the exit 0..{@link MythicDungeonDatapack#CORRIDOR_MAX_DROP}
 *     blocks below the entry; exit and other horizontal jigsaws (not on the entry's face) open onto the
 *     branch pool. Surface cultures keep one floor level: they skip corridors that drop and only open side
 *     jigsaws at the entry's level.</li>
 *     <li>{@link Role#SURFACE_ENTRANCE}: alternative start of an underground culture. {@code entryConnector}
 *     must face down ({@code down_*}) in the bottom layer within
 *     {@link MythicDungeonDatapack#SURFACE_ENTRANCE_MAX_CONNECTOR_OFFSET} blocks of the template centre on each
 *     horizontal axis; the whole underground dungeon hangs below it. The builder clears a 3x3, 4-tall opening
 *     above the connector; other jigsaws are neutralised. Surface cultures skip these pieces.</li>
 *     <li>{@link Role#FULL_DUNGEON}: a complete building. Its {@code entryConnector} faces down in the bottom
 *     layer within {@link MythicDungeonDatapack#FULL_DUNGEON_MAX_CONNECTOR_OFFSET} of the centre. Underground
 *     cultures use it as an alternative start (like a surface entrance; its horizontal jigsaws become open
 *     gates). Surface cultures use it as a dead-end wing building in the branch pool: its first horizontal
 *     jigsaw (template order) is the entry, the others become open gates and the descent closes.</li>
 * </ul>
 * {@code exitConnector} is only used (and required) by {@link Role#CORRIDOR}. Every template must stay within
 * 48 blocks per axis, use only {@code minecraft:} ids, contain no lodestone and not have the arena's 35x35
 * footprint (all of these throw). After the rewrite every piece is made to follow the playtest rules
 * ({@link MythicPassability}): connector doorways are opened to 3x4, spawners without room for large mobs and
 * unreachable spawners or chests are removed, at most one chest stays; a piece whose connectors a player
 * cannot walk between (or, for a start, whose descent cannot be walked to from its edge), or that has no
 * 5-wide, 5-high passage between its connectors, is left out of the pack.
 */
public record MythicExtraPiece(
    String name,
    StructureTemplate template,
    Role role,
    String entryConnector,
    String exitConnector,
    int weight
) {

    public enum Role {
        SIDE_ROOM, CORRIDOR, SURFACE_ENTRANCE, FULL_DUNGEON
    }

    public MythicExtraPiece {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[a-z0-9_]+(/[a-z0-9_]+)*")) {
            throw new IllegalArgumentException("Invalid extra piece name: " + name);
        }
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(entryConnector, "entryConnector");
        if (role == Role.CORRIDOR && exitConnector == null) {
            throw new IllegalArgumentException("Corridor " + name + " needs an exit connector");
        }
        if (weight < 1 || weight > 150) {
            throw new IllegalArgumentException("Extra piece weight must be within 1..150");
        }
    }
}
