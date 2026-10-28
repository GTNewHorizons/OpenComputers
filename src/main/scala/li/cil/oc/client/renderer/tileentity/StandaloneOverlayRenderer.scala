package li.cil.oc.client.renderer.tileentity

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.client.renderer.shading.MeshDraw
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import org.joml.Matrix4f

object StandaloneOverlayRenderer {
  private val model = new Matrix4f()
}

final class StandaloneOverlayRenderer(overlay: Overlay) extends TileEntitySpecialRenderer {
  private val meshes = new Array[CapturedMesh](overlay.states)

  private var atlasGeneration = -1

  private object Sink extends OverlaySink {
    var mesh: CapturedMesh = _

    override def bucket(texture: ResourceLocation): Tessellator = mesh.bucket(DefaultVertexFormat.POSITION_TEXTURE, texture)
  }

  override def renderTileEntityAt(te: TileEntity, x: Double, y: Double, z: Double, f: Float) {
    val alpha = overlay.alpha(te)
    if (alpha <= 0) return
    val state = overlay.state(te)
    if (state == OverlayState.NoMesh) return
    MeshDraw.drawUnlit(mesh(state), StandaloneOverlayRenderer.model.translation(x.toFloat, y.toFloat, z.toFloat).mul(overlay.transform(te)),
      overlay.blendMode, 1, 1, 1, alpha, inWorld = true)
  }

  private def mesh(state: Int): CapturedMesh = {
    if (atlasGeneration != StandaloneMeshBackend.atlasGeneration) {
      atlasGeneration = StandaloneMeshBackend.atlasGeneration
      var i = 0
      while (i < meshes.length) {
        if (meshes(i) != null) {
          meshes(i).delete()
          meshes(i) = null
        }
        i += 1
      }
    }
    var mesh = meshes(state)
    if (mesh == null) {
      mesh = new CapturedMesh
      Sink.mesh = mesh
      try overlay.emit(Sink, state)
      finally {
        mesh.closeBucket()
        Sink.mesh = null
      }
      meshes(state) = mesh
    }
    mesh
  }
}
