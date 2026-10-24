package li.cil.oc.client.renderer.tileentity

import li.cil.oc.common.tileentity.Hologram
import li.cil.oc.integration.angelica.AngelicaHologramMesh
import li.cil.oc.integration.angelica.AngelicaTesr

trait HologramMesh {
  def init(): Unit

  def draw(hologram: Hologram, alpha: Float, x: Double, y: Double, z: Double, partialTicks: Float): Unit
}

object HologramMesh {
  val active: HologramMesh = if (AngelicaTesr.MeshReplacement) AngelicaHologramMesh else StandaloneHologramMesh
}
