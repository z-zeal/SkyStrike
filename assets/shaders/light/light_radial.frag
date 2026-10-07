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
uniform vec2 u_selfBodyCenter;
uniform vec2 u_selfBodyHalfSize;
uniform float u_selfBodyRotation;
uniform int u_isLocalPlayerLight;
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

    // Outside the local-body-only exception below, a light may not affect pixels the observer
    // cannot see. The dim peripheral floor must not become a remote-player glow outside the cone;
    // the visibility field and SDF occlusion remain authoritative for this additive pass.
    float visibility = texture2D(u_visibilityTexture, v_texCoords).r;
    float visibleGate = smoothstep(
        u_visibilityFloor,
        u_visibilityFloor + max(u_visibilityFeather, 0.001),
        visibility);

    // The local player's body is already-known local state, not a remote position. Allow its own
    // light to lift only the rotated body rectangle so the silhouette survives when facing away;
    // the exception cannot reveal another entity or light the surrounding hidden scene.
    if (u_isLocalPlayerLight != 0) {
        vec2 bodyDelta = v_worldPos - u_selfBodyCenter;
        float bodyCos = cos(u_selfBodyRotation);
        float bodySin = sin(u_selfBodyRotation);
        vec2 bodyLocal = vec2(
            bodyCos * bodyDelta.x + bodySin * bodyDelta.y,
            -bodySin * bodyDelta.x + bodyCos * bodyDelta.y);
        float edgeFeather = 1.5;
        float bodyX = 1.0 - smoothstep(
            max(0.0, u_selfBodyHalfSize.x - edgeFeather),
            u_selfBodyHalfSize.x + edgeFeather,
            abs(bodyLocal.x));
        float bodyY = 1.0 - smoothstep(
            max(0.0, u_selfBodyHalfSize.y - edgeFeather),
            u_selfBodyHalfSize.y + edgeFeather,
            abs(bodyLocal.y));
        visibleGate = max(visibleGate, bodyX * bodyY);
    }
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
