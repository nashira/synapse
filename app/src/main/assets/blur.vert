#version 300 es

precision highp float;

uniform mat4 vertex_matrix0;
uniform mat4 texture_matrix0;

uniform vec2 resolution0;

layout(location = 0) in vec2 vertex_coords0_a;
layout(location = 1) in vec2 texture_coords0_a;

out vec2 texture_coords0;
out vec2 resolution;

void main() {
    gl_Position = vertex_matrix0 * vec4(vertex_coords0_a, 0.0, 1.0);
    texture_coords0 = (texture_matrix0 * vec4(texture_coords0_a, 0.0, 1.0)).xy;
    resolution = (texture_matrix0 * vec4(resolution0, 0.0, 1.0)).xy;
//    resolution = resolution0;
}