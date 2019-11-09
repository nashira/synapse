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

vec4 blur13(vec2 uv, vec2 off1, vec2 off2, vec2 off3) {
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
    vec2 off1 = vec2(1.411764705882353) * direction;
    vec2 off2 = vec2(3.2941176470588234) * direction;
    vec2 off3 = vec2(5.176470588235294) * direction;
    color = blur13(texture_coords0, off1 / resolution, off2 / resolution, off3 / resolution);
}