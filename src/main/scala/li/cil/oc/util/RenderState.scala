package li.cil.oc.util

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderHelper
import org.lwjgl.opengl._
import org.lwjgl.util.glu.GLU

object RenderState {
  final val DrawAttribBits = GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT

  def checkError(owner: AnyRef, stage: String) {
    if (Settings.get.logOpenGLErrors) logError(owner.getClass.getName + stage)
  }

  private def logError(where: String) {
    val error = GL11.glGetError
    if (error != 0) {
      OpenComputers.log.warn("GL ERROR @ " + where + ": " + GLU.gluErrorString(error))
    }
  }

  def disableLighting() {
    Minecraft.getMinecraft.entityRenderer.disableLightmap(0)
    RenderHelper.disableStandardItemLighting()
  }

  def enableLighting() {
    Minecraft.getMinecraft.entityRenderer.enableLightmap(0)
    RenderHelper.enableStandardItemLighting()
  }

  def makeItBlend() {
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
  }
}
