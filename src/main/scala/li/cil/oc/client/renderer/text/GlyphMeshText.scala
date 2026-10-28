package li.cil.oc.client.renderer.text

import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.client.renderer.font.DynamicFontRenderer
import li.cil.oc.client.renderer.shading.Blend
import li.cil.oc.client.renderer.shading.FixedFunction
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.OpenGlHelper
import org.lwjgl.opengl.GL11

object GlyphMeshText {
  def render(buffer: TextBufferRenderData, renderer: DynamicFontRenderer, inWorld: Boolean, worldFade: Float) {
    var mesh = buffer.mesh
    if (mesh == null) {
      mesh = new CapturedMesh
      buffer.mesh = mesh
      buffer.meshVersion = -1
    }
    if (buffer.meshVersion != buffer.version || buffer.cacheGeneration != renderer.cacheGeneration) {
      val lines = buffer.data.buffer
      var i = 0
      while (i < lines.length) {
        renderer.generateChars(lines(i))
        i += 1
      }
      mesh.reset()
      try renderer.emitGeometry(mesh, buffer.data, buffer.viewport._1, buffer.viewport._2)
      finally mesh.closeBucket()
      buffer.meshVersion = buffer.version
      buffer.cacheGeneration = renderer.cacheGeneration
    }

    val lightX = OpenGlHelper.lastBrightnessX
    val lightY = OpenGlHelper.lastBrightnessY
    GL11.glPushAttrib(RenderState.DrawAttribBits)
    if (inWorld) {
      OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 0xFF, 0xFF)
      RenderState.disableLighting()
    }
    Blend(Blend.Alpha)
    if (inWorld && worldFade < 1) FixedFunction.constantAlphaBlend(worldFade)
    if (inWorld) GL11.glDepthMask(false)
    GL11.glPushMatrix()
    GL11.glScalef(0.5f, 0.5f, 1)
    mesh.draw()
    GL11.glPopMatrix()
    if (inWorld) OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY)
    GL11.glPopAttrib()
  }
}
