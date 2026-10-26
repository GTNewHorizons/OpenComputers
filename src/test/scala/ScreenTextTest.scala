import java.nio.ByteBuffer

import li.cil.oc.client.renderer.text.CellRects
import li.cil.oc.client.renderer.text.CellUploader
import li.cil.oc.client.renderer.text.DirtyRows
import li.cil.oc.client.renderer.text.GlyphAtlas
import li.cil.oc.client.renderer.text.GlyphSlots
import li.cil.oc.client.renderer.text.GlyphStore
import li.cil.oc.client.renderer.text.PackBudget
import li.cil.oc.client.renderer.text.ScreenCellUpdate
import li.cil.oc.client.renderer.text.ScreenCells
import li.cil.oc.client.renderer.text.ScreenSheet
import li.cil.oc.client.renderer.text.SheetPool
import li.cil.oc.client.renderer.text.SheetTextures
import li.cil.oc.client.renderer.text.TextBufferRenderData
import li.cil.oc.client.renderer.text.TextCells
import li.cil.oc.util.FontUtils
import li.cil.oc.util.PackedColor
import li.cil.oc.util.TextBuffer
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.Matchers.be
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.junit.JUnitRunner

import scala.collection.mutable
import scala.util.Random

@RunWith(classOf[JUnitRunner])
class ScreenTextTest extends FunSpec {
  private val Wide = 0x4E2D

  private object CodePointSlots extends GlyphSlots {
    override def slotOf(char: Int): Int = if (char == ' ') 0 else char
  }

  private class CountingStore extends GlyphStore {
    var stored = 0

    var missing = Set.empty[Int]

    override def store(texture: Int, char: Int, x: Int, y: Int): Boolean =
      if (missing(char)) false
      else {
        stored += 1
        true
      }

    override def clear(texture: Int, x: Int, y: Int, width: Int): Unit = {}
  }

  private class FakeTextures extends SheetTextures {
    private var next = 0

    val live = mutable.Set.empty[Int]

    var copies = 0

    override def create(width: Int, height: Int): Int = {
      next += 1
      live += next
      next
    }

    override def delete(texture: Int): Unit = live.remove(texture) should be(true)

    override def copy(from: Int, to: Int, width: Int, height: Int): Unit = copies += 1
  }

  private final class Data extends TextBufferRenderData {
    var dirty = false

    override def data: TextBuffer = null

    override def viewport: (Int, Int) = (0, 0)
  }

  private def cleared: DirtyRows = {
    val rows = new DirtyRows
    rows.clear()
    rows
  }

  private def buffer(width: Int, height: Int) = new TextBuffer(width, height, new PackedColor.SingleBitFormat(0x33FF33))

  private def cell(out: ByteBuffer, index: Int) = {
    val bytes = new Array[Int](TextCells.BytesPerCell)
    for (i <- bytes.indices) bytes(i) = out.get(index * TextCells.BytesPerCell + i) & 0xFF
    bytes
  }

  private def fill(atlas: GlyphAtlas, from: Int, count: Int) {
    for (char <- from until from + count) atlas.slotOf(char)
  }

  describe("Row encoding") {
    it("writes slot, foreground and background for each cell of the requested rows") {
      val data = buffer(4, 3)
      data.set(0, 1, "ab", vertical = false)
      val out = ByteBuffer.allocate(4 * 3 * TextCells.BytesPerCell)
      TextCells.encode(out, data, 4, 1, 2, CodePointSlots)
      out.remaining should be(4 * TextCells.BytesPerCell)
      cell(out, 0) should be(Array('a'.toInt, 0, 0x33, 0xFF, 0x33, 0, 0, 0))
      cell(out, 1) should be(Array('b'.toInt, 0, 0x33, 0xFF, 0x33, 0, 0, 0))
      cell(out, 2).take(2) should be(Array(0, 0))
    }
    it("marks the placeholder cell of a wide glyph as its right half and clips it in the last visible column") {
      FontUtils.wcwidth(Wide) should be(2)
      val data = buffer(4, 1)
      data.set(1, 0, new String(Character.toChars(Wide)), vertical = false)
      val out = ByteBuffer.allocate(4 * TextCells.BytesPerCell)
      TextCells.encode(out, data, 4, 0, 1, CodePointSlots)
      cell(out, 1).take(2) should be(Array(Wide & 0xFF, Wide >> 8))
      cell(out, 2).take(2) should be(Array(Wide & 0xFF, (Wide >> 8) | TextCells.RightHalf))
      cell(out, 3).take(2) should be(Array(0, 0))
      val clipped = ByteBuffer.allocate(4 * TextCells.BytesPerCell)
      TextCells.encode(clipped, data, 2, 0, 1, CodePointSlots)
      clipped.remaining should be(2 * TextCells.BytesPerCell)
      cell(clipped, 1).take(2) should be(Array(Wide & 0xFF, Wide >> 8))
    }
    it("resolves palette colors on the CPU") {
      val format = new PackedColor.MutablePaletteFormat
      val data = new TextBuffer(1, 1, format)
      format(3) = 0x123456
      format(5) = 0x654321
      data.foreground = PackedColor.Color(3, isPalette = true)
      data.background = PackedColor.Color(5, isPalette = true)
      data.set(0, 0, "x", vertical = false)
      val out = ByteBuffer.allocate(TextCells.BytesPerCell)
      TextCells.encode(out, data, 1, 0, 1, CodePointSlots)
      cell(out, 0).drop(2) should be(Array(0x12, 0x34, 0x56, 0x65, 0x43, 0x21))
    }
  }

