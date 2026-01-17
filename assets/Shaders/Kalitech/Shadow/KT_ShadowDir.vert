// FILE: assets/Shaders/Kalitech/Shadow/KT_ShadowDir.vert

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldViewMatrix;
uniform mat4 g_WorldMatrix;

in vec3 inPosition;
in vec3 inNormal;

out vec3 vWorldPos;
out vec3 vWorldNormal;
out float vViewDepth;

void main() {
    vec4 worldPos4 = g_WorldMatrix * vec4(inPosition, 1.0);
    vWorldPos = worldPos4.xyz;

    // world normal (approx)
    vWorldNormal = normalize((g_WorldMatrix * vec4(inNormal, 0.0)).xyz);

    // view depth as positive distance along -Z (jME uses right-handed view; z is negative forward)
    vec4 viewPos = g_WorldViewMatrix * vec4(inPosition, 1.0);
    vViewDepth = -viewPos.z;

    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}