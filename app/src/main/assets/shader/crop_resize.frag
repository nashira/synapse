#version 300 es

//{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

in vec2 uv;

out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture;
#else
uniform sampler2D input_texture;
#endif


void main() {
    color = texture(input_texture, uv);
}