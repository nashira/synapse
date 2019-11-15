#version 300 es

#{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

#ifndef saturate
#define saturate(v) clamp(v,0.,1.)
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
//vec3 hue_to_rgb(float hue)
//{
//    float R = abs(hue * 6.0 - 3.0) - 1.0;
//    float G = 2.0 - abs(hue * 6.0 - 2.0);
//    float B = 2.0 - abs(hue * 6.0 - 4.0);
//    return clamp(vec3(R,G,B), 0.0, 1.0);
//}

vec3 hsv2rgb(vec3 c) {
    vec4 K=vec4(1.,2./3.,1./3.,3.);
    return c.z*mix(K.xxx,clamp(abs(fract(c.x+K.xyz)*6.-K.w)-K.x, 0., 1.),c.y);
}

void main() {
    vec4 content = texture(input_texture0, texture_coords0);
    vec4 mask = texture(input_texture1, texture_coords1);
//    mask = smoothstep(0.2, 1.0, mask);
    float alpha = min(mask.r, 0.5);
//    float alpha = smoothstep(0.1, 0.5, mask.r) * 0.75;
    vec3 effect = hsv2rgb(vec3(mask.r, 0.8, 1.0));
    color = vec4(effect * alpha + content.rgb * (1.0 - alpha), 1.0);
}