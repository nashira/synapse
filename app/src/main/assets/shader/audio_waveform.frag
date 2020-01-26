#version 300 es

precision highp float;
precision mediump int;


//{INT_TEXTURE}

in vec2 texture_coords0;

out vec4 color;

#ifdef INT_TEXTURE
uniform mediump isampler2D audio_texture;
#else
uniform sampler2D audio_texture;
#endif

uniform bool isSigned;


void main() {

#ifdef INT_TEXTURE
    ivec4 audio_raw = texture(audio_texture, texture_coords0);
    float audio = float(audio_raw.r) / 32767.0;
#else
    float audio = texture(audio_texture, texture_coords0).r;
#endif
    if (!isSigned) {
        audio = audio * 2.0 - 1.0;
    }
    float vertical = texture_coords0.y * 2.0 - 1.0;
    float falloff = (1.0 - abs(vertical)) * sign(vertical);
    audio = (1.0 - exp(-4.0 * abs(audio))) * sign(audio);

    color = vec4(vec3(audio * falloff * falloff), 1.0);
}