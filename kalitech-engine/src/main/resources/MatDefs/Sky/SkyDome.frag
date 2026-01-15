// FILE: MatDefs/Sky/SkyDome.frag
in vec3 vDir;
in vec2 vUv;
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

vec3 safeNormalize(vec3 v) {
    float l2 = dot(v, v);
    if (l2 < 1e-12) return vec3(0.0, 1.0, 0.0);
    return v * inversesqrt(l2);
}

float disc(vec3 dirN, vec3 lightDirN, float size) {
    float d = max(0.0, dot(dirN, lightDirN));
    return pow(d, max(0.5, size));
}

vec3 tonemapReinhard(vec3 x) {
    return x / (vec3(1.0) + x);
}

// UV mode for sampling 2D (equirectangular) sky textures:
//  0: analytic (atan/asin)
//  1: mesh UVs (vUv)
//  2: hybrid (u = mesh U, v = analytic V)
#ifndef SKY_UV_MODE
#define SKY_UV_MODE 2
#endif

// For sky panoramas, forcing LOD0 is a pragmatic fix.
#ifndef SKY_FORCE_LOD0
#define SKY_FORCE_LOD0 1
#endif

const float INV_PI     = 0.3183098861837907;// 1 / PI
const float INV_TWO_PI = 0.15915494309189535;// 1 / (2*PI)

float clamp01Safe(float x) {
    // Avoid sampling exactly at 0/1 to reduce seam bleed in bilinear filtering
    return clamp(x, 0.001, 0.999);
}

vec2 equirectUvAnalytic(vec3 dirN) {
    float u = atan(dirN.z, dirN.x) * INV_TWO_PI + 0.5;
    float v = asin(clamp(dirN.y, -1.0, 1.0)) * INV_PI + 0.5;

    // DO NOT use fract(u): it forces repetition and causes visible duplication.
    return vec2(clamp01Safe(u), clamp01Safe(v));
}

vec2 skyUv2D(vec3 dirN) {
    #if SKY_UV_MODE == 1
    // Mesh UVs can be outside [0..1] depending on exporter; clamp to prevent repeat.
    return vec2(clamp01Safe(vUv.x), clamp01Safe(vUv.y));
    #elif SKY_UV_MODE == 2
    // U from mesh (seam padding friendly), V from analytic
    float v = asin(clamp(dirN.y, -1.0, 1.0)) * INV_PI + 0.5;
    return vec2(clamp01Safe(vUv.x), clamp01Safe(v));
    #else
    return equirectUvAnalytic(dirN);
    #endif
}

vec3 sample2D(sampler2D tex, vec3 dirN) {
    vec2 uv = skyUv2D(dirN);
    #if SKY_FORCE_LOD0
    return textureLod(tex, uv, 0.0).rgb;
    #else
    return texture(tex, uv).rgb;
    #endif
}

vec3 sampleSkyA(vec3 dirN) {
    if (m_UseCube) return texture(m_SkyCubeA, dirN).rgb;
    return sample2D(m_SkyTexA, dirN);
}

vec3 sampleSkyB(vec3 dirN) {
    if (m_UseCube) return texture(m_SkyCubeB, dirN).rgb;
    return sample2D(m_SkyTexB, dirN);
}

vec3 sampleSkyMixed(vec3 dirN) {
    float sb = saturate(m_SkyBlend);
    vec3 a = sampleSkyA(dirN);
    vec3 b = sampleSkyB(dirN);
    return mix(a, b, sb);
}

void main() {
    vec3 dir = safeNormalize(vDir);

    // ----------------- procedural base -----------------
    float up   = saturate(dir.y * 0.5 + 0.5);
    float haze = saturate(m_Haze);

    float h = pow(1.0 - up, 1.5);
    vec3 base = mix(m_ZenithColor.rgb, m_HorizonColor.rgb, h * haze);

    // ----------------- sun / moon discs -----------------
    vec3 sunDir  = safeNormalize(m_SunDir);
    vec3 moonDir = safeNormalize(m_MoonDir);

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