// FILE: Assets/Shaders/HudSdfText.frag
uniform sampler2D m_SdfMap;
uniform vec4  m_Color;
uniform float m_Softness;

// Outline/shadow are expressed in PIXELS (UI-friendly)
uniform float m_OutlineSize;
uniform vec4  m_OutlineColor;

uniform vec2  m_ShadowOffset; // px
uniform float m_ShadowAlpha;

// Atlas metadata
uniform vec2  m_TexelSize; // 1/atlasW, 1/atlasH
uniform float m_PxRange;   // spreadPx used during atlas bake

in vec2 vUv;
out vec4 outColor;

float pxToSdf(float px) {
    float r = max(m_PxRange, 0.0001);
    return px / r;
}

float sdfAAWidth(float dist) {
    // Stable edge across scales
    float fw = fwidth(dist);
    // user softness is additive artistic control
    return max(fw * 0.75 + max(m_Softness, 0.0), 0.00075);
}

float sdfAlpha(float dist, float extra) {
    float w = sdfAAWidth(dist) + extra;
    return smoothstep(0.5 - w, 0.5 + w, dist);
}

void main() {
    float dist = texture(m_SdfMap, vUv).r;

    // Base glyph
    float a = sdfAlpha(dist, 0.0);

    // Outline in px -> sdf units
    float ao = 0.0;
    if (m_OutlineSize > 0.0001) {
        float extra = pxToSdf(m_OutlineSize);
        float o = sdfAlpha(dist, extra);
        ao = clamp(o - a, 0.0, 1.0);
    }

    // Shadow offset in px -> UV units
    vec4 shadow = vec4(0.0);
    if (m_ShadowAlpha > 0.0001) {
        vec2 uvOff = m_ShadowOffset * m_TexelSize;
        float distS = texture(m_SdfMap, vUv + uvOff).r;
        float as = sdfAlpha(distS, 0.0);
        shadow = vec4(0.0, 0.0, 0.0, as * m_ShadowAlpha);
    }

    vec4 col = m_Color; col.a *= a;
    vec4 outl = m_OutlineColor; outl.a *= ao;

    // Porter-Duff over
    vec4 base = shadow;
    base = base + outl * (1.0 - base.a);
    base = base + col  * (1.0 - base.a);

    if (base.a <= 0.001) discard;
    outColor = base;
}