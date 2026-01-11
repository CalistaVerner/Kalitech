in vec3 vDir;
out vec4 fragColor;

// JME MaterialParameters => "m_" prefix
uniform sampler2D   m_SkyTex;
uniform samplerCube m_SkyCube;
uniform bool        m_UseCube;

uniform float       m_TexBlend;// 0..1
uniform float       m_TexExposure;// HDR scale

uniform vec3  m_SunDir;
uniform vec3  m_MoonDir;

uniform vec4  m_SunColor;
uniform float m_SunIntensity;

uniform vec4  m_MoonColor;
uniform float m_MoonIntensity;

uniform vec4  m_ZenithColor;
uniform vec4  m_HorizonColor;

uniform float m_Haze;
uniform float m_SunDisk;
uniform float m_MoonDisk;
uniform float m_Exposure;

float saturate(float x) { return clamp(x, 0.0, 1.0); }

vec2 equirectUv(vec3 d) {
    const float PI = 3.141592653589793;
    float u = atan(d.z, d.x) / (2.0 * PI) + 0.5;
    float v = asin(clamp(d.y, -1.0, 1.0)) / PI + 0.5;
    return vec2(u, v);
}

vec3 tonemapReinhard(vec3 x) {
    return x / (vec3(1.0) + x);
}

vec3 sampleSky(vec3 dirN) {
    if (m_UseCube) {
        // If cubemap looks mirrored in your content, flip one axis deterministically:
        // vec3 cd = vec3(dirN.x, dirN.y, -dirN.z);
        // return texture(m_SkyCube, cd).rgb;
        return texture(m_SkyCube, dirN).rgb;
    }
    vec2 uv = equirectUv(dirN);
    return texture(m_SkyTex, uv).rgb;
}

void main() {
    vec3 dir = normalize(vDir);

    // ----------------- procedural base -----------------
    float up   = saturate(dir.y * 0.5 + 0.5);
    float haze = saturate(m_Haze);

    float h = pow(1.0 - up, 1.5);
    vec3 base = mix(m_ZenithColor.rgb, m_HorizonColor.rgb, h * haze);

    // ----------------- sun / moon discs -----------------
    vec3 sunDir  = normalize(m_SunDir);
    vec3 moonDir = normalize(m_MoonDir);

    float sDot = max(0.0, dot(dir, sunDir));
    float mDot = max(0.0, dot(dir, moonDir));

    float sunPow  = max(0.5, m_SunDisk);
    float moonPow = max(0.5, m_MoonDisk);

    float sun  = pow(sDot, sunPow)  * max(0.0, m_SunIntensity);
    float moon = pow(mDot, moonPow) * max(0.0, m_MoonIntensity);

    vec3 color = base;
    color += m_SunColor.rgb  * sun;
    color += m_MoonColor.rgb * moon;

    // ----------------- texture overlay -----------------
    float tb = saturate(m_TexBlend);
    if (tb > 0.0001) {
        vec3 tex = sampleSky(dir);
        tex *= max(0.001, m_TexExposure);
        tex = tonemapReinhard(tex);// <<< ключ
        color = mix(color, tex, tb);
    }

    // ----------------- final exposure -----------------
    float expv = max(0.001, m_Exposure);
    color = vec3(1.0) - exp(-color * expv);

    fragColor = vec4(color, 1.0);
}