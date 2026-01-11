uniform sampler2D m_Texture;

uniform float m_Exposure;
uniform float m_Gamma;
uniform vec4  m_Tint;

uniform float m_WhitePoint;
uniform float m_Shoulder;
uniform float m_Toe;
uniform float m_Saturation;

varying vec2 texCoord;

float clamp01(float v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

vec3 safePow(vec3 x, float p) {
    return pow(max(x, vec3(0.0)), vec3(p));
}

// ACES-ish approximation
vec3 acesFilmic(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x*(a*x+b)) / (x*(c*x+d)+e), 0.0, 1.0);
}

vec3 saturateColor(vec3 c, float sat) {
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(l), c, max(0.0, sat));
}

void main() {
    // extra safety clamp for any out-of-range coords
    vec2 uv = clamp(texCoord, 0.0, 1.0);

    vec3 col = texture2D(m_Texture, uv).rgb;

    // Exposure (linear)
    float exp = max(0.01, m_Exposure);
    col *= exp;

    // White point normalization (headroom control)
    float wp = max(0.01, m_WhitePoint);
    col *= (1.0 / wp);

    // Tonemap
    col = acesFilmic(col);

    // Toe (lift shadows)
    float toe = clamp01(m_Toe);
    float toeExp = mix(1.0, 0.55, toe);
    col = safePow(col, toeExp);

    // Shoulder (highlight rolloff)
    float sh = clamp01(m_Shoulder);
    float shK = mix(1.0, 3.0, sh);
    col = vec3(1.0) - safePow(vec3(1.0) - col, shK);

    // Tint (RGB mul), A = strength
    float a = clamp01(m_Tint.a);
    vec3 tintMul = clamp(m_Tint.rgb, 0.0, 2.0);
    col = mix(col, col * tintMul, a);

    // Saturation
    col = saturateColor(col, m_Saturation);

    // Gamma
    float g = max(0.01, m_Gamma);
    col = safePow(col, 1.0 / g);

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}