  describe("Dirty rows") {
    it("marks the one-based destination rows of a bitblt") {
      val rows = cleared
      rows.bitBlt(1, 4)
      (rows.from, rows.to) should be((0, 4))
    }
    it("covers every row a buffer operation changes, including clamped negative targets") {
      val random = new Random(7)
      val width = 20
      val height = 12
      val data = buffer(width, height)
      def text(length: Int) = Seq.fill(length)(('a' + random.nextInt(26)).toChar).mkString
      for (y <- 0 until height) data.set(0, y, text(width), vertical = false)
      for (_ <- 0 until 4000) {
        val before = data.buffer.map(_.clone())
        val rows = cleared
        val col = random.nextInt(width)
        val row = random.nextInt(height + 6) - 3
        random.nextInt(4) match {
          case 0 =>
            val s = text(1 + random.nextInt(6))
            data.set(col, row, s, vertical = false)
            rows.set(row, s, vertical = false)
          case 1 =>
            val s = text(1 + random.nextInt(6))
            val written = if (row < 0) s.drop(-row) else s
            if (written.nonEmpty) {
              data.set(col, math.max(row, 0), written, vertical = true)
              rows.set(row, written, vertical = true)
            }
          case 2 =>
            val h = 1 + random.nextInt(8)
            data.fill(col, row, 1 + random.nextInt(8), h, 'A' + random.nextInt(26))
            rows.fill(row, h)
          case _ =>
            val h = 1 + random.nextInt(8)
            val ty = random.nextInt(21) - 10
            data.copy(col, row, 1 + random.nextInt(8), h, random.nextInt(21) - 10, ty)
            rows.copy(row, h, ty)
        }
        for (y <- 0 until height if !before(y).sameElements(data.buffer(y)))
          (y >= rows.from && y < rows.to) should be(true)
      }
    }
    it("marks all rows and bumps the version when dirty with no marked rows") {
      val d = new Data
      d.rows.clear()
      d.dirty = true
      d.consumeDirty()
      (d.rows.from, d.rows.to) should be((0, Int.MaxValue))
      d.version should be(1)
      d.dirty should be(false)
    }
  }

  describe("Glyph atlas") {
    it("keeps slot zero for blanks, reserves the question mark and stores each glyph once") {
      val store = new CountingStore
      store.missing = Set(0x1234)
      val atlas = new GlyphAtlas(new FakeTextures, store, 4096)
      atlas.slotOf(' ') should be(0)
      atlas.slotOf('?') should be(1)
      atlas.slotOf('a') should be(2)
      atlas.slotOf('a') should be(2)
      atlas.slotOf(0x1234) should be(1)
      atlas.slotOf(0x1234) should be(1)
      store.stored should be(2)
    }
    it("keeps slot numbers when it grows into a larger texture") {
      val textures = new FakeTextures
      val atlas = new GlyphAtlas(textures, new CountingStore, 512)
      val first = atlas.texture
      val initialSlots = GlyphAtlas.InitialHeight / GlyphAtlas.GlyphSize * GlyphAtlas.Columns
      fill(atlas, 0x100, initialSlots - 2)
      atlas.slotOf(0x100) should be(2)
      atlas.height should be(GlyphAtlas.InitialHeight)
      atlas.slotOf(0x10000) should be(initialSlots)
      atlas.height should be(GlyphAtlas.InitialHeight * 2)
      textures.copies should be(1)
      textures.live should be(Set(atlas.texture))
      (atlas.texture != first) should be(true)
      atlas.slotOf(0x100) should be(2)
    }
    it("substitutes when full, resets on the next frame, and does not reset again within the cooldown") {
      val atlas = new GlyphAtlas(new FakeTextures, new CountingStore, GlyphAtlas.InitialHeight)
      val capacity = GlyphAtlas.InitialHeight / GlyphAtlas.GlyphSize * GlyphAtlas.Columns
      fill(atlas, 0x100, capacity - 2)
      atlas.slotOf(0x10000) should be(1)
      atlas.generation should be(0)
      atlas.beginFrame()
      atlas.generation should be(1)
      atlas.slotOf(0x10000) should be(2)
      fill(atlas, 0x100, capacity)
      for (_ <- 0 until GlyphAtlas.ResetCooldown) atlas.beginFrame()
      atlas.generation should be(1)
      atlas.beginFrame()
      atlas.generation should be(2)
    }
  }

