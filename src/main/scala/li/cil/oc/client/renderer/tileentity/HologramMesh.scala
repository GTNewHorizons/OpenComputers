package li.cil.oc.client.renderer.tileentity

import li.cil.oc.common.tileentity.Hologram

trait HologramMesh {
  def init(): Unit

  def draw(hologram: Hologram, alpha: Float, x: Double, y: Double, z: Double, partialTicks: Float): Unit
}

object HologramMesh {
  val active: HologramMesh = StandaloneHologramMesh
}
