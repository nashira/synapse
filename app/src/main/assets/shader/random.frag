#version 300 es

precision highp float;

in vec2 uv;

layout(location = 0) out vec4 random;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898,78.233))) * 43758.5453);
}

void main() {
    random = vec4(rand(uv) > 0.5, rand(uv.yx), rand(uv * uv), 1.0);
}