attribute vec3 inPosition;
attribute vec2 inTexCoord;

uniform vec2 m_TexCoordScale;

varying vec2 texCoord;

void main() {
    gl_Position = vec4(inPosition, 1.0);

    // Robust TexCoordScale handling:
    //  - If scale is unset -> (0,0) => treat as (1,1)
    //  - If some pipeline provides inverse scale (>1) => invert it
    vec2 s = m_TexCoordScale;

    if (s.x <= 0.000001 || s.y <= 0.000001) {
        s = vec2(1.0, 1.0);
    } else if (s.x > 1.5 || s.y > 1.5) {
        s = vec2(1.0 / s.x, 1.0 / s.y);
    }

    texCoord = inTexCoord * s;
}
