package li.cil.oc.client.renderer

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent
import li.cil.oc.client.renderer.shading.Fog
import li.cil.oc.client.renderer.shading.Programs
import li.cil.oc.client.renderer.shading.ShaderPack
import li.cil.oc.client.renderer.text.ScreenText

object FrameStart {
  var reloadPending = true

  @SubscribeEvent
  def onRenderTick(e: RenderTickEvent) {
    if (e.phase == TickEvent.Phase.START) {
      Fog.beginFrame()
      ShaderPack.beginFrame()
      if (reloadPending) {
        reloadPending = false
        TextBufferRenderCache.releaseAll()
        Programs.reload()
        ScreenText.reload(TextBufferRenderCache.renderer)
      }
      TextBufferRenderCache.beginFrame()
    }
  }
}
