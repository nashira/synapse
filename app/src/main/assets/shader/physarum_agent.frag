#version 300 es

//{ENV_EXT}
#ifdef ENV_EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

//{AGENT_EXT}
#ifdef AGENT_EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

const float PI  = 3.14159265358979323846264;
const float PI2 = PI * 2.0;
const float RAD = 1.0/PI;
const float PHI = 1.61803398874989484820459;// Golden Ratio
const float SQ2 = 1.41421356237309504880169;// Square Root of Two

#ifdef ENV_EXT
uniform samplerExternalOES env_texture;
#else
uniform sampler2D env_texture;
#endif

#ifdef AGENT_EXT
uniform samplerExternalOES agent_texture;
#else
uniform sampler2D agent_texture;
#endif

uniform vec2 resolution;
uniform mat4 texture_matrix;
uniform float sensor_angle;
uniform float travel_angle;
uniform float sensor_distance;
uniform float travel_distance;

in vec2 uv;
layout(location = 0) out vec4 agent_out;

float rand(in vec2 coordinate){
    return fract(tan(distance(coordinate*(PHI),vec2(PHI,PI*.01)))*SQ2*1000.0);
}

void main() {
    vec4 agent_data = texture(agent_texture, uv);

    //converts degree to radians (should be done on the CPU)
    float SA = sensor_angle * RAD;
    float RA = travel_angle * RAD;

    //downscales the parameters (should be done on the CPU)
    vec2 res = 1. / resolution;//data trail scale
    vec2 SO = sensor_distance * res;
    vec2 SS = travel_distance * res;

    //uv = input_texture.xy
    //where to sample in the data trail texture to get the agent's world position
    vec4 val = agent_data;

    //agent's heading
    float angle = val.z * PI2;

    // compute the sensors positions
    vec2 uvFL=val.xy+vec2(cos(angle-SA), sin(angle-SA)) * SO;
    vec2 uvF =val.xy+vec2(cos(angle), sin(angle)) * SO;
    vec2 uvFR=val.xy+vec2(cos(angle+SA), sin(angle+SA))*SO;

    uvFL = (texture_matrix * vec4(uvFL, 0.0, 1.0)).xy;
    uvF = (texture_matrix * vec4(uvF, 0.0, 1.0)).xy;
    uvFR = (texture_matrix * vec4(uvFR, 0.0, 1.0)).xy;

    //get the values unders the sensors
    float FL = texture(env_texture, uvFL).r;
    float F  = texture(env_texture, uvF).r;
    float FR = texture(env_texture, uvFR).r;

    // original implement not very parallel friendly
    // TODO remove the conditions
    if (F > FL && F > FR){
    } else if (F<FL && F<FR){
//        if (rand(val.xy * uv) > .5){
            angle +=RA;
//        } else {
//            angle -=RA;
//        }
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
    val.z = fract(angle / PI2);

    agent_out = vec4(val.rgb, 1.0);
}