#version 300 es

precision mediump float;

in vec3 agent_data;

layout(location = 0) out vec4 output1;

void main() {
//    output1 = vec4(agent_data, 1.0);
    output1 = vec4(1.0);
}