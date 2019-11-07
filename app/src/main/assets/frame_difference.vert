#version 300 es

uniform mat4 vertex_matrix0;
uniform mat4 texture_matrix0;

layout(location = 0) in vec2 vertex_coords0_a;
layout(location = 1) in vec2 texture_coords0_a;

out vec2 texture_coords0;
out vec2 texture_coords1;

void main() {
    gl_Position = vertex_matrix0 * vec4(vertex_coords0_a, 0.0, 1.0);
    texture_coords1 = texture_coords0_a;
    texture_coords0 = (texture_matrix0 * vec4(texture_coords0_a, 0.0, 1.0)).xy;
}