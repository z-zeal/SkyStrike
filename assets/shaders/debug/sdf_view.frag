#include "lib/header.glsl"
#include "lib/sdf.glsl"

varying vec2 v_texCoords;
varying vec2 v_worldPos;

uniform sampler2D u_sdfTexture;
uniform vec2 u_worldSize;

void main() {
    float d = sampleSdf(u_sdfTexture, v_worldPos, u_worldSize);

    // Visualise distance contours: green for outside, red for inside solids
    if (d < 0.0) {
        float factor = clamp(-d / 40.0, 0.0, 1.0);
        gl_FragColor = vec4(0.8 * factor + 0.2, 0.1, 0.1, 1.0);
    } else {
        // Isolines every 20 units
        float isoline = fract(d / 20.0);
        float lineGlow = step(0.9, isoline);
        float factor = clamp(d / 200.0, 0.0, 1.0);
        gl_FragColor = vec4(0.1 + lineGlow * 0.4, 0.2 + 0.6 * factor + lineGlow * 0.4, 0.2, 1.0);
    }
}
