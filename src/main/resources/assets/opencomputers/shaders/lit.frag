angelica_in vec2 v_UV;
angelica_in vec2 v_Light;
angelica_in vec4 v_Color;
angelica_in float v_Depth;

void main() {
    vec4 color = angelica_sampleTexture(v_UV) * v_Color;
    if (!angelica_alphaTest(color.a)) discard;
    color.rgb *= angelica_sampleLightmap(v_Light).rgb;
    angelica_FragColor = vec4(angelica_fog(color.rgb, v_Depth), color.a);
}
