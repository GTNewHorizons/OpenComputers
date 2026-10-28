package li.cil.oc.client.renderer

import net.minecraft.client.renderer.Tessellator

object UnitQuad {
  def emitFrontQuad(t: Tessellator) {
    t.addVertexWithUV(0, 1, 0, 0, 1)
    t.addVertexWithUV(1, 1, 0, 1, 1)
    t.addVertexWithUV(1, 0, 0, 1, 0)
    t.addVertexWithUV(0, 0, 0, 0, 0)
  }
}
