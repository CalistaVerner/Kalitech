// FILE: assets/Shaders/Kalitech/Shadow/KT_ShadowDir.frag

#import "Shaders/Kalitech/Shadow/KT_ShadowCommon.glsllib"

uniform vec4 m_LightColor;
uniform vec3 m_LightDir;

uniform sampler2DShadow m_ShadowMap0;
uniform sampler2DShadow m_ShadowMap1;
uniform sampler2DShadow m_ShadowMap2;
uniform sampler2DShadow m_ShadowMap3;
uniform sampler2DShadow m_ShadowMap4;
uniform sampler2DShadow m_ShadowMap5;
uniform sampler2DShadow m_ShadowMap6;
uniform sampler2DShadow m_ShadowMap7;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in float vViewDepth;

out vec4 fragColor;

sampler2DShadow kt_shadowSamplerByIndex(int i) {
    if (i == 0) return m_ShadowMap0;
    if (i == 1) return m_ShadowMap1;
    if (i == 2) return m_ShadowMap2;
    if (i == 3) return m_ShadowMap3;
    if (i == 4) return m_ShadowMap4;
    if (i == 5) return m_ShadowMap5;
    if (i == 6) return m_ShadowMap6;
    return m_ShadowMap7;
}

void main() {
    vec3 N = normalize(vWorldNormal);
    vec3 L = normalize(-m_LightDir);

    float ndotl = max(dot(N, L), 0.0);

    int splitIndex = kt_chooseSplit(vViewDepth);
    vec4 sc = kt_shadowCoord(splitIndex, vWorldPos);

    // Out of atlas / outside projection -> no shadow
    float shadow = 1.0;
    if (sc.x >= 0.0 && sc.x <= 1.0 && sc.y >= 0.0 && sc.y <= 1.0) {
        sampler2DShadow smp = kt_shadowSamplerByIndex(splitIndex);
        shadow = kt_shadowDepthCompare(sc, smp);
    }

    vec3 lit = m_LightColor.rgb * ndotl * shadow;
    fragColor = vec4(lit, 1.0);
}