#version 300 es

#{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

in vec2 texture_coords0;
in vec2 texture_coords1;

layout(location = 0) out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture0;
#else
uniform sampler2D input_texture0;
#endif
uniform sampler2D input_texture1;

uniform float multiply_factor;
uniform float accumulate_factor;

void main() {
    vec4 lastFrame = texture(input_texture1, texture_coords1);
    vec4 frame = texture(input_texture0, texture_coords0);

    color = lastFrame * multiply_factor + frame * accumulate_factor;
}