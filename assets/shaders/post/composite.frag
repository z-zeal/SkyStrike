#include "lib/header.glsl"

varying vec2 v_texCoords;

uniform sampler2D u_sceneTexture;
uniform sampler2D u_visibilityTexture;
uniform sampler2D u_lightTexture;
uniform float u_ambientFloor;
uniform int u_hasLight;

void main() {
    vec4 scene = texture2D(u_sceneTexture, v_texCoords);
    float vis = texture2D(u_visibilityTexture, v_texCoords).r;

    float fog = max(vis, u_ambientFloor);
    vec3 result = scene.rgb * fog;

    if (u_hasLight != 0) {
        vec4 light = texture2D(u_lightTexture, v_texCoords);
        result += light.rgb;
    }

    gl_FragColor = vec4(result, scene.a);
}
