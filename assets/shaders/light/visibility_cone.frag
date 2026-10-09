#include "lib/header.glsl"
#include "lib/camera.glsl"
#include "lib/sdf.glsl"

varying vec2 v_texCoords;
varying vec2 v_worldPos;

uniform sampler2D u_sdfTexture;
uniform vec2 u_worldSize;
uniform vec2 u_observerPos;
uniform float u_aimAngle;
uniform float u_reach;
uniform float u_coneHalfAngle;
uniform float u_featherAngle;
uniform float u_brightness;
uniform float u_peripheralFloor;
uniform float u_shadowK;
uniform int u_maxMarchSteps;

uniform int u_smokeCount;
uniform vec4 u_smokeCircles[8]; // xy = center, z = radius, w = density

const float PI = 3.141592653589793;

float calculateSmokeAttenuation(vec2 p0, vec2 p1) {
    float totalAtten = 0.0;
    vec2 seg = p1 - p0;
    float segLenSq = dot(seg, seg);

    for (int i = 0; i < 8; i++) {
        if (i >= u_smokeCount) break;
        vec2 center = u_smokeCircles[i].xy;
        float radius = u_smokeCircles[i].z;
        float density = u_smokeCircles[i].w;

        if (radius <= 0.0 || density <= 0.0) continue;

        float t = 0.0;
        if (segLenSq > 0.001) {
            t = clamp(dot(center - p0, seg) / segLenSq, 0.0, 1.0);
        }
        vec2 closestPoint = p0 + seg * t;
        float distToCenter = length(center - closestPoint);
        if (distToCenter < radius) {
            totalAtten += density * (1.0 - distToCenter / radius);
        }
    }
    return totalAtten;
}

void main() {
    vec2 toFrag = v_worldPos - u_observerPos;
    float dist = length(toFrag);

    // 1. Distance culling and continuous quadratic falloff
    if (dist > u_reach) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float u = dist / u_reach;
    float distFactor = max(0.0, 1.0 - u * u);

    // 2. Angular feathering across cone boundary with peripheral floor
    float fragAngle = atan(toFrag.y, toFrag.x);
    float deltaAngle = abs(mod(fragAngle - u_aimAngle + PI, 2.0 * PI) - PI);
    float innerAngle = max(0.0, u_coneHalfAngle - u_featherAngle);

    float angFactor;
    if (deltaAngle <= innerAngle) {
        angFactor = 1.0;
    } else if (deltaAngle >= u_coneHalfAngle) {
        angFactor = u_peripheralFloor;
    } else {
        float t = (deltaAngle - innerAngle) / max(0.001, u_coneHalfAngle - innerAngle);
        float smoothT = t * t * (3.0 - 2.0 * t);
        angFactor = mix(1.0, u_peripheralFloor, smoothT);
    }

    // 3. SDF soft shadow occlusion
    float shadow = sdfSoftShadow(u_sdfTexture, u_observerPos, v_worldPos, u_worldSize, u_shadowK, u_maxMarchSteps);

    // 4. Smoke volume attenuation
    float smokeAtten = calculateSmokeAttenuation(u_observerPos, v_worldPos);

    float vis = distFactor * angFactor * shadow;
    vis = max(0.0, vis - smokeAtten);

    // Per-observer brightness: a gadget device's cone is dimmer than a player's (mechanics §7.1).
    vis *= u_brightness;

    gl_FragColor = vec4(vis, vis, vis, 1.0);
}
