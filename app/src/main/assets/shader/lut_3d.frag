#version 300 es

//{EXT_INPUT}

#ifdef EXT_INPUT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;
precision mediump sampler3D;
precision mediump sampler2D;

#ifdef EXT_INPUT
uniform samplerExternalOES input_texture;
#else
uniform sampler2D input_texture;
#endif

uniform mat4 lut_matrix;
uniform sampler3D lut_texture;

in vec2 uv;

out vec4 color;

void main() {
    vec4 from = texture(input_texture, uv);
    vec3 lookup = (lut_matrix * vec4(from.xyz, 1.0)).xyz;
    color = texture(lut_texture, lookup);
}