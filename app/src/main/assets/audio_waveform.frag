#version 300 es

precision highp float;


#{INT_TEXTURE}

in vec2 texture_coords0;

out vec4 color;

#ifdef INT_TEXTURE
uniform isampler2D audio_texture;
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
    float falloff = 1.0 - abs(vertical);
    vertical = (falloff * falloff) * sign(vertical);

    color = vec4(vec3(audio)  * vertical * 2.0, 1.0);
}