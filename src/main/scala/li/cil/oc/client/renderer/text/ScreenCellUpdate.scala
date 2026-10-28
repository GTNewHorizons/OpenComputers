package li.cil.oc.client.renderer.text

import java.nio.ByteBuffer

import li.cil.oc.util.TextBuffer

trait CellUploader extends CellRects {
  def bind(cells: ScreenCells): Unit
}

object ScreenCellUpdate {
  def acquire(pool: SheetPool, buffer: TextBufferRenderData, size: Int): ScreenCells = {
    var cells = buffer.cells
    if (cells == null) {
      cells = new ScreenCells
      pool.place(cells, size)
      buffer.cells = cells
    }
    else if (cells.size != size) pool.resize(cells, size)
    cells
  }

  def update(uploader: CellUploader, atlas: GlyphAtlas, out: ByteBuffer, data: TextBuffer, dirty: DirtyRows, cells: ScreenCells, columns: Int, lines: Int) {
    if (cells.width != columns || cells.height != lines || cells.generation != atlas.generation) {
      cells.width = columns
      cells.height = lines
      cells.generation = atlas.generation
      dirty.mark(0, lines)
    }
    val from = math.max(dirty.from, 0)
    val to = math.min(dirty.to, lines)
    if (from < to) {
      TextCells.encode(out, data, columns, from, to, atlas)
      uploader.bind(cells)
      ScreenSheet.cellRects(cells.start + from * columns, cells.start + to * columns, uploader)
    }
    dirty.clear()
  }
}
