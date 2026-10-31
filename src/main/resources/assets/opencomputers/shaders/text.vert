uniform vec4 u_Cells;

angelica_out vec2 v_Cell;
angelica_out float v_Depth;

void main() {
    gl_Position = angelica_clipPosition();
    v_Cell = angelica_uv() * u_Cells.zw;
    v_Depth = angelica_fogCoord();
}
