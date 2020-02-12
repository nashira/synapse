#version 300 es

//{EXT_POS}
#ifdef EXT_POS
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision highp float;

#ifdef EXT_POS
uniform samplerExternalOES pos_texture;
#else
uniform sampler2D pos_texture;
#endif

uniform vec2 resolution;
uniform mat4 pos_matrix;


layout(location = 0) in vec3 position;


void main() {
    float iid = float(gl_InstanceID);
    vec2 offset = 0.5 / resolution;
    vec2 r = iid / resolution;
    vec2 uv = vec2(fract(r.x), floor(r.x) / resolution.y) + offset;
    vec4 data = texture(pos_texture, uv);

    gl_Position = pos_matrix * vec4(position + data.xyz, 1.0);
//    gl_PointSize = 3.0;
}
