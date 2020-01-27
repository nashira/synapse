#version 300 es

precision mediump float;

//{ENV_EXT}
#ifdef ENV_EXT
#extension GL_OES_EGL_image_external_essl3 : require
uniform samplerExternalOES env_texture;
#else
uniform sampler2D env_texture;
#endif

//{AGENT_EXT}
#ifdef AGENT_EXT
#extension GL_OES_EGL_image_external_essl3 : require
uniform samplerExternalOES agent_texture;
#else
uniform sampler2D agent_texture;
#endif

uniform vec2 resolution;

in vec2 uv;

layout(location = 0) out vec4 agent_out;


const float PI  = 3.14159265358979323846264;
const float PI2 = PI * 2.0;
const float RAD = 1.0/PI;
const float PHI = 1.61803398874989484820459 * .1;// Golden Ratio
const float SQ2 = 1.41421356237309504880169 * 1000.;// Square Root of Two

float rand(in vec2 coordinate){
    return fract(tan(distance(coordinate*(PHI),vec2(PHI,PI*.1)))*SQ2);
}


void main() {
    vec4 agent_data = texture(agent_texture, uv);

    //converts degree to radians (should be done on the CPU)
    float SA = 2.0 * RAD;
    float RA = 4.0 * RAD;

    //downscales the parameters (should be done on the CPU)
    vec2 res = 1. / resolution;//data trail scale
    vec2 SO = 11.0 * res;
    vec2 SS = 1.1 * res;

    //uv = input_texture.xy
    //where to sample in the data trail texture to get the agent's world position
    vec4 src = agent_data;
    vec4 val = src;

    //agent's heading
    float angle = val.z * PI2;

    // compute the sensors positions
    vec2 uvFL=val.xy+vec2(cos(angle-SA), sin(angle-SA)) * SO;
    vec2 uvF =val.xy+vec2(cos(angle), sin(angle)) * SO;
    vec2 uvFR=val.xy+vec2(cos(angle+SA), sin(angle+SA))*SO;

    //get the values unders the sensors
    float FL = texture(env_texture, uvFL).r;
    float F  = texture(env_texture, uvF).r;
    float FR = texture(env_texture, uvFR).r;

    // original implement not very parallel friendly
    // TODO remove the conditions
    if (F > FL && F > FR){
    } else if (F<FL && F<FR){
        if (rand(val.xy * uv) > .5){
            angle +=RA;
        } else {
            angle -=RA;
        }
    } else if (FL<FR){
        angle+=RA;
    } else if (FL>FR){
        angle-=RA;
    }

    vec2 offset = vec2(cos(angle), sin(angle)) * SS;
    val.xy += offset;

    //condition from the paper : move only if the destination is free
    // if( getDataValue(val.xy) == 1. ){
    //     val.xy = src.xy;
    //     angle = rand(val.xy+time) * PI2;
    // }

    //warps the coordinates so they remains in the [0-1] interval
    val.xy = fract(val.xy);

    //converts the angle back to [0-1]
    val.z = (angle / PI2);

    agent_out = vec4(val.rgb, 1.0);
}