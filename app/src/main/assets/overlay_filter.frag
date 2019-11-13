#version 300 es

#{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

in vec2 texture_coords0;
in vec2 texture_coords1;

out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture0;
#else
uniform sampler2D input_texture0;
#endif

uniform sampler2D input_texture1;

void main() {
    vec4 content = texture(input_texture0, texture_coords0);
    vec4 mask = texture(input_texture1, texture_coords1);
    color = vec4(vec3(0.0, mask.r, 0.0) + content.rgb * (1.0 - mask.r), 1.0);
}