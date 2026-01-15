#ifdef GL_ES
precision mediump float;
#endif

uniform bool UseCube;
uniform bool HasCloudTex;

uniform sampler2D CloudTexA;
uniform sampler2D CloudTexB;
uniform samplerCube CloudCubeA;
uniform samplerCube CloudCubeB;

uniform vec3 SunDir;
uniform vec3 MoonDir;
uniform vec3 SunColor;
uniform vec3 MoonColor;
uniform vec3 TintColor;

uniform float SunIntensity;
uniform float MoonIntensity;

uniform float UvScale;
uniform float SpeedU;
uniform float SpeedV;

uniform float Coverage;
uniform float Density;
uniform float Softness;

uniform float Lighting;
uniform float Alpha;

uniform float TexBlend;
uniform float TexExposure;
uniform float TimeSec;

varying vec3 vPos;
varying vec2 vUv;

float saturate(float x) { return clamp(x, 0.0, 1.0); }

vec3 sampleCloudA(vec2 uv, vec3 dir) {
    if (!HasCloudTex) return vec3(0.0);
    if (UseCube) return textureCube(CloudCubeA, dir).rgb;
    return texture2D(CloudTexA, uv).rgb;
}

vec3 sampleCloudB(vec2 uv, vec3 dir) {
    if (!HasCloudTex) return vec3(0.0);
    if (UseCube) return textureCube(CloudCubeB, dir).rgb;
    return texture2D(CloudTexB, uv).rgb;
}

void main() {
    vec3 dir = normalize(vPos);

    vec2 uv = vUv * UvScale + vec2(TimeSec * SpeedU, TimeSec * SpeedV);

    vec3 a = sampleCloudA(uv, dir);
    vec3 b = sampleCloudB(uv, dir);

    vec3 tex = mix(a, b, TexBlend) * TexExposure;

    float mask = tex.r;

    float c = saturate((mask - (1.0 - Coverage)) * Density);
    c = smoothstep(0.0, max(1e-4, Softness), c) * Alpha;

    float sunN = saturate(dot(dir, normalize(-SunDir)));
    float moonN = saturate(dot(dir, normalize(-MoonDir)));

    vec3 lit = SunColor * SunIntensity * sunN + MoonColor * MoonIntensity * moonN;
    vec3 col = mix(vec3(1.0), lit, Lighting) * TintColor;

    gl_FragColor = vec4(col, c);
}