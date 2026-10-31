angelica_out vec2 v_UV;
angelica_out vec2 v_Light;
angelica_out vec4 v_Color;
angelica_out float v_Depth;

void main() {
    gl_Position = angelica_clipPosition();
    vec3 normal = angelica_eyeNormal();
    float d0 = max(dot(normal, normalize(angelica_lightDirection(0))), 0.0);
    float d1 = max(dot(normal, normalize(angelica_lightDirection(1))), 0.0);
    vec3 light = min(vec3(0.4) + angelica_lightDiffuse(0) * d0 + angelica_lightDiffuse(1) * d1, 1.0);
    vec4 color = angelica_color();
    v_Color = vec4(color.rgb * light, color.a);
    v_UV = angelica_uv();
    v_Light = angelica_lightmapUv();
    v_Depth = angelica_fogCoord();
}
