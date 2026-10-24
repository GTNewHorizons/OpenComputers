package li.cil.oc.integration.angelica

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial
import com.gtnewhorizons.angelica.api.tesr.TesrShader
import com.gtnewhorizons.angelica.api.tesr.TesrShaders
import li.cil.oc.Settings
import li.cil.oc.client.renderer.shading.Blend
import li.cil.oc.client.renderer.shading.Programs
import net.minecraft.util.ResourceLocation

object AngelicaMaterials {
  // Requires API version 3+
  private def register(name: String): TesrShader = TesrShaders.register(Settings.resourceDomain + ":" + name,
    new ResourceLocation(Settings.resourceDomain, "shaders/" + name + ".vert"),
    new ResourceLocation(Settings.resourceDomain, "shaders/" + name + ".frag"))

  private lazy val Unlit = register("unlit")

  private lazy val Lit = register("lit")

  private def unlit(material: TesrMaterial.Builder): TesrMaterial.Builder =
    if (AngelicaTesr.SourceShaders) material.shader(Unlit) else material

  private def lit(material: TesrMaterial.Builder): TesrMaterial.Builder =
    if (AngelicaTesr.SourceShaders) material.shader(Lit) else material

  private val overlays = {
    val table = new Array[TesrMaterial](Blend.Count * 2)
    var blend = 0
    while (blend < Blend.Count) {
      table(blend * 2) = buildOverlay(blend, animated = false)
      table(blend * 2 + 1) = buildOverlay(blend, animated = true)
      blend += 1
    }
    table
  }

  private def buildOverlay(blend: Int, animated: Boolean): TesrMaterial = {
    val builder = TesrMaterial.builder().unlit().lightmap(240, 240).cutout(Programs.AlphaCutoff)
    if (blend == Blend.Alpha) builder.translucent()
    else if (blend == Blend.Additive) builder.additiveAlpha()
    if (animated) builder.stream()
    unlit(builder).build()
  }

  def overlay(blend: Int, animated: Boolean): TesrMaterial = overlays(blend * 2 + (if (animated) 1 else 0))

  val UpIndicator: TesrMaterial = unlit(TesrMaterial.builder().translucent().unlit().lightmap(240, 240).noDepthWrite().color(1, 1, 1, 1).cutout(Programs.AlphaCutoff)).build()

  val RobotLightMaterial: TesrMaterial = overlay(Blend.Opaque, animated = true)

  val RobotPiece: TesrMaterial = lit(TesrMaterial.builder().cutout(Programs.AlphaCutoff).stream()).build()

  lazy val HologramPrepass: TesrMaterial = lit(TesrMaterial.builder().depthOnly().cutout(Programs.AlphaCutoff).stream()).build()

  lazy val HologramColor: TesrMaterial = lit(TesrMaterial.builder().additiveAlpha().depthEqual().cutout(Programs.AlphaCutoff).stream()).build()
}
