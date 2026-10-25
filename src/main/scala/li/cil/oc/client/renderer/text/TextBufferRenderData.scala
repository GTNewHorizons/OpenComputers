package li.cil.oc.client.renderer.text

import com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.CustomFramebuffer
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.util.TextBuffer

trait TextBufferRenderData {
  def dirty: Boolean

  def dirty_=(value: Boolean): Unit

  var cacheGeneration: Int = -1

  var version: Int = 0

  var mesh: CapturedMesh = _

  var meshVersion: Int = -1

  var cells: ScreenCells = _

  var lastUsedTick: Int = 0

  var tracked = false

  var packTarget: CustomFramebuffer = _

  var packVersion: Int = -1

  var packColumns: Int = 0

  var packLines: Int = 0

  var packGeneration: Int = -1

  var drawnInPack = false

  var packRejectedAt: Int = -1

  val rows = new DirtyRows

  def data: TextBuffer

  def viewport: (Int, Int)

  def packStale(columns: Int, lines: Int, generation: Int): Boolean =
    packVersion != version || packColumns != columns || packLines != lines || packGeneration != generation

  def consumeDirty() {
    if (dirty) {
      if (rows.isEmpty) rows.markAll()
      version += 1
      dirty = false
    }
  }
}
