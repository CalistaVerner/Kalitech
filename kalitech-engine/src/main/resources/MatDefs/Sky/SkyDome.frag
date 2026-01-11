in vec3 vDir;
out vec4 fragColor;

// ------------------------------------------------------------
// JME MaterialParameters come with "m_" prefix in GLSL
// SkyTex  -> m_SkyTex
// TexBlend-> m_TexBlend
// etc.
// ------------------------------------------------------------

// ------------------- texture -------------------
uniform sampler2D m_SkyTex;// optional equirectangular panorama
uniform float     m_TexBlend;// 0..1 mix factor

// ------------------- sun / moon ----------------
uniform vec3  m_SunDir;
uniform vec3  m_MoonDir;

uniform vec4  m_SunColor;
uniform float m_SunIntensity;

uniform vec4  m_MoonColor;
uniform float m_MoonIntensity;

// ------------------- sky colors ----------------
uniform vec4  m_ZenithColor;
uniform vec4  m_HorizonColor;

// ------------------- atmosphere ----------------
uniform float m_Haze;
uniform float m_SunDisk;
uniform float m_MoonDisk;
uniform float m_Exposure;

// ------------------------------------------------

float saturate(float x) { return clamp(x, 0.0, 1.0); }

// Equirectangular mapping for sky texture
vec2 equirectUv(vec3 d) {
    // d must be normalized
    const float PI = 3.141592653589793;
    float u = atan(d.z, d.x) / (2.0 * PI) + 0.5;
    float v = asin(clamp(d.y, -1.0, 1.0)) / PI + 0.5;
    return vec2(u, v);
}

void main() {
    vec3 dir = normalize(vDir);

    // ------------------------------------------------
    // Base sky gradient (procedural)
    // ------------------------------------------------
    float up = saturate(dir.y * 0.5 + 0.5);// -1..1 -> 0..1
    float haze = saturate(m_Haze);

    // stronger blend near horizon
    float h = pow(1.0 - up, 1.5);
    vec3 base = mix(m_ZenithColor.rgb, m_HorizonColor.rgb, h * haze);

    // ------------------------------------------------
    // Sun disk
    // ------------------------------------------------
    vec3 sunDir = normalize(m_SunDir);
    float sDot = max(0.0, dot(dir, sunDir));
    float sunPow = max(0.5, m_SunDisk);
    float sun = pow(sDot, sunPow) * max(0.0, m_SunIntensity);

    // ------------------------------------------------
    // Moon disk
    // ------------------------------------------------
    vec3 moonDir = normalize(m_MoonDir);
    float mDot = max(0.0, dot(dir, moonDir));
    float moonPow = max(0.5, m_MoonDisk);
    float moon = pow(mDot, moonPow) * max(0.0, m_MoonIntensity);

    vec3 color = base;
    color += m_SunColor.rgb * sun;
    color += m_MoonColor.rgb * moon;

    // ------------------------------------------------
    // Sky texture overlay (HDRI / panorama)
    // ------------------------------------------------
    float tb = saturate(m_TexBlend);
    if (tb > 0.0001) {
        vec2 uv = equirectUv(dir);
        vec3 tex = texture(m_SkyTex, uv).rgb;
        color = mix(color, tex, tb);
    }

    // ------------------------------------------------
    // Simple exposure
    // ------------------------------------------------
    float expv = max(0.001, m_Exposure);
    color = vec3(1.0) - exp(-color * expv);

    fragColor = vec4(color, 1.0);
}