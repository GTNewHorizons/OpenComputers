package li.cil.oc.client.renderer.shading

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.client.renderer.glsl.Program
import li.cil.oc.client.renderer.glsl.ProgramRecipe
import li.cil.oc.client.renderer.text.GlyphAtlas
import li.cil.oc.client.renderer.text.ScreenSheet
import li.cil.oc.client.renderer.text.TextCells
import org.lwjgl.opengl.GLContext

object Programs {
  final val AlphaCutoff = 0.1f

  private val unlitRecipe = ProgramRecipe.of(Settings.resourceDomain, "unlit").
    required("u_Color", "u_Fog", "u_FogColor").
    sampler("u_Texture", 0).
    constant("u_AlphaRef", AlphaCutoff).
    tinted()

  private[shading] val unlitColor = unlitRecipe.uniform("u_Color")

  private[shading] val unlitFog = unlitRecipe.uniform("u_Fog")

  private[shading] val unlitFogColor = unlitRecipe.uniform("u_FogColor")

  private[shading] var unlit: Program = _

  private val litRecipe = ProgramRecipe.of(Settings.resourceDomain, "lit").
    required("u_Color", "u_LightCoord", "u_VertexColor", "u_Fog", "u_FogColor").
    sampler("u_Texture", 0).
    sampler("u_Lightmap", 1).
    constant("u_AlphaRef", AlphaCutoff).
    lights()

  private[shading] val litColor = litRecipe.uniform("u_Color")

  private[shading] val litLightCoord = litRecipe.uniform("u_LightCoord")

  private[shading] val litVertexColor = litRecipe.uniform("u_VertexColor")

  private[shading] val litFog = litRecipe.uniform("u_Fog")

  private[shading] val litFogColor = litRecipe.uniform("u_FogColor")

  private[shading] var lit: Program = _

  private val textRecipe = ProgramRecipe.of(Settings.resourceDomain, "text").
    required("u_Cells", "u_Fade", "u_Smooth", "u_GlyphScale", "u_Fog", "u_FogColor").
    sampler("u_Texture", 0).
    sampler("u_Glyphs", 2).
    constant("u_AlphaRef", 0).
    define("OC_SHEET_WIDTH", ScreenSheet.Width).
    define("OC_SHEET_HEIGHT", ScreenSheet.Height).
    define("OC_STRIDE", ScreenSheet.Stride).
    define("OC_GLYPH_COLUMNS", GlyphAtlas.Columns).
    define("OC_GLYPH_SIZE", GlyphAtlas.GlyphSize).
    define("OC_RIGHT_HALF", TextCells.RightHalf)

  private[renderer] val textCells = textRecipe.uniform("u_Cells")

  private[renderer] val textFade = textRecipe.uniform("u_Fade")

  private[renderer] val textSmooth = textRecipe.uniform("u_Smooth")

  private[renderer] val textGlyphScale = textRecipe.uniform("u_GlyphScale")

  private[renderer] val textFog = textRecipe.uniform("u_Fog")

  private[renderer] val textFogColor = textRecipe.uniform("u_FogColor")

  private[renderer] var text: Program = _

  val recipes: Seq[ProgramRecipe] = Seq(unlitRecipe, litRecipe, textRecipe)

  def reload() {
    if (unlit != null) unlit.release()
    if (lit != null) lit.release()
    if (text != null) text.release()
    if (!GLContext.getCapabilities.OpenGL20) {
      unlit = null
      lit = null
      text = null
      OpenComputers.log.warn("OpenGL 2.0 is unavailable; shaders are disabled and rendering falls back to fixed-function.")
      return
    }
    unlit = unlitRecipe.bake()
    lit = litRecipe.bake()
    text = textRecipe.bake()
  }

  def usable(program: Program): Boolean = program != null && program.isValid

  // Binding one of our programs while a shader pack is active writes into its gbuffers and shadow map.
  def fixedFunction(inWorld: Boolean): Boolean = inWorld && ShaderPack.inUse
}
