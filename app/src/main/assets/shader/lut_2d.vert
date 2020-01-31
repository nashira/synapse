#version 300 es

precision mediump float;

uniform mat4 input_matrix;

layout(location = 0) in vec2 vertex_coords;
layout(location = 1) in vec2 texture_coords;

out vec2 uv;

void main() {
    gl_Position = vec4(vertex_coords, 0.0, 1.0);
    uv = (input_matrix * vec4(texture_coords, 0.0, 1.0)).xy;
}
