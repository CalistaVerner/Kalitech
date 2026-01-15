// FILE: Shaders/Kalitech/Light/KaliLighting.frag
//
// This is a Kalitech-oriented Lighting fragment shader focused on:
// - Directional sun lighting
// - Cascaded shadow maps (up to 4 splits)
// - Optional Kalitech Poisson PCF kernel when USE_KALI_SHADOW_POISSON is enabled
//
// Required by this shader (material/world parameters):
// - WorldMatrix / ViewMatrix / CameraPosition (from j3md WorldParameters)
// - Diffuse/Specular/Shininess (classic Lighting-style parameters)
// - Shadow maps and coords for up to 4 cascades:
//     sampler2DShadow m_ShadowMap0..m_ShadowMap3
//     vec4 vShadowCoord0..vShadowCoord3
//     vec4 m_Splits   (split far distances: r,g,b,a)
// - Directional light uniforms (sun):
//     vec3 g_DirLightDirection  (world, points FROM surface TO light)
//     vec3 g_DirLightColor
//
// Notes:
// - If your engine uses different uniform names for sun light, rename them here.
// - If you have fewer cascades, just define NUM_SHADOW_SPLITS accordingly in the j3md via Defines.
// - This file is intentionally "engine-owned" (platform), not a copy of jME Lighting.frag.

#version 150

#ifdef GL_ES
precision mediump float;
#endif

// ------------------------------------------------------------
// Varyings from vertex shader (must match your Lighting.vert)
// ------------------------------------------------------------
in vec3 wPosition;
in vec3 wNormal;
in vec2 texCoord;

out vec4 fragColor;

// ------------------------------------------------------------
// Material parameters (classic)
// ------------------------------------------------------------
uniform vec4 m_Diffuse;
uniform vec4 m_Specular;
uniform float m_Shininess;

#ifdef DIFFUSEMAP
uniform sampler2D m_DiffuseMap;
#endif

// ------------------------------------------------------------
// World parameters
// ------------------------------------------------------------
uniform mat4 g_ViewMatrix;
uniform vec3 g_CameraPosition;

// ------------------------------------------------------------
// Sun light (rename if your pipeline uses other names)
// Direction points FROM surface TO light.
// ------------------------------------------------------------
uniform vec3 g_DirLightDirection;
uniform vec3 g_DirLightColor;

// ------------------------------------------------------------
// Cascaded shadows (up to 4)
// m_Splits = far distances for each cascade in view space units
// ------------------------------------------------------------
#ifndef NUM_SHADOW_SPLITS
#define NUM_SHADOW_SPLITS 4
#endif

uniform vec4 m_Splits;

uniform sampler2DShadow m_ShadowMap0;
#if NUM_SHADOW_SPLITS > 1
uniform sampler2DShadow m_ShadowMap1;
#endif
#if NUM_SHADOW_SPLITS > 2
uniform sampler2DShadow m_ShadowMap2;
#endif
#if NUM_SHADOW_SPLITS > 3
uniform sampler2DShadow m_ShadowMap3;
#endif

in vec4 vShadowCoord0;
#if NUM_SHADOW_SPLITS > 1
in vec4 vShadowCoord1;
#endif
#if NUM_SHADOW_SPLITS > 2
in vec4 vShadowCoord2;
#endif
#if NUM_SHADOW_SPLITS > 3
in vec4 vShadowCoord3;
#endif

// ------------------------------------------------------------
// Kalitech Poisson PCF (optional)
// ------------------------------------------------------------
#ifdef USE_KALI_SHADOW_POISSON
#import "Shaders/Kalitech/Shadows/KaliPoissonPcf.glsllib"
uniform bool  m_UseKaliShadowPoisson;
uniform vec2  m_KaliShadowTexelSize;
uniform float m_KaliPoissonRadiusTexels;
uniform int   m_KaliPoissonTaps;
uniform bool  m_KaliPoissonRotatePerPixel;
uniform vec4  m_KaliPoissonKernel[16];
#endif

// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------
float kali_viewDepth(vec3 worldPos) {
    vec4 v = g_ViewMatrix * vec4(worldPos, 1.0);
    // jME-style view space: camera looks down -Z, so forward is negative Z
    return -v.z;
}

float kali_shadowSample(sampler2DShadow sm, vec4 sc, vec2 seed) {
    // sc is expected to be in shadow projection space already:
    // sc.xyz = (u, v, depth) in [0..1] for uv, and depth in [0..1] compare space.
    // sc.w ignored.
    #ifdef USE_KALI_SHADOW_POISSON
    if (m_UseKaliShadowPoisson) {
        return kali_poisson_pcf(sm, sc, seed);
    }
    #endif
    return texture(sm, sc.xyz);
}

float kali_csmShadow(vec3 worldPos, vec2 seed) {
    float z = kali_viewDepth(worldPos);

    // Choose cascade by view depth against split far planes.
    // m_Splits.r is far of split0, m_Splits.g split1, etc.
    if (z <= m_Splits.r) {
        return kali_shadowSample(m_ShadowMap0, vShadowCoord0, seed);
    }

    #if NUM_SHADOW_SPLITS > 1
    if (z <= m_Splits.g) {
        return kali_shadowSample(m_ShadowMap1, vShadowCoord1, seed);
    }
    #endif

    #if NUM_SHADOW_SPLITS > 2
    if (z <= m_Splits.b) {
        return kali_shadowSample(m_ShadowMap2, vShadowCoord2, seed);
    }
    #endif

    #if NUM_SHADOW_SPLITS > 3
    return kali_shadowSample(m_ShadowMap3, vShadowCoord3, seed);
    #else
    return kali_shadowSample(m_ShadowMap0, vShadowCoord0, seed);
    #endif
}

vec3 kali_fresnelSchlick(float cosTheta, vec3 F0) {
    float x = clamp(1.0 - cosTheta, 0.0, 1.0);
    float x5 = x * x * x * x * x;
    return F0 + (1.0 - F0) * x5;
}

// ------------------------------------------------------------
// Main
// ------------------------------------------------------------
void main() {
    vec4 base = m_Diffuse;

    #ifdef DIFFUSEMAP
    base *= texture(m_DiffuseMap, texCoord);
    #endif

    vec3 N = normalize(wNormal);
    vec3 V = normalize(g_CameraPosition - wPosition);
    vec3 L = normalize(g_DirLightDirection);

    float NoL = max(dot(N, L), 0.0);

    // Shadow factor: 1 = lit, 0 = fully shadowed.
    // Use gl_FragCoord.xy as stable per-pixel seed for poisson rotation.
    float shadow = kali_csmShadow(wPosition, gl_FragCoord.xy);

    vec3 diffuse = base.rgb * g_DirLightColor * NoL * shadow;

    // Simple specular (Blinn-Phong with fresnel-ish boost)
    vec3 H = normalize(L + V);
    float NoH = max(dot(N, H), 0.0);
    float specPow = max(m_Shininess, 1.0);
    float specTerm = pow(NoH, specPow);

    vec3 F0 = clamp(m_Specular.rgb, 0.0, 1.0);
    vec3 F = kali_fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 specular = (F * specTerm) * g_DirLightColor * shadow;

    vec3 color = diffuse + specular;

    fragColor = vec4(color, base.a);
}