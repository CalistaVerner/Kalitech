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
    // Apply world scale/rotation (ignore translation here; we recenter manually)
    vec3 worldFromMesh = (g_WorldMatrix * vec4(inPosition, 1.0)).xyz;

    // Direction for sky shading (procedural + cubemap sampling)
    vDir = safeNormalize(worldFromMesh);

    // Mesh-authored UVs (seam-safe, padded) for 2D equirect textures.
    // IMPORTANT: do NOT fract() here. Many sky domes intentionally pad U slightly
    // outside [0..1] to smooth the seam with WrapMode.Repeat.
    vUv = inTexCoord;

    // Center the dome on camera (no parallax)
    vec3 worldPos = worldFromMesh + g_CameraPosition;

    // Project
    gl_Position = g_ViewProjectionMatrix * vec4(worldPos, 1.0);

    // Force to far plane to kill any residual Z artifacts (even if depth test is on somewhere)
    gl_Position.z = gl_Position.w;
}