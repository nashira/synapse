#version 300 es

//{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision highp float;

const vec3 GRAYSCALE = vec3(0.299, 0.587, 0.114);

in vec2 texture_coords0;

out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture0;
#else
uniform sampler2D input_texture0;
#endif

uniform vec2 step_size;
uniform int mode;
uniform bool grayscale;

vec4 blur5(vec2 uv) {
    vec2 off1 = vec2(1.3333333333333333) * step_size;
    vec4 color = vec4(0.0);
    color += texture(input_texture0, uv) * 0.29411764705882354;
    color += texture(input_texture0, uv + off1) * 0.35294117647058826;
    color += texture(input_texture0, uv - off1) * 0.35294117647058826;
    return color;
}

vec4 blur9(vec2 uv) {
    vec2 off1 = vec2(1.3846153846) * step_size;
    vec2 off2 = vec2(3.2307692308) * step_size;
    vec4 color = vec4(0.0);
    color += texture(input_texture0, uv) * 0.2270270270;
    color += texture(input_texture0, uv + off1) * 0.3162162162;
    color += texture(input_texture0, uv - off1) * 0.3162162162;
    color += texture(input_texture0, uv + off2) * 0.0702702703;
    color += texture(input_texture0, uv - off2) * 0.0702702703;
    return color;
}

vec4 blur13(vec2 uv) {
    vec2 off1 = vec2(1.411764705882353) * step_size;
    vec2 off2 = vec2(3.2941176470588234) * step_size;
    vec2 off3 = vec2(5.176470588235294) * step_size;
    vec4 color = vec4(0.0);
    color += texture(input_texture0, uv) * 0.1964825501511404;
    color += texture(input_texture0, uv + off1) * 0.2969069646728344;
    color += texture(input_texture0, uv - off1) * 0.2969069646728344;
    color += texture(input_texture0, uv + off2) * 0.09447039785044732;
    color += texture(input_texture0, uv - off2) * 0.09447039785044732;
    color += texture(input_texture0, uv + off3) * 0.010381362401148057;
    color += texture(input_texture0, uv - off3) * 0.010381362401148057;
    return color;
}

void main() {
    vec4 original;
    if (mode == 0) {
        original = texture(input_texture0, texture_coords0);
    } else if (mode == 5) {
        original = blur5(texture_coords0);
    } else if (mode == 9) {
        original = blur9(texture_coords0);
    } else if (mode == 13) {
        original = blur13(texture_coords0);
    }
    if (grayscale) {
        color = vec4(vec3(dot(original.rgb, GRAYSCALE)), original.a);
    } else {
        color = original;
    }
}