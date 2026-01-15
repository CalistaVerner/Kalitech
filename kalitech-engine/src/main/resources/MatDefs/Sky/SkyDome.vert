// FILE: MatDefs/Sky/SkyDome.vert
in vec3 inPosition;
in vec2 inTexCoord;

uniform mat4 g_WorldMatrix;
uniform mat4 g_ViewProjectionMatrix;
uniform vec3 g_CameraPosition;

out vec3 vDir;
out vec2 vUv;

vec3 safeNormalize(vec3 v) {
    float l2 = dot(v, v);
    if (l2 < 1e-12) return vec3(0.0, 1.0, 0.0);
    return v * inversesqrt(l2);
}

void main() {
    // Rotation+scale only (ignore translation) for stable direction vectors.
    // Using mat3 also avoids translation affecting cubemap direction.
    vec3 worldVec = mat3(g_WorldMatrix) * inPosition;

    // Direction for sky shading (procedural + cubemap sampling)
    vDir = safeNormalize(worldVec);

    // Keep mesh-authored UVs (seam padding friendly)
    vUv = inTexCoord;

    // Center the dome on camera (no parallax)
    vec3 worldPos = worldVec + g_CameraPosition;

    // Project
    gl_Position = g_ViewProjectionMatrix * vec4(worldPos, 1.0);

    // Force to far plane to avoid any Z artifacts
    gl_Position.z = gl_Position.w;
}
