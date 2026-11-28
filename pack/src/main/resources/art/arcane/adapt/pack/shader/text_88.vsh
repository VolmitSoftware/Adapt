#version 330

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
#endif

out vec4 vertexColor;
out vec2 texCoord0;

// Adapt: text set 16384 GUI pixels or more right of the action bar's origin is the HUD corner. Each 1024 pixels of x
// past that is one pixel of y; the rest is x. It is moved to the top left corner, 4 pixels in from both edges.
vec3 adaptHudCorner(vec3 position) {
    if (ProjMat[3][3] != 1.0 || position.x < 12288.0) {
        return position;
    }
    float guiWidth = ceil(2.0 / ProjMat[0][0] - 0.01);
    float guiHeight = ceil(2.0 / abs(ProjMat[1][1]) - 0.01);
    float offset = position.x - floor(guiWidth * 0.5) - 16384.0;
    float row = floor((offset + 256.0) / 1024.0);
    return vec3(4.0 + offset - row * 1024.0, position.y - (guiHeight - 72.0) + 4.0 + row - 64.0, position.z);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(adaptHudCorner(Position), 1.0);

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}
