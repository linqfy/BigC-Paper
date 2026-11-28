#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
out vec4 vertexColor;
out vec2 texCoord0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
in ivec2 UV2;
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
#endif

#ifdef IS_GUI
int panelOffset(inout ivec3 rgb, out int face) {
    int key = (rgb.r << 16) | (rgb.g << 8) | rgb.b;
    face = -1;
    if (key >= 0xfdfaf0 && key <= 0xfdfaf3) {
        rgb = ivec3(255);
        return key - 0xfdfaf0;
    }
    if (key >= 0xfdfaf4 && key <= 0xfdfaff) {
        face = (key - 0xfdfaf4) / 4;
        rgb = ivec3(255);
        return (key - 0xfdfaf4) % 4;
    }
    switch (key) {
/* PANEL_COLOURS */
            rgb ^= ivec3(1, 2, 4);
            return 0;
        default:
            return -1;
    }
}

// The inventory's block model view (display "gui": rotation 30/225/0, scale 0.625) in a 16-pixel slot.
const vec2 CUBE_TOP = vec2(8.0, 0.135);
const vec2 CUBE_LEFT = vec2(0.929, 3.67);
const vec2 CUBE_RIGHT = vec2(15.071, 3.67);
const vec2 CUBE_FRONT = vec2(8.0, 7.205);
const vec2 CUBE_LEFT_BOTTOM = vec2(0.929, 12.33);
const vec2 CUBE_FRONT_BOTTOM = vec2(8.0, 15.865);
const vec2 CUBE_RIGHT_BOTTOM = vec2(15.071, 12.33);

// Glyph corners arrive top-left, bottom-left, bottom-right, top-right.
vec2 cubeCorner(int face, int corner) {
    if (face == 0) {
        // Rotated like the vanilla inventory: a barrel's lid notch lands on the upper right edge.
        return corner == 0 ? CUBE_RIGHT : corner == 1 ? CUBE_TOP : corner == 2 ? CUBE_LEFT : CUBE_FRONT;
    }
    if (face == 1) {
        return corner == 0 ? CUBE_LEFT : corner == 1 ? CUBE_LEFT_BOTTOM : corner == 2 ? CUBE_FRONT_BOTTOM : CUBE_FRONT;
    }
    return corner == 0 ? CUBE_FRONT : corner == 1 ? CUBE_FRONT_BOTTOM : corner == 2 ? CUBE_RIGHT_BOTTOM : CUBE_RIGHT;
}
#endif

void main() {
    vec3 position = Position;
    vertexColor = Color;
#ifdef IS_GUI
    ivec3 rgb = ivec3(round(Color.rgb * 255.0));
    int face = -1;
    // Panel glyphs sit 40/256 of a GUI pixel right of the grid; a run sits (48 + 8 * (row % 4) + power) / 256 right.
    float shift = fract(position.x) * 256.0;
    int kind = int(shift);
    int offset = shift == float(kind) && (kind == 40 || kind >= 48 && kind < 80) ? panelOffset(rgb, face) : -1;
    if (offset >= 0) {
        vec2 viewport = abs(2.0 / vec2(ProjMat[0][0], ProjMat[1][1]));
        float scale = min(viewport.x / 232.0, viewport.y / 130.0);
        position.x -= float(kind) / 256.0;
        position.y += float(offset + 2);
        if (kind >= 48) {
            // A run's 128 x 64 cell is solid: cut the quad to the run's own row and width, so neighbouring runs
            // share exact edges instead of meeting at texel borders the sampler can round either way.
            int row = (kind - 48) / 8;
            int width = 1 << ((kind - 48) % 8);
            int corner = gl_VertexID % 4;
            position.y += corner == 0 || corner == 3 ? float(row) : float(row + 1 - 64);
            if (corner >= 2) position.x += float(width - 128);
        }
        vertexColor.rgb = vec3(rgb) / 255.0;
        if (face >= 0) {
            int corner = gl_VertexID % 4;
            vec2 local = corner == 0 ? vec2(0.0) : corner == 1 ? vec2(0.0, 16.0) : corner == 2 ? vec2(16.0) : vec2(16.0, 0.0);
            position.xy += cubeCorner(face, corner) - local;
            // The vanilla inventory lights the top fully, the right face less and the left face least.
            vertexColor.rgb *= face == 0 ? 1.0 : face == 1 ? 0.6 : 0.8;
        }
        position.xy = viewport * 0.5 + (position.xy - viewport * 0.5) * scale;
    }
#endif
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor *= sample_lightmap(Sampler2, UV2);
#endif
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    texCoord0 = UV0;
}
