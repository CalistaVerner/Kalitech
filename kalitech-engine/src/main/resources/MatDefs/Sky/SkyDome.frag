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

// ---- Clouds ----
uniform bool  m_CloudsEnabled;
uniform float m_Time;
uniform float m_CloudsScale;
uniform vec2  m_CloudsSpeed;
uniform float m_CloudsCoverage;
uniform float m_CloudsSharpness;
uniform float m_CloudsOpacity;
uniform float m_CloudsHeightBlend;
uniform vec4  m_CloudsColor;
uniform float m_CloudsLightPow;
uniform float m_CloudsAmbient;

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

#ifndef SKY_FORCE_LOD0
#define SKY_FORCE_LOD0 1
#endif

const float INV_PI     = 0.3183098861837907;
const float INV_TWO_PI = 0.15915494309189535;

float clamp01Safe(float x) {
    return clamp(x, 0.001, 0.999);
}

vec2 equirectUvAnalytic(vec3 dirN) {
    float u = atan(dirN.z, dirN.x) * INV_TWO_PI + 0.5;
    float v = asin(clamp(dirN.y, -1.0, 1.0)) * INV_PI + 0.5;
    return vec2(clamp01Safe(u), clamp01Safe(v));
}

vec2 skyUv2D(vec3 dirN) {
    #if SKY_UV_MODE == 1
    return vec2(clamp01Safe(vUv.x), clamp01Safe(vUv.y));
    #elif SKY_UV_MODE == 2
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

// ----------------- Clouds (procedural FBM) -----------------
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 m = mat2(1.6, -1.2, 1.2, 1.6);
    for (int i = 0; i < 5; i++) {
        v += a * noise2(p);
        p = m * p;
        a *= 0.5;
    }
    return v;
}

vec2 cloudUv(vec3 dirN) {
    // stable lat-long UV for clouds independent from sky texture UV
    float u = atan(dirN.z, dirN.x) * INV_TWO_PI + 0.5;
    float v = asin(clamp(dirN.y, -1.0, 1.0)) * INV_PI + 0.5;

    // tiling for clouds is fine; it avoids stretching and keeps motion stable
    u = fract(u);
    v = clamp(v, 0.0, 1.0);
    return vec2(u, v);
}

vec3 applyClouds(vec3 skyColor, vec3 dirN, vec3 sunDirN) {
    if (!m_CloudsEnabled) return skyColor;

    float up = saturate(dirN.y * 0.5 + 0.5);

    // fade clouds near horizon (prevents harsh banding)
    float heightBlend = saturate(m_CloudsHeightBlend);
    float horizonFade = smoothstep(0.0, 1.0, (up - 0.08) / max(0.001, 1.0 - 0.08));
    horizonFade = mix(1.0, horizonFade, heightBlend);

    vec2 uv = cloudUv(dirN);
    vec2 motion = m_CloudsSpeed * m_Time;
    float scale = max(0.001, m_CloudsScale);

    // main + detail layers
    float n1 = fbm((uv * scale) + motion);
    float n2 = fbm((uv * (scale * 2.25)) - motion * 1.37);

    float density = (n1 * 0.72 + n2 * 0.28);

    // coverage shifts average density to control overall cloud amount
    float coverage = saturate(m_CloudsCoverage);
    density = density - (1.0 - coverage);

    // sharpness controls edge hardness
    float sharp = max(0.001, m_CloudsSharpness);
    float mask = saturate(density * sharp);
    mask = smoothstep(0.0, 1.0, mask);

    // lighting: simple forward scattering approximation
    float sunFacing = saturate(dot(dirN, sunDirN));
    float light = pow(sunFacing, max(0.25, m_CloudsLightPow));
    float ambient = saturate(m_CloudsAmbient);

    vec3 cloudCol = m_CloudsColor.rgb;
    vec3 lit = cloudCol * (ambient + light * saturate(m_SunIntensity));

    float opacity = saturate(m_CloudsOpacity) * mask * horizonFade;
    return mix(skyColor, lit, opacity);
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

    // ----------------- clouds -----------------
    color = applyClouds(color, dir, sunDir);

    color *= max(0.05, m_Exposure);
    fragColor = vec4(color, 1.0);
}