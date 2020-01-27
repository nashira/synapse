#version 300 es

precision mediump float;

//{AGENT_EXT}
#ifdef AGENT_EXT
#extension GL_OES_EGL_image_external_essl3 : require
uniform samplerExternalOES agent_texture;
#else
uniform sampler2D agent_texture;
#endif

uniform vec2 resolution;

layout(location = 0) in vec2 agent_unused;

out vec3 agent_data;

void main() {
    float iid = float(gl_InstanceID);
    vec2 offset = 0.5 / resolution;
    vec2 r = iid / resolution;
    vec2 uv = vec2(fract(r.x), floor(r.x) / resolution.y) + offset;
    agent_data = texture(agent_texture, uv).rgb;

    gl_Position = vec4(agent_data.xy * 2.0 - 1.0, 0.0, 1.0);
    gl_PointSize = 1.0;
}
