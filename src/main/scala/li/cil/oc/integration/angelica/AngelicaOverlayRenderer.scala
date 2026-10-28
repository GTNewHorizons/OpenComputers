package li.cil.oc.integration.angelica

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial
import com.gtnewhorizons.angelica.api.tesr.TesrMeshBuilder
import com.gtnewhorizons.angelica.api.tesr.TesrMeshCache
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink
import li.cil.oc.client.renderer.shading.FixedFunction
import li.cil.oc.client.renderer.tileentity.Overlay
import li.cil.oc.client.renderer.tileentity.OverlaySink
import li.cil.oc.client.renderer.tileentity.OverlayState
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

object AngelicaOverlayRenderer {
  private object Sink extends OverlaySink {
    var sink: TesrMeshSink = _

    var material: TesrMaterial = _

    override def bucket(texture: ResourceLocation): Tessellator =
      sink.`angelica$bucket`(DefaultVertexFormat.POSITION_TEXTURE, texture, material)
  }
}

final class AngelicaOverlayRenderer(overlay: Overlay) extends TileEntitySpecialRenderer with TesrMeshBuilder {
  import AngelicaOverlayRenderer.Sink

  private val keys = Array.fill[AnyRef](overlay.states)(new AnyRef)

  private val overlayMaterial = AngelicaMaterials.overlay(overlay.blendMode, overlay.animated)

  private var state = 0

  override def `angelica$build`(sink: TesrMeshSink) {
    Sink.sink = sink
    Sink.material = overlayMaterial
    try overlay.emit(Sink, state)
    finally Sink.sink = null
  }

  override def renderTileEntityAt(te: TileEntity, x: Double, y: Double, z: Double, f: Float) {
    val alpha = overlay.alpha(te)
    if (alpha <= 0) return
    state = overlay.state(te)
    if (state == OverlayState.NoMesh) return
    GL11.glPushMatrix()
    GL11.glTranslated(x, y, z)
    FixedFunction.multMatrix(overlay.transform(te))
    GL11.glColor4f(1, 1, 1, alpha)
    TesrMeshCache.renderCached(keys(state), this)
    GL11.glColor4f(1, 1, 1, 1)
    GL11.glPopMatrix()
  }
}
