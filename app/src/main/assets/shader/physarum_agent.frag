#version 300 es

#{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

#ifdef EXT
uniform samplerExternalOES env_texture;
#else
uniform sampler2D env_texture;
#endif

layout(location = 0) out vec4 agent_out;

in vec3 agent_data;
in vec2 uv_left;
in vec2 uv_center;
in vec2 uv_right;

void main() {
    agent_out = vec4(gl_FragCoord.xy, 0.0, 1.0);
}