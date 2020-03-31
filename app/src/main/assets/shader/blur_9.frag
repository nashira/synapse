#version 300 es

//{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision highp float;

in vec2 texture_coords0;
in vec2 resolution;

out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture0;
#else
uniform sampler2D input_texture0;
#endif

uniform vec2 direction;

vec4 blur9(vec2 uv, vec2 off1, vec2 off2) {
    vec4 color = vec4(0.0);
    color += texture(input_texture0, uv) * 0.2270270270;
    color += texture(input_texture0, uv + off1) * 0.3162162162;
    color += texture(input_texture0, uv - off1) * 0.3162162162;
    color += texture(input_texture0, uv + off2) * 0.0702702703;
    color += texture(input_texture0, uv - off2) * 0.0702702703;
    return color;
}

void main() {
    vec2 off1 = vec2(1.3846153846) * direction;
    vec2 off2 = vec2(3.2307692308) * direction;
    color = blur9(texture_coords0, off1 / resolution, off2 / resolution);
}