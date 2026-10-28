package li.cil.oc.client.renderer.text

final class DirtyRows {
  var from = 0

  var to = Int.MaxValue

  def isEmpty: Boolean = from >= to

  def mark(first: Int, end: Int) {
    val start = math.max(first, 0)
    if (end > start) {
      if (start < from) from = start
      if (end > to) to = end
    }
  }

  def markAll(): Unit = mark(0, Int.MaxValue)

  def clear() {
    from = Int.MaxValue
    to = 0
  }

  def set(row: Int, s: String, vertical: Boolean): Unit =
    if (vertical && row < 0) mark(0, s.length)
    else mark(row, row + (if (vertical) s.length else 1))

  def fill(row: Int, h: Int): Unit = mark(row, row + h)

  def copy(row: Int, h: Int, ty: Int): Unit = mark(row + ty, math.max(row + ty + h, 1))

  def bitBlt(row: Int, h: Int): Unit = mark(row - 1, row - 1 + h)
}
