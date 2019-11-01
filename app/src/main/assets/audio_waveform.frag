#version 300 es

precision mediump float;

in vec2 texture_coords0;

out vec4 color;

uniform sampler2D audio_texture;
uniform bool isSigned;


void main() {
    vec4 audio = texture(audio_texture, texture_coords0);
    if (!isSigned) {
        audio.r = audio.r * 2.0 - 1.0;
    }
    float vertical = texture_coords0.y * 2.0 - 1.0;
    vertical = (1.0 - abs(vertical)) * sign(vertical);

    color = vec4(audio.rrr * vertical, 1.0);
}