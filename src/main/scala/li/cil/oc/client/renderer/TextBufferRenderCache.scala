package li.cil.oc.client.renderer

import java.util

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent
import li.cil.oc.client.renderer.font.DynamicFontRenderer
import li.cil.oc.client.renderer.text.GlyphMeshText
import li.cil.oc.client.renderer.text.TextBufferRenderData

object TextBufferRenderCache {
  val renderer = new DynamicFontRenderer()

  var inWorld = false

  var worldFade = 1f

  private final val UnusedTicks = 40

  private val owners = new util.ArrayList[TextBufferRenderData]

  private var tick = 0

  def render(buffer: TextBufferRenderData) {
    buffer.consumeDirty()
    if (!buffer.tracked) {
      buffer.tracked = true
      owners.add(buffer)
    }
    buffer.lastUsedTick = tick

    GlyphMeshText.render(buffer, renderer, inWorld, worldFade)
  }

  private def release(buffer: TextBufferRenderData) {
    if (buffer.mesh != null) {
      buffer.mesh.delete()
      buffer.mesh = null
    }
    buffer.tracked = false
  }

  def releaseAll() {
    var i = 0
    while (i < owners.size) {
      release(owners.get(i))
      i += 1
    }
    owners.clear()
  }

  @SubscribeEvent
  def onTick(e: ClientTickEvent) {
    if (e.phase == TickEvent.Phase.END) {
      tick += 1
      var i = owners.size - 1
      while (i >= 0) {
        val owner = owners.get(i)
        if (tick - owner.lastUsedTick > UnusedTicks) {
          release(owner)
          owners.remove(i)
        }
        i -= 1
      }
    }
  }
}
