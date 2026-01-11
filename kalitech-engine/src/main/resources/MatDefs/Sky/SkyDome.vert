in vec3 inPosition;

uniform mat4 g_WorldViewProjectionMatrix;
uniform vec3 g_CameraPosition;

out vec3 vDir;

void main() {
    // Direction for sampling should come from the dome's local sphere direction
    vec3 localPos = inPosition;
    vDir = normalize(localPos);

    // No parallax: keep dome centered on camera
    vec3 worldPos = localPos + g_CameraPosition;

    gl_Position = g_WorldViewProjectionMatrix * vec4(worldPos, 1.0);
}
