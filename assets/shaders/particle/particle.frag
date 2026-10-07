#include "lib/header.glsl"

// One fragment shader for both particle tiers and both blend batches. The quad is a procedural
// soft circle: a radial gradient costs a few instructions and needs no texture atlas, and the
// particle system is free to batch by blend mode first.

varying vec2 v_corner;
varying vec4 v_color;
varying vec2 v_screenUv;

// The additive batch is gated by the visibility texture, exactly like M6's light pass: an
// emissive particle glows through darkness, but it may never identify a detonation the
// observer cannot see. The alpha batch draws inside the scene pass and is darkened by the fog
// composite instead, so it leaves the gate off.
uniform sampler2D u_visibilityTexture;
uniform float u_visibilityFloor;
uniform float u_visibilityFeather;
uniform int u_gateVisibility;

void main() {
    float radial = length(v_corner);
    float falloff = smoothstep(1.0, 0.55, radial);
    float alpha = falloff * v_color.a;

    if (u_gateVisibility != 0) {
        float visibility = texture2D(u_visibilityTexture, v_screenUv).r;
        float gate = smoothstep(
            u_visibilityFloor,
            u_visibilityFloor + max(u_visibilityFeather, 0.001),
            visibility);
        alpha *= gate;
    }

    if (alpha <= 0.003) {
        discard;
    }

    // Premultiplied output: correct under both the premultiplied-alpha scene batch and the
    // additive post-composite batch.
    gl_FragColor = vec4(v_color.rgb * alpha, alpha);
}
