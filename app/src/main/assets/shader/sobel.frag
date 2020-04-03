#version 300 es

//{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

in vec2 uv;

out vec4 color;

#ifdef EXT
uniform samplerExternalOES input_texture;
#else
uniform sampler2D input_texture;
#endif

uniform int frame_count;
uniform vec2 input_size;


void main() {
    vec4 original = texture(input_texture, uv);
    vec3 TL = texture(input_texture, uv + vec2(-1, 1) * input_size.xy).rgb;
    vec3 TM = texture(input_texture, uv + vec2(0, 1) * input_size.xy).rgb;
    vec3 TR = texture(input_texture, uv + vec2(1, 1) * input_size.xy).rgb;

    vec3 ML = texture(input_texture, uv + vec2(-1, 0) * input_size.xy).rgb;
    vec3 MR = texture(input_texture, uv + vec2(1, 0) * input_size.xy).rgb;

    vec3 BL = texture(input_texture, uv + vec2(-1, -1) * input_size.xy).rgb;
    vec3 BM = texture(input_texture, uv + vec2(0, -1) * input_size.xy).rgb;
    vec3 BR = texture(input_texture, uv + vec2(1, -1) * input_size.xy).rgb;

    vec3 GradX = -TL + TR - 2.0 * ML + 2.0 * MR - BL + BR;
    vec3 GradY = TL + 2.0 * TM + TR - BL - 2.0 * BM - BR;


    /* vec2 gradCombo = vec2(GradX.r, GradY.r) + vec2(GradX.g, GradY.g) + vec2(GradX.b, GradY.b);
     
     fragColor = vec4(gradCombo.r, gradCombo.g, 0, 1);*/
    color = vec4(
        length(vec2(GradX.r, GradY.r)),
        length(vec2(GradX.g, GradY.g)),
        length(vec2(GradX.b, GradY.b)),
        1.0
    );
}