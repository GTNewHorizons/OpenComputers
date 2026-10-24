package li.cil.oc.client.renderer.text

import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.util.TextBuffer

trait TextBufferRenderData {
  def dirty: Boolean

  def dirty_=(value: Boolean): Unit

  var cacheGeneration: Int = -1

  var version: Int = 0

  var mesh: CapturedMesh = _

  var meshVersion: Int = -1

  var lastUsedTick: Int = 0

  var tracked = false

  def data: TextBuffer

  def viewport: (Int, Int)

  def consumeDirty() {
    if (dirty) {
      version += 1
      dirty = false
    }
  }
}
