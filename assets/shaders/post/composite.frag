#include "lib/header.glsl"

varying vec2 v_texCoords;

uniform sampler2D u_sceneTexture;
uniform sampler2D u_visibilityTexture;
uniform sampler2D u_lightTexture;
uniform float u_ambientFloor;
uniform float u_peripheralFloor;
uniform float u_visibilityGamma;
uniform int u_hasLight;

float displayVisibility(float visibility) {
    // Keep the dim peripheral floor and absolute darkness intact, but lift midtones in the
    // observer's actual sight field so it reads clearly instead of looking translucent.
    if (visibility <= u_peripheralFloor) {
        return visibility;
    }
    float coneVisibility = (visibility - u_peripheralFloor)
        / max(1.0 - u_peripheralFloor, 0.001);
    return u_peripheralFloor
        + (1.0 - u_peripheralFloor) * pow(clamp(coneVisibility, 0.0, 1.0), u_visibilityGamma);
}

void main() {
    vec4 scene = texture2D(u_sceneTexture, v_texCoords);
    float vis = displayVisibility(texture2D(u_visibilityTexture, v_texCoords).r);

    // The light buffer is the illumination the sight field is lit by, not a colour drawn on top
    // of it: it is added to the field and multiplies the scene with it, so a lit surface brightens
    // in its own colour instead of washing out into a white disc, and an unlit pixel stays black
    // however bright the lamp is. The light pass has already rejected pixels outside the sight
    // field and behind SDF occluders, so the multiply cannot reveal hidden geometry.
    vec3 illumination = vec3(max(vis, u_ambientFloor));
    if (u_hasLight != 0) {
        illumination += texture2D(u_lightTexture, v_texCoords).rgb;
    }

    gl_FragColor = vec4(scene.rgb * illumination, scene.a);
}
