#version 300 es

//{EXT_INPUT}
//{EXT_LUT}

#if (defined(EXT_INPUT) || defined(EXT_LUT))
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

#ifdef EXT_INPUT
uniform samplerExternalOES input_texture;
#else
uniform sampler2D input_texture;
#endif

#ifdef EXT_LUT
uniform samplerExternalOES lut_texture;
#else
uniform sampler2D lut_texture;
#endif

in vec2 uv;

out vec4 color;

void main() {
    vec4 from = texture(input_texture, uv);
    color = texture(lut_texture, from.rg);
}