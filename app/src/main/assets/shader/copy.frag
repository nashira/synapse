#version 300 es

#{EXT}
#{RED}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

in vec2 texture_coords0;

out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture0;
#else
uniform sampler2D input_texture0;
#endif


void main() {
#ifdef RED
    color = vec4(texture(input_texture0, texture_coords0).rrr, 1.0);
#else
    color = texture(input_texture0, texture_coords0);
#endif
}