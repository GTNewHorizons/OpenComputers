package li.cil.oc.client.renderer.shading

import org.lwjgl.opengl.GL11

object Blend {
  final val Opaque = 0
  final val Alpha = 1
  final val Additive = 2
  final val Count = 3

  def apply(mode: Int) {
    if (mode == Opaque) GL11.glDisable(GL11.GL_BLEND)
    else {
      GL11.glEnable(GL11.GL_BLEND)
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, if (mode == Additive) GL11.GL_ONE else GL11.GL_ONE_MINUS_SRC_ALPHA)
    }
  }
}
