#include "lib/header.glsl"
#include "lib/camera.glsl"
#include "lib/sdf.glsl"
#include "lib/door_occluders.glsl"

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
// Sized by VisionConfig.MAX_SMOKE_VOLUMES (16): three whole five-circle SmokeCloud clusters
// plus a slot to spare. SmokeField fills it oldest-first, whole clouds only, identically on the
// server's sight queries; the zero-filled tail keeps u_smokeCount the only bound the loop obeys.
uniform vec4 u_smokeCircles[16]; // xy = center, z = radius, w = density

const float PI = 3.141592653589793;

float calculateSmokeAttenuation(vec2 p0, vec2 p1) {
    float totalAtten = 0.0;
    vec2 seg = p1 - p0;
    // Use a shared component scale for the projection so neither dot product squares raw
    // world-space distances. For sub-unit segments the scale stays at 1, preserving the original
    // short-segment guard below.
    float segScale = max(max(abs(seg.x), abs(seg.y)), 1.0);
    vec2 scaledSeg = seg / segScale;
    float segLenSq = dot(scaledSeg, scaledSeg);

    for (int i = 0; i < 16; i++) {
        if (i >= u_smokeCount) break;
        vec2 center = u_smokeCircles[i].xy;
        float radius = u_smokeCircles[i].z;
        float density = u_smokeCircles[i].w;

        if (radius <= 0.0 || density <= 0.0) continue;

        float t = 0.0;
        if (segLenSq > 0.001) {
            vec2 scaledCenterDelta = (center - p0) / segScale;
            t = clamp(dot(scaledCenterDelta, scaledSeg) / segLenSq, 0.0, 1.0);
        }
        vec2 closestPoint = p0 + seg * t;
        vec2 centerDelta = center - closestPoint;
        // Reject circles whose centre is outside either radius slab before length() so its
        // remaining components are at most one radius (the bounded distance the test needs).
        if (abs(centerDelta.x) < radius && abs(centerDelta.y) < radius) {
            float distToCenter = length(centerDelta / max(radius, 0.001));
            if (distToCenter < 1.0) {
                totalAtten += density * (1.0 - distToCenter);
            }
        }
    }
    return totalAtten;
}

void main() {
    vec2 toFrag = v_worldPos - u_observerPos;
    // Scale both values before squaring. The arena diagonal remains small at 1/2048 scale,
    // including observers with the 70-unit device bubble, while the ratio preserves the same
    // circular reach and quadratic falloff.
    vec2 scaledToFrag = toFrag / 2048.0;
    float scaledReach = u_reach / 2048.0;
    float scaledReachSq = scaledReach * scaledReach;
    float scaledDistSq = dot(scaledToFrag, scaledToFrag);

    // 1. Distance culling and continuous quadratic falloff
    if (scaledDistSq > scaledReachSq) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float distFactor = max(0.0, 1.0 - scaledDistSq / scaledReachSq);

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

    // 3. SDF soft shadow occlusion. Doors are absent from that same SDF on purpose (map plan
    //    D3: the bake is static, a door is not), so the closed door rectangles gate the ray
    //    analytically beside it: a shut door blocks sight exactly as hard as the wall it sits in.
    float shadow = sdfSoftShadow(u_sdfTexture, u_observerPos, v_worldPos, u_worldSize, u_shadowK, u_maxMarchSteps);
    shadow *= doorOccluderVisibility(u_observerPos, v_worldPos);

    // 4. Smoke volume attenuation
    float smokeAtten = calculateSmokeAttenuation(u_observerPos, v_worldPos);

    float vis = distFactor * angFactor * shadow;
    vis = max(0.0, vis - smokeAtten);

    // Per-observer brightness: a gadget device's cone is dimmer than a player's (mechanics §7.1).
    vis *= u_brightness;

    gl_FragColor = vec4(vis, vis, vis, 1.0);
}
