#ifdef OC_CORE
#define angelica_out out
layout(location = 0) in vec3 a_Position;
layout(location = 2) in vec2 a_UV;
uniform mat4 u_MVP;

vec4 angelica_clipPosition() {
    return u_MVP * vec4(a_Position, 1.0);
}

vec2 angelica_uv() {
    return a_UV;
}

#ifdef OC_LIT
layout(location = 1) in vec4 a_Color;
layout(location = 4) in vec3 a_Normal;
uniform mat3 u_NormalMatrix;
uniform vec3 u_LightDir[2];
uniform vec3 u_LightDiffuse[2];
#define OC_COLOR a_Color

vec3 angelica_eyeNormal() {
    return normalize(u_NormalMatrix * a_Normal);
}

vec3 angelica_lightDirection(int i) {
    return u_LightDir[i];
}

vec3 angelica_lightDiffuse(int i) {
    return u_LightDiffuse[i];
}
#endif
#else
#define angelica_out varying
uniform mat4 u_ModelMatrix;

vec4 angelica_clipPosition() {
    return gl_ModelViewProjectionMatrix * u_ModelMatrix * gl_Vertex;
}

vec2 angelica_uv() {
    return gl_MultiTexCoord0.xy;
}

#ifdef OC_LIT
#define OC_COLOR gl_Color

vec3 angelica_eyeNormal() {
    return normalize(gl_NormalMatrix * (mat3(u_ModelMatrix) * gl_Normal));
}

vec3 angelica_lightDirection(int i) {
    return gl_LightSource[i].position.xyz;
}

vec3 angelica_lightDiffuse(int i) {
    return gl_LightSource[i].diffuse.rgb;
}
#endif
#endif

#ifdef OC_TINTED
uniform vec4 u_Color;
#ifdef OC_LIT
// 0 for a mesh without a color element, whose color input is then undefined.
uniform float u_VertexColor;

vec4 angelica_color() {
    return mix(vec4(1.0), OC_COLOR, u_VertexColor) * u_Color;
}
#else
vec4 angelica_color() {
    return u_Color;
}
#endif
#endif

#ifdef OC_LIT
uniform vec2 u_LightCoord;

vec2 angelica_lightmapUv() {
    return (u_LightCoord + 8.0) / 256.0;
}
#endif

// Clip-space w, read back from gl_Position: a body assigns gl_Position before calling this.
float angelica_fogCoord() {
    return gl_Position.w;
}
