in vec3 vDir;
out vec4 fragColor;

// JME MaterialParameters => "m_" prefix
uniform sampler2D   m_SkyTexA;
uniform sampler2D   m_SkyTexB;

uniform samplerCube m_SkyCubeA;
uniform samplerCube m_SkyCubeB;

uniform bool        m_UseCube;

// Crossfade between A (0) and B (1)
uniform float       m_SkyBlend;

// Mix procedural vs texture result
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

float disc(vec3 dirN, vec3 lightDirN, float size) {
    float d = max(0.0, dot(dirN, lightDirN));
    return pow(d, max(0.5, size));
}

vec2 equirectUv(vec3 dirN) {
    float u = atan(dirN.z, dirN.x) / (2.0 * 3.14159265) + 0.5;
    float v = asin(clamp(dirN.y, -1.0, 1.0)) / 3.14159265 + 0.5;
    return vec2(u, v);
}

vec3 tonemapReinhard(vec3 x) {
    return x / (vec3(1.0) + x);
}

vec3 sampleSkyA(vec3 dirN) {
    if (m_UseCube) return texture(m_SkyCubeA, dirN).rgb;
    return texture(m_SkyTexA, equirectUv(dirN)).rgb;
}

vec3 sampleSkyB(vec3 dirN) {
    if (m_UseCube) return texture(m_SkyCubeB, dirN).rgb;
    return texture(m_SkyTexB, equirectUv(dirN)).rgb;
}

vec3 sampleSkyMixed(vec3 dirN) {
    float sb = saturate(m_SkyBlend);
    vec3 a = sampleSkyA(dirN);
    vec3 b = sampleSkyB(dirN);
    return mix(a, b, sb);
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

    float sun  = disc(dir, sunDir, m_SunDisk)  * max(0.0, m_SunIntensity);
    float moon = disc(dir, moonDir, m_MoonDisk) * max(0.0, m_MoonIntensity);

    vec3 color = base;
    color += m_SunColor.rgb  * sun;
    color += m_MoonColor.rgb * moon;

    // ----------------- texture overlay (A/B crossfade) -----------------
    float tb = saturate(m_TexBlend);
    if (tb > 0.0001) {
        vec3 tex = sampleSkyMixed(dir);
        tex *= max(0.001, m_TexExposure);
        tex = tonemapReinhard(tex);
        color = mix(color, tex, tb);
    }

    color *= max(0.05, m_Exposure);
    fragColor = vec4(color, 1.0);
}