#version 300 es

//{EXT}

#ifdef EXT
#extension GL_OES_EGL_image_external_essl3 : require
#endif

precision mediump float;

in vec2 texture_coords0;
in vec2 texture_coords1;

layout(location = 0) out vec4 currentFrame;
layout(location = 1) out vec4 frameDifference;

#ifdef EXT
uniform samplerExternalOES input_texture0;
#else
uniform sampler2D input_texture0;
#endif
uniform sampler2D input_texture1;


vec2 opticalFlow(in vec2 uv1, in vec2 uv2, in sampler2D view, in sampler2D past, in float offset, in float lambda) {
    vec2 off = vec2(offset, 0.0);

    vec4 gradX = (texture(view, uv1+off.xy)-texture(view, uv1-off.xy))+
    (texture(past, uv2+off.xy)-texture(past, uv2-off.xy));

    vec4 gradY = (texture(view, uv1+off.yx)-texture(view, uv1-off.yx))+
    (texture(past, uv2+off.yx)-texture(past, uv2-off.yx));

    vec4 gradMag = sqrt((gradX*gradX)+(gradY*gradY)+vec4(lambda));

    vec4 diff = texture(view, uv1)-texture(past, uv2);

    return vec2((diff*(gradX/gradMag)).x, (diff*(gradY/gradMag)).x);
}

void main() {
    vec4 frame = texture(input_texture0, texture_coords0);
    currentFrame = frame;

    frameDifference = vec4(opticalFlow(texture_coords0, texture_coords1, input_texture0, input_texture1, 0.05, 0.001) * 0.5 + 0.5, 0.0, 1.0);
}

//
//void main() {
//    vec4 frame = texture(input_texture0, texture_coords0);
//    currentFrame = frame;
//
//    vec4 lastFrame = texture(input_texture1, texture_coords1);
//
//    frameDifference = abs(frame - lastFrame);
//}

//vec2 scale = vec2(2.3);
//vec2 offset = vec2(0.05);
//float lambda = 1.02;
//
//float TWO_PI = radians(360.);
//float PI = radians(180.);
//
//float rgbToGray(vec4 rgba) {
//    const vec3 W = vec3(0.2125, 0.7154, 0.0721);
//    return dot(rgba.xyz, W);
//}
//
//float rgbToFloat(vec3 color) {
//    return (color.r + color.g + color.b) / 3.;
//}
//
//vec3 hsv2rgb(vec3 c)
//{
//    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
//    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
//    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
//}
//
//void main() {
//
//    vec2 uv0 = texture_coords0.xy;
//    vec2 uv1 = texture_coords1.xy;
//    vec4 a = texture(input_texture0, uv0);
//    vec4 b = texture(input_texture1, uv1);
//    vec2 offsetX = vec2(offset.x,0.);
//    vec2 offsetY = vec2(0.,offset.y);
//
//    // get the color difference between frames
//    vec4 frameDiff = b - a;
//
//    // calculate the gradient on each axis
//    vec4 gradientX = texture(input_texture1, uv1 + offsetX) - texture(input_texture1, uv1 - offsetX);
//    gradientX += texture(input_texture0, uv0 + offsetX) - texture(input_texture0, uv0 - offsetX);
//    vec4 gradientY = texture(input_texture1, uv1 + offsetY) - texture(input_texture1, uv1 - offsetY);
//    gradientY += texture(input_texture0, uv0 + offsetY) - texture(input_texture0, uv0 - offsetY);
//
//    // calc directional magnitude
//    vec4 gradientMagnitude = sqrt((gradientX * gradientX) + (gradientY * gradientY) + vec4(lambda));
//
//    //
//    vec4 vx = frameDiff * (gradientX / gradientMagnitude);
//    float vxd = rgbToGray(vx); // assumes greyscale
//    vec2 xout = vec2(max(vxd,0.),abs(min(vxd,0.)))*scale.x; // format output for flowrepos, out(-x,+x,-y,+y)
//
//    vec4 vy = frameDiff * (gradientY / gradientMagnitude);
//    float vyd = rgbToGray(vy);
//    vec2 yout = vec2(max(vyd,0.),abs(min(vyd,0.)))*scale.y; // format output for flowrepos, out(-x,+x,-y,+y)
//
//    // get rotation & strength
//    float dir = atan(rgbToFloat(vy.rgb), rgbToFloat(vx.rgb));
//    float rot = (PI + dir) / TWO_PI;	// normalize rotation to 0-1
//    float amp = abs(rgbToFloat(vx.rgb) + rgbToFloat(vy.rgb));
//
//    // draw to buffer
//    // gl_FragColor = clamp(vec4(xout.xy,yout.xy), 0.0, 1.0);
////    frameDifference = vec4(rot, amp, 0., 1.);
////    frameDifference = vec4(xout.y, yout.y, 0., 1.);
//    frameDifference = vec4(hsv2rgb(vec3(dir, 1.0, 1.0)) * amp, 1.);
//    currentFrame = a;
//}