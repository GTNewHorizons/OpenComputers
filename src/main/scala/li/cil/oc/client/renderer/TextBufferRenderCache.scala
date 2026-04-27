package li.cil.oc.client.renderer

import li.cil.oc.Settings
import li.cil.oc.client.renderer.font.TextBufferRenderData
import li.cil.oc.util.RenderState

object TextBufferRenderCache {
  val renderer =
    if (Settings.get.fontRenderer == "texture") new font.StaticFontRenderer()
    else new font.DynamicFontRenderer()

  // ----------------------------------------------------------------------- //
  // Rendering
  // ----------------------------------------------------------------------- //

  def render(buffer: TextBufferRenderData) {
    RenderState.checkError(getClass.getName + ".render: entering (aka: wasntme)")

    if (buffer.dirty) {
      for (line <- buffer.data.buffer) {
        renderer.generateChars(line)
      }
      buffer.dirty = false
    }

    renderer.drawBuffer(buffer.data, buffer.viewport._1, buffer.viewport._2)

    RenderState.checkError(getClass.getName + ".render: leaving")
  }
}
