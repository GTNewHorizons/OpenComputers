package li.cil.oc.integration.angelica

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial
import com.gtnewhorizons.angelica.api.tesr.TesrMeshBuilder
import com.gtnewhorizons.angelica.api.tesr.TesrMeshCache
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.tileentity.HologramMesh
import li.cil.oc.client.renderer.tileentity.HologramRenderer
import li.cil.oc.common.tileentity.Hologram
import org.lwjgl.opengl.GL11

object AngelicaHologramMesh extends HologramMesh {
  override def init() {}

  override def draw(hologram: Hologram, alpha: Float, x: Double, y: Double, z: Double, partialTicks: Float) {
    GL11.glPushMatrix()
    val inside = HologramRenderer.applyTransform(hologram, x, y, z, partialTicks)

    GL11.glColor4f(1, 1, 1, alpha)

    if (hologram.needsRendering) {
      TesrMeshCache.invalidate(hologram)
      hologram.needsRendering = false
    }
    HologramMeshBuilder.hologram = hologram
    val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
    if (inside) GL11.glDisable(GL11.GL_CULL_FACE)
    else GL11.glEnable(GL11.GL_CULL_FACE)
    TesrMeshCache.renderCached(hologram, HologramMeshBuilder)
    if (cull) GL11.glEnable(GL11.GL_CULL_FACE)
    else GL11.glDisable(GL11.GL_CULL_FACE)
    HologramMeshBuilder.hologram = null
    GL11.glColor4f(1, 1, 1, 1)

    GL11.glPopMatrix()
  }
}

private object HologramMeshBuilder extends TesrMeshBuilder {
  var hologram: Hologram = _

  override def `angelica$build`(sink: TesrMeshSink): Unit = {
    val count = HologramRenderer.collectFaces(hologram)
    HologramRenderer.emitFaces(bucket(sink, AngelicaMaterials.HologramPrepass), hologram, count)
    HologramRenderer.emitFaces(bucket(sink, AngelicaMaterials.HologramColor), hologram, count)
  }

  private def bucket(sink: TesrMeshSink, material: TesrMaterial) =
    sink.`angelica$bucket`(DefaultVertexFormat.POSITION_TEXTURE_COLOR_NORMAL, Textures.blockHologram, material)
}
