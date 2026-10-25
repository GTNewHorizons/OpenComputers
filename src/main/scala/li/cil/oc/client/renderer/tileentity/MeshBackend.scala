package li.cil.oc.client.renderer.tileentity

import li.cil.oc.integration.angelica.AngelicaMeshBackend
import li.cil.oc.integration.angelica.AngelicaTesr
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer

trait MeshBackend {
  def overlayRenderer(overlay: Overlay): TileEntitySpecialRenderer

  def drawRobotPiece(top: Boolean): Unit

  def drawRobotLight(step: Int, red: Float, green: Float, blue: Float): Unit

  def drawUpIndicator(): Unit
}

object MeshBackend {
  val active: MeshBackend = if (AngelicaTesr.Batching) AngelicaMeshBackend else StandaloneMeshBackend
}
