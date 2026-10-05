#include "lib/header.glsl"

attribute vec4 a_position;
attribute vec2 a_texCoord0;

varying vec2 v_texCoords;
varying vec2 v_worldPos;

uniform vec2 u_camPos;
uniform vec2 u_viewportSize;

void main() {
    v_texCoords = a_texCoord0;
    gl_Position = vec4(a_position.xy, 0.0, 1.0);
    v_worldPos = u_camPos + a_position.xy * (u_viewportSize * 0.5);
}
