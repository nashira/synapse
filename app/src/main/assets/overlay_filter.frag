#version 300 es

#{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

in vec2 texture_coords0;
in vec2 texture_coords1;

out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture0;
#else
uniform sampler2D input_texture0;
#endif

uniform sampler2D input_texture1;

// Converts from pure Hue to linear RGB
vec3 hue_to_rgb(float hue)
{
    float R = abs(hue * 6.0 - 3.0) - 1.0;
    float G = 2.0 - abs(hue * 6.0 - 2.0);
    float B = 2.0 - abs(hue * 6.0 - 4.0);
    return clamp(vec3(R,G,B), 0.0, 1.0);
}

void main() {
    vec4 content = texture(input_texture0, texture_coords0);
    vec4 mask = texture(input_texture1, texture_coords1);
    float alpha = min(mask.r, 0.5);
    color = vec4(hue_to_rgb(mask.r) * alpha + content.rgb * (1.0 - alpha), 1.0);
}