  describe("Sheet allocation") {
    it("merges freed extents with both neighbours, the next one and the previous one") {
      val both = new ScreenSheet(1)
      Seq(100, 100, 100, ScreenSheet.Cells - 300).map(both.alloc) should be(Seq(0, 100, 200, 300))
      both.alloc(1) should be(-1)
      both.free(0, 100)
      both.free(200, 100)
      both.free(100, 100)
      both.alloc(300) should be(0)
      val next = new ScreenSheet(2)
      Seq(100, 100, ScreenSheet.Cells - 200).map(next.alloc)
      next.free(100, 100)
      next.free(0, 100)
      next.alloc(200) should be(0)
      val previous = new ScreenSheet(3)
      Seq(100, 100, ScreenSheet.Cells - 200).map(previous.alloc)
      previous.free(0, 100)
      previous.free(100, 100)
      previous.alloc(200) should be(0)
    }
    it("never overlaps or leaks a sheet under random place, resize and release") {
      val random = new Random(11)
      val textures = new FakeTextures
      val pool = new SheetPool(textures)
      val live = mutable.ArrayBuffer.empty[ScreenCells]
      def size() = random.nextInt(4) match {
        case 0 => 800
        case 1 => 2000
        case 2 => 8000
        case _ => 1 + random.nextInt(ScreenSheet.Cells / 2)
      }
      for (_ <- 0 until 3000) {
        random.nextInt(3) match {
          case 0 =>
            val cells = new ScreenCells
            pool.place(cells, size())
            live += cells
          case 1 if live.nonEmpty => pool.resize(live(random.nextInt(live.size)), size())
          case _ if live.nonEmpty => pool.release(live.remove(random.nextInt(live.size)))
          case _ =>
        }
        val bySheet = live.groupBy(_.sheet)
        textures.live should be(bySheet.keys.map(_.texture).toSet)
        for ((sheet, cells) <- bySheet) {
          sheet.freeCells should be(ScreenSheet.Cells - cells.map(_.size).sum)
          val sorted = cells.sortBy(_.start)
          for (c <- sorted) (c.start >= 0 && c.start + c.size <= ScreenSheet.Cells) should be(true)
          for ((a, b) <- sorted.zip(sorted.drop(1))) (a.start + a.size <= b.start) should be(true)
        }
      }
      live.foreach(pool.release)
      textures.live should be(Set.empty)
    }
    it("fills earlier sheets first so a later sheet drains and is deleted") {
      val textures = new FakeTextures
      val pool = new SheetPool(textures)
      val a, b, c, d = new ScreenCells
      pool.place(a, ScreenSheet.Cells / 2)
      pool.place(b, ScreenSheet.Cells / 2)
      pool.place(c, 100)
      (c.sheet eq a.sheet) should be(false)
      pool.release(b)
      pool.place(d, 100)
      (d.sheet eq a.sheet) should be(true)
      pool.release(c)
      textures.live should be(Set(a.sheet.texture))
    }
  }

