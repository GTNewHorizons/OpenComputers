package li.cil.oc.client.renderer.shading

import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.OpenGlHelper
import org.joml.Matrix4fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

object FixedFunction {
  private val matrix = BufferUtils.createFloatBuffer(16)

  def multMatrix(model: Matrix4fc) {
    matrix.clear()
    model.get(matrix)
    GL11.glMultMatrix(matrix)
  }

  def constantAlphaBlend(alpha: Float) {
    GL14.glBlendColor(0, 0, 0, alpha)
    GL11.glBlendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE)
  }

  def drawUnlit(mesh: CapturedMesh, model: Matrix4fc, blendMode: Int, red: Float, green: Float, blue: Float, alpha: Float) {
    val lightX = OpenGlHelper.lastBrightnessX
    val lightY = OpenGlHelper.lastBrightnessY
    GL11.glPushAttrib(RenderState.DrawAttribBits)
    GL11.glPushMatrix()
    multMatrix(model)
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 0xF0, 0xF0)
    RenderState.disableLighting()
    Blend(blendMode)
    GL11.glColor4f(red, green, blue, alpha)
    mesh.draw()
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY)
    GL11.glPopMatrix()
    GL11.glPopAttrib()
  }
}
