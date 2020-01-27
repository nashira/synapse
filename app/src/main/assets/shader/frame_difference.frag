#version 300 es

//{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

in vec2 texture_coords0;
in vec2 texture_coords1;

layout(location = 0) out vec4 currentFrame;
layout(location = 1) out vec4 frameDiff;

#ifdef EXT
uniform samplerExternalOES input_texture0;
#else
uniform sampler2D input_texture0;
#endif
uniform sampler2D input_texture1;


void main() {
    vec4 frame = texture(input_texture0, texture_coords0);
    currentFrame = frame;

    vec4 lastFrame = texture(input_texture1, texture_coords1);

    frameDiff = abs(frame - lastFrame);
}