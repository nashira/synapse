#version 300 es

#{EXT}

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

vec4 blur5(vec2 uv, vec2 off1) {
    vec4 color = vec4(0.0);
    color += texture(input_texture0, uv) * 0.29411764705882354;
    color += texture(input_texture0, uv + off1) * 0.35294117647058826;
    color += texture(input_texture0, uv - off1) * 0.35294117647058826;
    return color;
}

void main() {
    vec2 off1 = vec2(1.3333333333333333) * direction;
    color = blur5(texture_coords0, off1 / resolution);
}