#version 300 es

//{EXT_INPUT}

#ifdef EXT_INPUT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;
precision mediump sampler3D;

//#ifdef EXT_INPUT
//uniform samplerExternalOES input_texture;
//#else
//uniform sampler2D input_texture;
//#endif

uniform sampler3D t3d_texture;
uniform float t3d_layer;
uniform float t3d_depth;
uniform int slice_direction;

in vec2 uv;

out vec4 color;

void main() {
    float z = 1.0;
    if (slice_direction == 0) {
        z = fract(t3d_layer + (1.0 - uv.y) * t3d_depth);
    } else if (slice_direction == 1) {
        z = fract(t3d_layer + uv.y * t3d_depth);
    } else if (slice_direction == 2) {
        z = fract(t3d_layer + uv.x * t3d_depth);
    } else if (slice_direction == 3) {
        z = fract(t3d_layer + (1.0 - uv.x) * t3d_depth);
    }

    color = texture(t3d_texture, vec3(uv, z));
//    color = texture(t3d_texture, uv.xyx);
}