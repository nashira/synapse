#version 300 es

//{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision highp float;

in vec2 uv;

out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture;
#else
uniform sampler2D input_texture;
#endif

uniform vec3 num_elements;
uniform int frame_count;

#define HASHSCALE1 1.1031
float hash13(vec3 p3)
{
    p3  = fract(p3 * HASHSCALE1);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 original = texture(input_texture, uv);
    vec3 elements = round(original.rgb * num_elements);
    vec3 ng = pow(vec3(2.0), elements.rgb);
    vec2 gridr = floor(uv * ng.r) / (ng.r - 1.0);
    vec2 gridg = floor(uv * ng.g) / (ng.g - 1.0);
    vec2 gridb = floor(uv * ng.b) / (ng.b - 1.0);
    float time = sin(float(frame_count) * 0.00001);
    vec3 f = vec3(
    hash13(vec3(gridr, time)),
    hash13(vec3(gridg, time)),
    hash13(vec3(gridb, time)));
//    vec3 f = vec3(rand(gridr), rand(gridg), rand(gridb));
//    color = vec4(mix(original.rgb, vec3(1.0), f), 1.0);
//    color = vec4(1.0 / ng, 1.0);
//    color = vec4(length(gridr), length(gridg), length(gridb), 1.0);
    color = vec4(f, 1.0);
}