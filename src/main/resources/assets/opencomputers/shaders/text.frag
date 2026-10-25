uniform vec4 u_Cells;
uniform float u_Fade;
uniform float u_Smooth;
uniform sampler2D u_Glyphs;
uniform vec2 u_GlyphScale;

angelica_in vec2 v_Cell;
angelica_in float v_Depth;

const vec2 INV = 1.0 / vec2(OC_SHEET_WIDTH, OC_SHEET_HEIGHT);

float glyph(vec2 slotOrigin, float right, vec2 pixel) {
    vec2 px = vec2(right * 8.0 + clamp(pixel.x, 0.0, 7.0), clamp(pixel.y, 0.0, 15.0)) + 0.5;
    return OC_TEXTURE(u_Glyphs, (slotOrigin + px) * u_GlyphScale).a;
}

void main() {
    vec2 cell = min(floor(v_Cell), u_Cells.zw - 1.0);
    vec2 f = clamp(v_Cell - cell, 0.0, 0.9999);
    float index = u_Cells.x + cell.y * u_Cells.z + cell.x;
    float row = floor((index + 0.5) / OC_STRIDE);
    vec2 base = vec2((index - row * OC_STRIDE) * 2.0, row);
    vec4 a = angelica_sampleTexture((base + vec2(0.5, 0.5)) * INV);
    vec4 b = angelica_sampleTexture((base + vec2(1.5, 0.5)) * INV);
    float lo = floor(a.r * 255.0 + 0.5);
    float hi = floor(a.g * 255.0 + 0.5);
    float right = step(OC_RIGHT_HALF, hi);
    float slot = lo + (hi - right * OC_RIGHT_HALF) * 256.0;
    vec2 slotOrigin = vec2(mod(slot, OC_GLYPH_COLUMNS), floor(slot / OC_GLYPH_COLUMNS)) * OC_GLYPH_SIZE;
    vec2 gp = f * vec2(8.0, 16.0);

    float cover = glyph(slotOrigin, right, floor(gp));
    if (u_Smooth > 0.5 && max(fwidth(v_Cell.x) * 8.0, fwidth(v_Cell.y) * 16.0) > 1.0) {
        vec2 p = gp - 0.5;
        vec2 i = floor(p);
        vec2 w = p - i;
        cover = mix(
            mix(glyph(slotOrigin, right, i), glyph(slotOrigin, right, i + vec2(1.0, 0.0)), w.x),
            mix(glyph(slotOrigin, right, i + vec2(0.0, 1.0)), glyph(slotOrigin, right, i + vec2(1.0, 1.0)), w.x),
            w.y);
    }

    vec3 fg = vec3(a.b, a.a, b.r);
    vec3 bg = b.gba;
    bool hasBackground = bg != vec3(0.0);
    float alpha = hasBackground ? 1.0 : cover;
    if (!angelica_alphaTest(alpha)) discard;
    vec3 color = hasBackground ? mix(bg, fg, cover) : fg;
    angelica_FragColor = vec4(angelica_fog(color, v_Depth), alpha * u_Fade);
}
