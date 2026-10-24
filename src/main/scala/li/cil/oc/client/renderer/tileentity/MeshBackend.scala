package li.cil.oc.client.renderer.tileentity

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer

trait MeshBackend {
  def overlayRenderer(overlay: Overlay): TileEntitySpecialRenderer

  def drawRobotPiece(top: Boolean): Unit

  def drawRobotLight(step: Int, red: Float, green: Float, blue: Float): Unit

  def drawUpIndicator(): Unit
}

object MeshBackend {
  val active: MeshBackend = StandaloneMeshBackend
}
