package li.cil.oc.client.renderer.text

import java.util

import it.unimi.dsi.fastutil.ints.IntArrayList

trait SheetTextures {
  def create(width: Int, height: Int): Int

  def delete(texture: Int): Unit

  def copy(from: Int, to: Int, width: Int, height: Int): Unit
}

trait CellRects {
  def rect(column: Int, row: Int, columns: Int, rows: Int, firstCell: Int): Unit
}

final class ScreenCells {
  var sheet: ScreenSheet = _

  var start = 0

  var size = 0

  var width = 0

  var height = 0

  var generation = -1
}

object ScreenSheet {
  final val Width = 2048
  final val Height = 512
  final val Stride = Width / 2
  final val Cells = Stride * Height

  def cellRects(firstCell: Int, endCell: Int, out: CellRects) {
    var cell = firstCell
    val column = cell % Stride
    if (column != 0) {
      val count = math.min(Stride - column, endCell - cell)
      out.rect(column, cell / Stride, count, 1, cell - firstCell)
      cell += count
    }
    val fullRows = (endCell - cell) / Stride
    if (fullRows > 0) {
      out.rect(0, cell / Stride, Stride, fullRows, cell - firstCell)
      cell += fullRows * Stride
    }
    if (cell < endCell) out.rect(0, cell / Stride, endCell - cell, 1, cell - firstCell)
  }
}

final class ScreenSheet(val texture: Int) {
  private val starts = IntArrayList.wrap(Array(0))

  private val lengths = IntArrayList.wrap(Array(ScreenSheet.Cells))

  var screens = 0

  def freeCells: Int = {
    var total = 0
    var i = 0
    while (i < lengths.size) {
      total += lengths.getInt(i)
      i += 1
    }
    total
  }

  def alloc(size: Int): Int = {
    var best = -1
    var bestLength = Int.MaxValue
    var i = 0
    while (i < starts.size) {
      val length = lengths.getInt(i)
      if (length >= size && length < bestLength) {
        best = i
        bestLength = length
        if (length == size) i = starts.size
      }
      i += 1
    }
    if (best < 0) return -1
    val start = starts.getInt(best)
    if (bestLength == size) {
      starts.removeInt(best)
      lengths.removeInt(best)
    }
    else {
      starts.set(best, start + size)
      lengths.set(best, bestLength - size)
    }
    screens += 1
    start
  }

  def free(start: Int, size: Int) {
    screens -= 1
    var low = 0
    var high = starts.size
    while (low < high) {
      val mid = (low + high) >>> 1
      if (starts.getInt(mid) < start) low = mid + 1 else high = mid
    }
    val joinsPrevious = low > 0 && starts.getInt(low - 1) + lengths.getInt(low - 1) == start
    val joinsNext = low < starts.size && start + size == starts.getInt(low)
    if (joinsPrevious && joinsNext) {
      lengths.set(low - 1, lengths.getInt(low - 1) + size + lengths.getInt(low))
      starts.removeInt(low)
      lengths.removeInt(low)
    }
    else if (joinsPrevious) lengths.set(low - 1, lengths.getInt(low - 1) + size)
    else if (joinsNext) {
      starts.set(low, start)
      lengths.set(low, lengths.getInt(low) + size)
    }
    else {
      starts.add(low, start)
      lengths.add(low, size)
    }
  }
}

final class SheetPool(textures: SheetTextures) {
  private val sheets = new util.ArrayList[ScreenSheet]

  def place(cells: ScreenCells, size: Int): Unit = place(cells, size, null)

  def resize(cells: ScreenCells, size: Int) {
    val sheet = cells.sheet
    sheet.free(cells.start, cells.size)
    val start = sheet.alloc(size)
    if (start >= 0) assign(cells, sheet, start, size)
    else {
      place(cells, size, sheet)
      dropIfEmpty(sheet)
    }
  }

  def release(cells: ScreenCells) {
    val sheet = cells.sheet
    sheet.free(cells.start, cells.size)
    cells.sheet = null
    dropIfEmpty(sheet)
  }

  private def place(cells: ScreenCells, size: Int, skip: ScreenSheet) {
    var i = 0
    while (i < sheets.size) {
      val sheet = sheets.get(i)
      if (sheet ne skip) {
        val start = sheet.alloc(size)
        if (start >= 0) {
          assign(cells, sheet, start, size)
          return
        }
      }
      i += 1
    }
    val sheet = new ScreenSheet(textures.create(ScreenSheet.Width, ScreenSheet.Height))
    sheets.add(sheet)
    assign(cells, sheet, sheet.alloc(size), size)
  }

  private def assign(cells: ScreenCells, sheet: ScreenSheet, start: Int, size: Int) {
    cells.sheet = sheet
    cells.start = start
    cells.size = size
    cells.width = 0
    cells.height = 0
  }

  private def dropIfEmpty(sheet: ScreenSheet): Unit = if (sheet.screens == 0) {
    textures.delete(sheet.texture)
    sheets.remove(sheet)
  }
}
