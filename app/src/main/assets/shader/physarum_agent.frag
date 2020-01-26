#version 300 es

precision mediump float;

//{ENV_EXT}
#ifdef ENV_EXT
#extension GL_OES_EGL_image_external_essl3 : require
uniform samplerExternalOES env_texture;
#else
uniform sampler2D env_texture;
#endif

//{AGENT_EXT}
#ifdef AGENT_EXT
#extension GL_OES_EGL_image_external_essl3 : require
uniform samplerExternalOES agent_texture;
#else
uniform sampler2D agent_texture;
#endif

in vec2 uv;

layout(location = 0) out vec4 agent_out;


void main() {
    vec3 agent_data = texture(agent_texture, uv).xyz;
    agent_out = vec4(uv, 0.0, 1.0);
}