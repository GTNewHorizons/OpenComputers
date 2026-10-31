package li.cil.oc.integration.angelica

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import com.gtnewhorizons.angelica.api.tesr.TesrMeshBuilder
import com.gtnewhorizons.angelica.api.tesr.TesrMeshCache
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.UnitQuad
import li.cil.oc.client.renderer.tileentity.MeshBackend
import li.cil.oc.client.renderer.tileentity.Overlay
import li.cil.oc.client.renderer.tileentity.RobotLight
import li.cil.oc.client.renderer.tileentity.RobotRenderer
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import org.lwjgl.opengl.GL11

object AngelicaMeshBackend extends MeshBackend {
  private val UpIndicatorKey = new Object

  private val RobotLightKeys = Array.fill[AnyRef](RobotLight.Steps)(new AnyRef)

  private val RobotPieceKeys = Array.fill[AnyRef](2)(new AnyRef)

  override def overlayRenderer(overlay: Overlay): TileEntitySpecialRenderer = new AngelicaOverlayRenderer(overlay)

  override def drawRobotPiece(top: Boolean) {
    RobotPieceBuilder.top = top
    TesrMeshCache.renderCached(RobotPieceKeys(if (top) 1 else 0), RobotPieceBuilder)
  }

  override def drawRobotLight(step: Int, red: Float, green: Float, blue: Float) {
    RobotLightBuilder.step = step
    GL11.glColor4f(red, green, blue, 1)
    TesrMeshCache.renderCached(RobotLightKeys(step), RobotLightBuilder)
    GL11.glColor4f(1, 1, 1, 1)
  }

  override def drawUpIndicator(): Unit = TesrMeshCache.renderCached(UpIndicatorKey, UpIndicatorBuilder)
}

private object UpIndicatorBuilder extends TesrMeshBuilder {
  override def `angelica$build`(sink: TesrMeshSink): Unit =
    UnitQuad.emitFrontQuad(sink.`angelica$bucket`(DefaultVertexFormat.POSITION_TEXTURE, Textures.blockScreenUpIndicator, AngelicaMaterials.UpIndicator))
}

private object RobotLightBuilder extends TesrMeshBuilder {
  var step = 0

  override def `angelica$build`(sink: TesrMeshSink): Unit =
    RobotRenderer.emitLight(sink.`angelica$bucket`(DefaultVertexFormat.POSITION_TEXTURE, Textures.blockRobot, AngelicaMaterials.RobotLightMaterial), step)
}

private object RobotPieceBuilder extends TesrMeshBuilder {
  var top = false

  override def `angelica$build`(sink: TesrMeshSink): Unit =
    RobotRenderer.emitPiece(sink.`angelica$bucket`(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, Textures.blockRobot, AngelicaMaterials.RobotPiece), top)
}
