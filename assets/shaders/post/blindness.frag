#include "lib/header.glsl"
#include "lib/noise.glsl"

// The flashbang whiteout (build plan M7 §8.3, effects plan §8). Drawn over the composited frame
// with premultiplied alpha: white at the centre, deepening to a dim blue-grey rim as the flash
// intensifies, with animated grain so a full whiteout does not read as a flat fill. The intensity
// itself is the shared StunMath exponential curve, computed on the CPU from the player's blind
// state.

varying vec2 v_texCoords;

uniform float u_intensity; // 0..1, exponential recovery
uniform float u_time;      // effect time, for the grain

void main() {
    if (u_intensity <= 0.001) {
        discard;
    }

    float vig = smoothstep(0.45, 1.05, distance(v_texCoords, vec2(0.5)));
    vec3 wash = mix(vec3(1.0), vec3(0.62, 0.64, 0.72), vig);

    float grain = hash21(v_texCoords * vec2(1920.0, 1080.0) + vec2(u_time * 61.7)) - 0.5;
    wash += grain * 0.05;

    float alpha = u_intensity * (0.94 - 0.45 * vig);
    gl_FragColor = vec4(wash * alpha, alpha);
}
