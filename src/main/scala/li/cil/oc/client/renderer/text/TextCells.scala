package li.cil.oc.client.renderer.text

import java.nio.ByteBuffer

import li.cil.oc.util.FontUtils
import li.cil.oc.util.PackedColor
import li.cil.oc.util.TextBuffer

trait GlyphSlots {
  def slotOf(char: Int): Int
}

object TextCells {
  final val RightHalf = 0x80

  final val BytesPerCell = 8

  def encode(out: ByteBuffer, data: TextBuffer, columns: Int, from: Int, to: Int, glyphs: GlyphSlots) {
    val format = data.format
    out.clear()
    var y = from
    while (y < to) {
      val chars = data.buffer(y)
      val colors = data.color(y)
      var x = 0
      while (x < columns) {
        val char = chars(x)
        val slot = glyphs.slotOf(char)
        val foreground = PackedColor.unpackForeground(colors(x), format)
        putCell(out, slot, 0, foreground, PackedColor.unpackBackground(colors(x), format))
        if (slot != 0 && FontUtils.wcwidth(char) == 2 && x + 1 < columns) {
          x += 1
          putCell(out, slot, RightHalf, foreground, PackedColor.unpackBackground(colors(x), format))
        }
        x += 1
      }
      y += 1
    }
    out.flip()
  }

  private def putCell(out: ByteBuffer, slot: Int, flags: Int, foreground: Int, background: Int) {
    out.put(slot.toByte)
    out.put(((slot >> 8) | flags).toByte)
    out.put((foreground >> 16).toByte)
    out.put((foreground >> 8).toByte)
    out.put(foreground.toByte)
    out.put((background >> 16).toByte)
    out.put((background >> 8).toByte)
    out.put(background.toByte)
  }
}
