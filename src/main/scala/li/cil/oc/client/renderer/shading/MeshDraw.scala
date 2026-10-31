package li.cil.oc.client.renderer.shading

import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.OpenGlHelper
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

object MeshDraw {
  val Identity: Matrix4fc = new Matrix4f()

  // Leaves the lit program bound; the caller restores the previous program.
  def useLit(alpha: Float, vertexColor: Boolean): Boolean = {
    val lit = Programs.lit
    if (!Programs.usable(lit)) return false
    lit.use()
    GL20.glUniform4f(lit.loc(Programs.litColor), 1, 1, 1, alpha)
    GL20.glUniform2f(lit.loc(Programs.litLightCoord), OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY)
    GL20.glUniform1f(lit.loc(Programs.litVertexColor), if (vertexColor) 1 else 0)
    Fog.upload(lit, Programs.litFog, Programs.litFogColor, inWorld = true)
    lit.uploadModel(Identity)
    true
  }

  def drawUnlit(mesh: CapturedMesh, model: Matrix4fc, blendMode: Int, red: Float, green: Float, blue: Float, alpha: Float, inWorld: Boolean) {
    val unlit = Programs.unlit
    if (Programs.fixedFunction(inWorld) || !Programs.usable(unlit)) {
      FixedFunction.drawUnlit(mesh, model, blendMode, red, green, blue, alpha)
      return
    }
    val program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
    GL11.glPushAttrib(RenderState.DrawAttribBits)
    Blend(blendMode)
    unlit.use()
    GL20.glUniform4f(unlit.loc(Programs.unlitColor), red, green, blue, alpha)
    Fog.upload(unlit, Programs.unlitFog, Programs.unlitFogColor, inWorld)
    unlit.uploadModel(model)
    mesh.draw()
    GL11.glPopAttrib()
    GL20.glUseProgram(program)
  }
}
