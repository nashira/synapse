#version 300 es

precision highp float;

in vec2 uv;

out vec4 color;

uniform sampler2D grid;
uniform vec2 grid_size;

void main() {

    const float _K0 = -20.0/6.0; // center weight
    const float _K1 = 4.0/6.0; // edge-neighbors
    const float _K2 = 1.0/6.0; // vertex-neighbors
    const float cs = 0.25; // curl scale
    const float ls = 0.24; // laplacian scale
    const float ps = -0.06; // laplacian of divergence scale
    const float ds = -0.08; // divergence scale
    const float pwr = 0.2; // power when deriving rotation angle from curl
    const float amp = 1.03; // self-amplification
    const float sq2 = 0.7; // diagonal weight

    // 3x3 neighborhood coordinates
    float step_x = grid_size.x;
    float step_y = grid_size.y;
    vec2 n  = vec2(0.0, step_y);
    vec2 ne = vec2(step_x, step_y);
    vec2 e  = vec2(step_x, 0.0);
    vec2 se = vec2(step_x, -step_y);
    vec2 s  = vec2(0.0, -step_y);
    vec2 sw = vec2(-step_x, -step_y);
    vec2 w  = vec2(-step_x, 0.0);
    vec2 nw = vec2(-step_x, step_y);

    vec3 uv_0 =    texture(grid, uv).xyz;
    vec3 uv_n =  texture(grid, uv+n).xyz;
    vec3 uv_e =  texture(grid, uv+e).xyz;
    vec3 uv_s =  texture(grid, uv+s).xyz;
    vec3 uv_w =  texture(grid, uv+w).xyz;
    vec3 uv_nw = texture(grid, uv+nw).xyz;
    vec3 uv_sw = texture(grid, uv+sw).xyz;
    vec3 uv_ne = texture(grid, uv+ne).xyz;
    vec3 uv_se = texture(grid, uv+se).xyz;

    // uv.x and uv.y are our x and y components, uv.z is divergence

    // laplacian of all components
    vec3 lapl  = _K0*uv_0 + _K1*(uv_n + uv_e + uv_w + uv_s) + _K2*(uv_nw + uv_sw + uv_ne + uv_se);
    float sp = ps * lapl.z;

    // calculate curl
    // vectors point clockwise about the center point
    float curl = uv_n.x - uv_s.x - uv_e.y + uv_w.y + sq2 * (uv_nw.x + uv_nw.y + uv_ne.x - uv_ne.y + uv_sw.y - uv_sw.x - uv_se.y - uv_se.x);

    // compute angle of rotation from curl
    float sc = cs * sign(curl) * pow(abs(curl), pwr);

    // calculate divergence
    // vectors point inwards towards the center point
    float div  = uv_s.y - uv_n.y - uv_e.x + uv_w.x + sq2 * (uv_nw.x - uv_nw.y - uv_ne.x - uv_ne.y + uv_sw.x + uv_sw.y + uv_se.y - uv_se.x);
    float sd = ds * div;

    vec2 norm = normalize(uv_0.xy);

    // temp values for the update rule
    float ta = amp * uv_0.x + ls * lapl.x + norm.x * sp + uv_0.x * sd;
    float tb = amp * uv_0.y + ls * lapl.y + norm.y * sp + uv_0.y * sd;

    // rotate
    float a = ta * cos(sc) - tb * sin(sc);
    float b = ta * sin(sc) + tb * cos(sc);

    color = clamp(vec4(a,b,div,1.), -1., 1.);
}