#version 300 es

//{AGENT_EXT}

#ifdef AGENT_EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

uniform vec2 resolution;

#ifdef AGENT_EXT
uniform samplerExternalOES agent_texture;
#else
uniform sampler2D agent_texture;
#endif

layout(location = 0) in vec2 agent_unused;

out vec3 agent_data;
out vec2 uv_left;
out vec2 uv_center;
out vec2 uv_right;

void main() {
    float iid = float(gl_InstanceID);
    vec2 offset = 0.5 / resolution;
    vec2 r = iid / resolution;
    vec2 pos = vec2(fract(r.x), floor(r.x) / resolution.y);
//    agent_data = texture(agent_texture, pos).xyz;

//    agent_data = vec3(agent_unused, 0.0);
    agent_data = vec3(pos, 0.0);
//    agent_data = vec3(1.0);

    gl_Position = vec4(pos * 2.0 - 1.0 + offset, 0.0, 1.0);
    gl_PointSize = 1.0;
}
