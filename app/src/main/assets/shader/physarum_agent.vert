#version 300 es

#{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

uniform vec2 resolution;

#ifdef EXT
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
    vec2 r = vec2(float(gl_InstanceID)) / resolution;
    vec2 pos = vec2(fract(r.x), floor(r.y) / resolution.y);
    agent_data = texture(agent_texture, pos).xyz;

    gl_Position = vec4(pos, 0.0, 1.0);
}
