#include "lib/header.glsl"

// The CPU tier's vertex shader: pre-transformed quads. The CPU particle system integrates and
// collides on the CPU and writes each casing's centre, size and colour directly, so this shader
// only places the quad. It shares the fragment shader with the GPU tier.

attribute vec2 a_corner;
attribute vec2 a_center;
attribute float a_size;
attribute vec4 a_color;

uniform vec2 u_camPos;
uniform vec2 u_viewportSize;

varying vec2 v_corner;
varying vec4 v_color;
varying vec2 v_screenUv;

void main() {
    vec2 halfView = u_viewportSize * 0.5;
    vec2 rel = a_center - u_camPos;
    vec2 ndc = rel / halfView + a_corner * (a_size / halfView);
    v_corner = a_corner;
    v_color = a_color;
    v_screenUv = (ndc + 1.0) * 0.5;
    gl_Position = vec4(ndc, 0.0, 1.0);
}
