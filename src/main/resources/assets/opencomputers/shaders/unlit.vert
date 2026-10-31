angelica_out vec2 v_UV;
angelica_out vec4 v_Color;
angelica_out float v_Depth;

void main() {
    gl_Position = angelica_clipPosition();
    v_UV = angelica_uv();
    v_Color = angelica_color();
    v_Depth = angelica_fogCoord();
}
