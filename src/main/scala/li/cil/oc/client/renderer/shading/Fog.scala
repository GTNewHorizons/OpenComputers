package li.cil.oc.client.renderer.shading

import li.cil.oc.client.renderer.glsl.Program
import li.cil.oc.client.renderer.glsl.Uniform
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

object Fog {
  private val color = BufferUtils.createFloatBuffer(16)

  private var mode = 0f

  private var start = 0f

  private var end = 0f

  private var density = 0f

  private var stale = true

  def beginFrame() {
    stale = true
  }

  def upload(shader: Program, fog: Uniform, fogColor: Uniform, inWorld: Boolean) {
    if (inWorld) {
      if (stale) read()
      GL20.glUniform4f(shader.loc(fog), mode, start, end, density)
      GL20.glUniform3f(shader.loc(fogColor), color.get(0), color.get(1), color.get(2))
    }
    else GL20.glUniform4f(shader.loc(fog), 0, 0, 0, 0)
  }

  private def read() {
    stale = false
    if (!GL11.glIsEnabled(GL11.GL_FOG)) mode = 0
    else {
      mode = GL11.glGetInteger(GL11.GL_FOG_MODE) match {
        case GL11.GL_LINEAR => 1
        case GL11.GL_EXP => 2
        case _ => 3
      }
      start = GL11.glGetFloat(GL11.GL_FOG_START)
      end = GL11.glGetFloat(GL11.GL_FOG_END)
      density = GL11.glGetFloat(GL11.GL_FOG_DENSITY)
      color.clear()
      GL11.glGetFloat(GL11.GL_FOG_COLOR, color)
    }
  }
}
