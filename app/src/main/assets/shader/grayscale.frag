#version 300 es

#{EXT}

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

const vec3 TRANSFORM = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec4 original = texture(input_texture0, texture_coords0);
    vec3 transformed = original.rgb * TRANSFORM;
    color = vec4(vec3(transformed.r + transformed.b + transformed.g), 1.0);
}