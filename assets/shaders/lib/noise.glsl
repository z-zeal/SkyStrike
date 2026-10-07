// Hash, value noise and curl noise helpers shared by the particle and post shaders.
//
// Everything here is deterministic and side-effect free: the particle system seeds its layout on
// the CPU and the shaders only add divergence-free wobble on top of it.

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Smooth value noise in [0, 1]: four hashed corners, cosine-smoothed.
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Divergence-free curl of a scalar noise potential: (dψ/dy, -dψ/dx).
// Divergence-free matters: the turbulence pushes particles around without ever
// compressing or expanding the cloud, which is what keeps smoke billowing
// instead of clumping.
vec2 curlNoise(vec2 p) {
    float e = 0.1;
    float dY = (valueNoise(p + vec2(0.0, e)) - valueNoise(p - vec2(0.0, e))) / (2.0 * e);
    float dX = (valueNoise(p + vec2(e, 0.0)) - valueNoise(p - vec2(e, 0.0))) / (2.0 * e);
    return vec2(dY, -dX) * 0.5;
}
