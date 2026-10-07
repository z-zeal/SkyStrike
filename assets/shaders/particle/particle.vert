#include "lib/header.glsl"
#include "lib/noise.glsl"

#ifdef GL_ES
// The mediump default from lib/header.glsl is not enough here: velocities integrate over seconds
// and land in the thousands of world units. Vertex shaders support highp on every ES2 target.
precision highp float;
#endif

// One particle = four vertices, each carrying the full stateless descriptor. The CPU writes it
// once at spawn; after that only u_time changes.
attribute vec2 a_corner;
attribute vec2 a_spawnPos;
attribute vec2 a_velocity;
attribute float a_spawnTime;
attribute float a_lifetime;
attribute float a_startSize;
attribute float a_endSize;
attribute vec4 a_startColor;
attribute vec4 a_endColor;
attribute float a_gravity;
attribute float a_drag;
attribute float a_turbulence;

uniform float u_time;
uniform vec2 u_camPos;
uniform vec2 u_viewportSize;
uniform float u_noiseScale;

varying vec2 v_corner;
varying vec4 v_color;
varying vec2 v_screenUv;

void main() {
    float age = u_time - a_spawnTime;
    if (age < 0.0 || age > a_lifetime) {
        // Dead slot: collapse the quad to a zero-area triangle outside the clip volume.
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        v_corner = vec2(0.0);
        v_color = vec4(0.0);
        v_screenUv = vec2(0.5);
        return;
    }

    float lifeT = clamp(age / max(a_lifetime, 0.0001), 0.0, 1.0);

    // Analytic ballistic integral with exponential drag, in camera-relative coordinates so the
    // mediump mobile path keeps its precision:
    //   v(t) = (v0 - g/d) * e^(-d t) + g/d
    //   p(t) = p0 + (v0 - g/d) * (1 - e^(-d t)) / d + g * t / d
    vec2 g = vec2(0.0, a_gravity);
    vec2 rel = a_spawnPos - u_camPos;
    if (a_drag < 0.001) {
        rel += a_velocity * age + g * age * age * 0.5;
    } else {
        float d = a_drag;
        float decay = exp(-d * age);
        rel += (a_velocity - g / d) * (1.0 - decay) / d + g * age / d;
    }

    // Divergence-free turbulence, animated in time and tamed late in life.
    if (a_turbulence > 0.0) {
        vec2 noisePos = (rel + u_camPos) * u_noiseScale + vec2(age * 0.35, age * 0.21);
        rel += curlNoise(noisePos) * a_turbulence * (1.0 - lifeT * 0.5);
    }

    float size = mix(a_startSize, a_endSize, lifeT);
    v_color = mix(a_startColor, a_endColor, lifeT);
    v_corner = a_corner;

    // Camera-relative NDC: no view-projection matrix, no large world coordinates in the shader.
    vec2 halfView = u_viewportSize * 0.5;
    vec2 ndc = rel / halfView;
    ndc += a_corner * (size / halfView);
    v_screenUv = (ndc + 1.0) * 0.5;
    gl_Position = vec4(ndc, 0.0, 1.0);
}
