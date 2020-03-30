#version 300 es

precision highp float;

in vec2 uv;

out vec4 color;

uniform sampler2D grid;
uniform vec2 grid_size;

#define HASHSCALE1 .1031
float hash13(vec3 p3)
{
    p3  = fract(p3 * HASHSCALE1);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec4 l = texture(grid, uv + vec2(-grid_size.x, 0.0));
    vec4 lt = texture(grid, uv + vec2(-grid_size.x, grid_size.y));
    vec4 lb = texture(grid, uv + vec2(-grid_size.x, -grid_size.y));
    vec4 r = texture(grid, uv + vec2(grid_size.x, 0.0));
    vec4 rt = texture(grid, uv + vec2(grid_size.x, grid_size.y));
    vec4 rb = texture(grid, uv + vec2(grid_size.x, -grid_size.y));
    vec4 u = texture(grid, uv + vec2(0.0, grid_size.y));
    vec4 d = texture(grid, uv + vec2(0.0, -grid_size.y));
    float c = texture(grid, uv).r;
    float sum = l.r + r.r + u.r + d.r + lt.r + lb.r + rt.r + rb.r;

    float state = c;

    if ((sum > 3.0 || sum < 2.0)) {
        state = 0.0;
    } else if (sum == 3.0) {
        state = 1.0;
    }

    color = vec4(state, 0.0, 0.0, 1.0);
}