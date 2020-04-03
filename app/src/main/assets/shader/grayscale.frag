#version 300 es

//{EXT}

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

const vec3 TRANSFORM = vec3(0.299, 0.587, 0.114);

void main() {
    vec4 original = texture(input_texture0, texture_coords0);
    float transformed = dot(original.rgb, TRANSFORM);
    color = vec4(vec3(transformed), 1.0);
}