#include "lib/header.glsl"
#include "lib/sdf.glsl"

varying vec2 v_texCoords;
varying vec2 v_worldPos;

uniform sampler2D u_sdfTexture;
uniform sampler2D u_visibilityTexture;
uniform vec2 u_worldSize;
uniform vec2 u_lightPos;
uniform float u_lightRadius;
uniform vec3 u_lightColor;
uniform float u_lightIntensity;
uniform float u_falloff;
uniform float u_shadowK;
uniform float u_visibilityFloor;
uniform float u_visibilityFeather;
uniform int u_castsShadow;
uniform int u_maxMarchSteps;

void main() {
    float distanceToLight = length(v_worldPos - u_lightPos);
    float radialDistance = distanceToLight / max(u_lightRadius, 0.001);
    if (radialDistance >= 1.0) {
        discard;
    }

    // A continuous radial profile with a soft zero at the edge of the light's circle.
    float radial = pow(max(0.0, 1.0 - radialDistance), u_falloff);

    // A light may not make pixels visible that the local observer cannot see. In particular,
    // the visibility pass's dim peripheral floor must not become a player-position glow outside
    // the cone; opaque SDF occlusion and the cone remain authoritative for this additive pass.
    float visibility = texture2D(u_visibilityTexture, v_texCoords).r;
    float visibleGate = smoothstep(
        u_visibilityFloor,
        u_visibilityFloor + max(u_visibilityFeather, 0.001),
        visibility);
    if (visibleGate <= 0.0) {
        discard;
    }

    float shadow = 1.0;
    if (u_castsShadow != 0) {
        shadow = sdfSoftShadow(
            u_sdfTexture,
            u_lightPos,
            v_worldPos,
            u_worldSize,
            u_shadowK,
            u_maxMarchSteps);
    }

    float energy = radial * visibleGate * shadow * u_lightIntensity;
    gl_FragColor = vec4(u_lightColor * energy, energy);
}