  describe("Screen cell update") {
    final class Uploader(store: CountingStore = null) extends CellUploader {
      var binds = 0
      var storedAtBind = -1
      var rects = Vector.empty[(Int, Int)]
      var placed = Vector.empty[(Int, Int, Int, Int)]

      override def bind(cells: ScreenCells) {
        binds += 1
        if (store != null) storedAtBind = store.stored
      }

      override def rect(column: Int, row: Int, columns: Int, rows: Int, firstCell: Int) {
        rects :+= ((firstCell, columns * rows))
        placed :+= ((column, row, columns, rows))
      }

      def uploaded: Int = rects.map(_._2).sum
    }

    val columns = 4
    val lines = 3

    def out = ByteBuffer.allocate(columns * lines * TextCells.BytesPerCell)

    final class Screen(atlasHeight: Int = 4096) {
      val textures = new FakeTextures
      val pool = new SheetPool(textures)
      val store = new CountingStore
      val atlas = new GlyphAtlas(textures, store, atlasHeight)
      val data: TextBuffer = buffer(columns, lines)
      val rows: DirtyRows = cleared
      val owner = new Data
      pool.place(new ScreenCells, ScreenSheet.Stride + 476)
      val cells: ScreenCells = ScreenCellUpdate.acquire(pool, owner, columns * lines)

      def update(uploader: Uploader = new Uploader, buf: ByteBuffer = out): Uploader = {
        ScreenCellUpdate.update(uploader, atlas, buf, data, rows, cells, columns, lines)
        uploader
      }
    }

    def primed(atlasHeight: Int = 4096) = {
      val screen = new Screen(atlasHeight)
      screen.update()
      screen
    }

    it("uploads every row when the cells were just placed or resized") {
      val screen = new Screen
      val uploader = screen.update()
      uploader.uploaded should be(columns * lines)
      uploader.rects.head._1 should be(0)
      (screen.cells.width, screen.cells.height) should be((columns, lines))
      screen.rows.isEmpty should be(true)
      ScreenCellUpdate.acquire(screen.pool, screen.owner, columns * lines + 1)
      screen.cells.width should be(0)
    }
    it("uploads nothing when no rows are dirty and the size is unchanged") {
      val uploader = primed().update()
      uploader.rects should be(Vector.empty)
      uploader.binds should be(0)
    }
    it("uploads only the dirty rows, binding the sheet after new glyphs are stored") {
      val screen = primed()
      screen.data.set(0, 1, "ab", vertical = false)
      screen.rows.set(1, "ab", vertical = false)
      val buf = out
      val uploader = screen.update(new Uploader(screen.store), buf)
      uploader.storedAtBind should be(3)
      uploader.rects should be(Vector((0, columns)))
      uploader.placed should be(Vector((480, 1, columns, 1)))
      buf.remaining should be(columns * TextCells.BytesPerCell)
      screen.rows.isEmpty should be(true)
    }
    it("clamps dirty rows beyond the viewport") {
      val screen = primed()
      screen.rows.mark(2, 99)
      screen.update().uploaded should be(columns)
    }
    it("re-uploads every row after the glyph atlas resets") {
      val screen = primed(GlyphAtlas.InitialHeight)
      fill(screen.atlas, 0x100, GlyphAtlas.InitialHeight / GlyphAtlas.GlyphSize * GlyphAtlas.Columns)
      screen.atlas.beginFrame()
      screen.update().uploaded should be(columns * lines)
    }
  }

  describe("Pack target budget") {
    it("rejects past the limit and advances the epoch when bytes are freed, so rejected screens retry") {
      val budget = new PackBudget(100)
      budget.resize(0, 60) should be(true)
      val rejectedAt = budget.epoch
      budget.resize(0, 60) should be(false)
      budget.resize(60, 80) should be(true)
      budget.epoch should be(rejectedAt)
      budget.release(80)
      (budget.epoch != rejectedAt) should be(true)
      budget.resize(0, 100) should be(true)
      budget.used should be(100)
    }
  }

  describe("Cell rects") {
    final class Grid(rows: Int) extends CellRects {
      val source = Array.fill(ScreenSheet.Stride * rows)(-1)
      var rects = 0

      override def rect(column: Int, row: Int, columns: Int, rows: Int, firstCell: Int) {
        rects += 1
        (column + columns) should be <= ScreenSheet.Stride
        for (y <- 0 until rows; x <- 0 until columns) {
          val target = (row + y) * ScreenSheet.Stride + column + x
          source(target) should be(-1)
          source(target) = firstCell + y * columns + x
        }
      }
    }

    it("maps every cell exactly once in at most three rects across sheet rows") {
      for ((start, count, single) <- Seq((0, 1024, true), (100, 200, true), (1000, 50, false), (1023, 1, true), (500, 3000, false), (7, 2053, false), (0, 3072, true))) {
        val grid = new Grid(8)
        ScreenSheet.cellRects(start, start + count, grid)
        withClue(s"($start, $count): ") {
          for (cell <- grid.source.indices)
            grid.source(cell) should be(if (cell >= start && cell < start + count) cell - start else -1)
          grid.rects should be <= 3
          if (single) grid.rects should be(1)
        }
      }
    }
  }
}
