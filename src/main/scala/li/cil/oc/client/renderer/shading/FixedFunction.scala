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

  private var lightX = 0f

  private var lightY = 0f

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
    begin(model, blendMode, red, green, blue, alpha)
    mesh.draw()
    end()
  }

  def drawPremultiplied(mesh: CapturedMesh, texture: Int, model: Matrix4fc, alpha: Float) {
    begin(model, Blend.Alpha, alpha, alpha, alpha, alpha)
    GL11.glBlendFunc(GL11.GL_ONE, if (alpha < 1) GL11.GL_ONE else GL11.GL_ONE_MINUS_SRC_ALPHA)
    GL11.glDepthMask(false)
    RenderState.saveTexture0()
    GL11.glEnable(GL11.GL_TEXTURE_2D)
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
    mesh.drawGeometry()
    RenderState.restoreTexture0()
    end()
  }

  private def begin(model: Matrix4fc, blendMode: Int, red: Float, green: Float, blue: Float, alpha: Float) {
    lightX = OpenGlHelper.lastBrightnessX
    lightY = OpenGlHelper.lastBrightnessY
    GL11.glPushAttrib(RenderState.DrawAttribBits)
    GL11.glPushMatrix()
    multMatrix(model)
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 0xF0, 0xF0)
    RenderState.disableLighting()
    Blend(blendMode)
    GL11.glColor4f(red, green, blue, alpha)
  }

  private def end() {
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY)
    GL11.glPopMatrix()
    GL11.glPopAttrib()
  }
}
