in vec3 inPosition;

uniform mat4 g_WorldViewProjectionMatrix;
uniform vec3 g_CameraPosition;

out vec3 vDir;

void main() {
    // skydome is centered on camera: direction from camera to vertex
    vec3 worldPos = inPosition + g_CameraPosition;
    vDir = normalize(worldPos - g_CameraPosition);
    gl_Position = g_WorldViewProjectionMatrix * vec4(worldPos, 1.0);
}