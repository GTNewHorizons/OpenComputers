#ifdef OC_CORE
#define angelica_in in
#define OC_TEXTURE texture
layout(location = 0) out vec4 angelica_FragColor;
#else
#define angelica_in varying
#define OC_TEXTURE texture2D
#define angelica_FragColor gl_FragColor
#endif

uniform sampler2D u_Texture;
// Must match the encoding Fog.upload sends: x 0 off, 1 linear, 2 exp, 3 exp2; y start; z end; w density.
uniform vec4 u_Fog;
uniform vec3 u_FogColor;
uniform float u_AlphaRef;

vec4 angelica_sampleTexture(vec2 uv) {
    return OC_TEXTURE(u_Texture, uv);
}

#ifdef OC_LIT
uniform sampler2D u_Lightmap;

vec4 angelica_sampleLightmap(vec2 uv) {
    return OC_TEXTURE(u_Lightmap, uv);
}
#endif

vec3 angelica_fog(vec3 color, float fogCoord) {
    float fog = 1.0;
    if (u_Fog.x > 2.5) {
        float d = u_Fog.w * fogCoord;
        fog = exp(-d * d);
    } else if (u_Fog.x > 1.5) {
        fog = exp(-u_Fog.w * fogCoord);
    } else if (u_Fog.x > 0.5) {
        fog = clamp((u_Fog.z - fogCoord) / (u_Fog.z - u_Fog.y), 0.0, 1.0);
    }
    return mix(u_FogColor, color, fog);
}

// Negated <= keeps NaN alpha like the alpha test it replaces; a plain > would discard it.
bool angelica_alphaTest(float alpha) {
    return !(alpha <= u_AlphaRef);
}
