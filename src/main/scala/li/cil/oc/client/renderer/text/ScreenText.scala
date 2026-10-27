package li.cil.oc.client.renderer.text

import java.nio.ByteBuffer

import com.gtnewhorizon.gtnhlib.client.opengl.GLCaps
import com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.CustomFramebuffer
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import li.cil.oc.Settings
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.client.renderer.UnitQuad
import li.cil.oc.client.renderer.font.DynamicFontRenderer
import li.cil.oc.client.renderer.font.IGlyphProvider
import li.cil.oc.client.renderer.shading.Blend
import li.cil.oc.client.renderer.shading.FixedFunction
import li.cil.oc.client.renderer.shading.Fog
import li.cil.oc.client.renderer.shading.Programs
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.OpenGlHelper
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

final class PackBudget(limit: Long) {
  var used = 0L

  var epoch = 0

  def resize(previous: Long, needed: Long): Boolean =
    if (used - previous + needed > limit) false
    else {
      used += needed - previous
      if (needed < previous) epoch += 1
      true
    }

  def release(bytes: Long) {
    used -= bytes
    epoch += 1
  }
}

object ScreenText {
  private var quad: CapturedMesh = _

  private var provider: IGlyphProvider = _

  private var atlas: GlyphAtlas = _

  private var available = false

  private val budget = new PackBudget(256L << 20)

  private object Glyphs extends GlyphStore {
    override def store(texture: Int, char: Int, x: Int, y: Int): Boolean = {
      val glyph = provider.getGlyph(char)
      if (glyph == null) return false
      val height = provider.getGlyphHeight
      val width = glyph.remaining / (height * 4)
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
      GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, glyph)
      if (width < GlyphAtlas.GlyphSize) clear(texture, x + width, y, GlyphAtlas.GlyphSize - width)
      true
    }

    override def clear(texture: Int, x: Int, y: Int, width: Int) {
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
      GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, GlyphAtlas.GlyphSize, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, blankGlyph)
    }
  }

  private object Textures extends SheetTextures {
    override def create(width: Int, height: Int): Int = {
      val texture = GL11.glGenTextures()
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null.asInstanceOf[ByteBuffer])
      texture
    }

    override def delete(texture: Int): Unit = GL11.glDeleteTextures(texture)

    override def copy(from: Int, to: Int, width: Int, height: Int) {
      val pixels = BufferUtils.createByteBuffer(width * height * 4)
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, from)
      GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, to)
      GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
    }
  }

  private val blankGlyph = BufferUtils.createByteBuffer(GlyphAtlas.GlyphSize * GlyphAtlas.GlyphSize * 4)

  private val pool = new SheetPool(Textures)

  private var staging = BufferUtils.createByteBuffer(80 * 25 * TextCells.BytesPerCell)

  private object Upload extends CellUploader {
    override def bind(cells: ScreenCells): Unit = GL11.glBindTexture(GL11.GL_TEXTURE_2D, cells.sheet.texture)

    override def rect(column: Int, row: Int, columns: Int, rows: Int, firstCell: Int) {
      staging.position(firstCell * TextCells.BytesPerCell)
      GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, column * 2, row, columns * 2, rows, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, staging)
    }
  }

  private val model = new Matrix4f()

  private val smoothing = if (Settings.get.textLinearFiltering) 1f else 0f

  private val packFilter = if (Settings.get.textLinearFiltering) CustomFramebuffer.TEXTURE_LINEAR else 0

  private var framebufferSaved = false

  private var savedFramebuffer = 0

  private val savedViewport = BufferUtils.createIntBuffer(16)

  def reload(renderer: DynamicFontRenderer) {
    provider = renderer.glyphProvider
    val shader = Programs.text
    available = Programs.usable(shader) && GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) >= 3
    if (atlas != null) atlas.dispose()
    atlas = null
    if (!available) return
    val program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
    shader.use()
    GL20.glUniform1f(shader.loc(Programs.textSmooth), smoothing)
    GL20.glUseProgram(program)
    if (quad == null) quad = CapturedMesh.build(DefaultVertexFormat.POSITION_TEXTURE, null)(UnitQuad.emitFrontQuad)
    RenderState.saveTexture0()
    atlas = new GlyphAtlas(Textures, Glyphs, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE))
    RenderState.restoreTexture0()
  }

  def beginFrame(): Unit = if (atlas != null) atlas.beginFrame()

  def release(buffer: TextBufferRenderData) {
    releaseCells(buffer)
    releasePackTarget(buffer)
    buffer.drawnInPack = false
    buffer.packRejectedAt = -1
  }

  def render(renderer: DynamicFontRenderer, buffer: TextBufferRenderData, fade: Float, inWorld: Boolean): Boolean = {
    if (!available) return false
    val viewport = buffer.viewport
    val columns = columnsOf(buffer, viewport)
    val lines = linesOf(buffer, viewport, columns)
    if (columns * lines == 0) return true
    RenderState.saveTexture0()
    val cells = prepare(buffer, columns, lines)
    draw(cells, columns, lines, model.scaling(columns * renderer.charRenderWidth, lines * renderer.charRenderHeight, 1), fade, inWorld,
      if (fade < 1) Blend.Additive else Blend.Alpha)
    RenderState.restoreTexture0()
    true
  }

  def drawPacked(renderer: DynamicFontRenderer, buffer: TextBufferRenderData, fade: Float): Boolean = {
    if (!available || !GLCaps.fboSupported() || buffer.packRejectedAt == budget.epoch) return false
    buffer.drawnInPack = true
    val target = buffer.packTarget
    if (target != null) {
      FixedFunction.drawPremultiplied(quad, target.framebufferTexture,
        model.scaling(buffer.packColumns * renderer.charRenderWidth, buffer.packLines * renderer.charRenderHeight, 1), fade)
    }
    true
  }

  def renderPackTarget(renderer: DynamicFontRenderer, buffer: TextBufferRenderData) {
    if (!available) return
    val viewport = buffer.viewport
    val columns = columnsOf(buffer, viewport)
    val lines = linesOf(buffer, viewport, columns)
    if (columns * lines == 0) return
    val width = columns * renderer.charWidth
    val height = lines * renderer.charHeight
    var target = buffer.packTarget
    if (target == null || target.framebufferWidth != width || target.framebufferHeight != height) {
      val previous = if (target == null) 0L else targetBytes(target)
      if (!budget.resize(previous, width.toLong * height * 4)) {
        releasePackTarget(buffer)
        releaseCells(buffer)
        buffer.packRejectedAt = budget.epoch
        return
      }
      saveFramebuffer()
      if (target == null) {
        target = new CustomFramebuffer(packFilter)
        buffer.packTarget = target
      }
      target.createBindFramebuffer(width, height)
      buffer.packVersion = -1
    }
    RenderState.saveTexture0()
    val cells = prepare(buffer, columns, lines)
    if (buffer.packStale(columns, lines, atlas.generation)) {
      saveFramebuffer()
      target.clearBindFramebuffer(true)
      GL11.glMatrixMode(GL11.GL_PROJECTION)
      GL11.glPushMatrix()
      GL11.glLoadIdentity()
      GL11.glOrtho(0, width, 0, height, -1, 1)
      GL11.glMatrixMode(GL11.GL_MODELVIEW)
      GL11.glPushMatrix()
      GL11.glLoadIdentity()
      GL11.glPushAttrib(GL11.GL_ENABLE_BIT)
      GL11.glDisable(GL11.GL_CULL_FACE)
      draw(cells, columns, lines, model.scaling(width, height, 1), 1, inWorld = false, Blend.Opaque)
      GL11.glPopAttrib()
      GL11.glPopMatrix()
      GL11.glMatrixMode(GL11.GL_PROJECTION)
      GL11.glPopMatrix()
      GL11.glMatrixMode(GL11.GL_MODELVIEW)
      buffer.packVersion = buffer.version
      buffer.packColumns = columns
      buffer.packLines = lines
      buffer.packGeneration = atlas.generation
    }
    RenderState.restoreTexture0()
  }

  def finishPackTargets(): Unit = if (framebufferSaved) {
    GLCaps.FBO.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFramebuffer)
    GL11.glViewport(savedViewport.get(0), savedViewport.get(1), savedViewport.get(2), savedViewport.get(3))
    framebufferSaved = false
  }

  def releasePackTarget(buffer: TextBufferRenderData) {
    val target = buffer.packTarget
    if (target != null) {
      saveFramebuffer()
      budget.release(targetBytes(target))
      target.checkDeleteFramebuffer()
      buffer.packTarget = null
    }
  }

  private def releaseCells(buffer: TextBufferRenderData) {
    if (buffer.cells != null) {
      pool.release(buffer.cells)
      buffer.cells = null
    }
  }

  private def columnsOf(buffer: TextBufferRenderData, viewport: (Int, Int)) = math.min(viewport._1, buffer.data.width)

  private def linesOf(buffer: TextBufferRenderData, viewport: (Int, Int), columns: Int) =
    math.min(math.min(viewport._2, buffer.data.height), ScreenSheet.Cells / math.max(columns, 1))

  private def targetBytes(target: CustomFramebuffer) = target.framebufferWidth.toLong * target.framebufferHeight * 4

  private def prepare(buffer: TextBufferRenderData, columns: Int, lines: Int): ScreenCells = {
    val size = columns * lines
    if (staging.capacity < size * TextCells.BytesPerCell) staging = BufferUtils.createByteBuffer(size * TextCells.BytesPerCell)
    val cells = ScreenCellUpdate.acquire(pool, buffer, size)
    ScreenCellUpdate.update(Upload, atlas, staging, buffer.data, buffer.rows, cells, columns, lines)
    cells
  }

  private def saveFramebuffer(): Unit = if (!framebufferSaved) {
    savedFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
    savedViewport.clear()
    GL11.glGetInteger(GL11.GL_VIEWPORT, savedViewport)
    framebufferSaved = true
  }

  private def draw(cells: ScreenCells, columns: Int, lines: Int, transform: Matrix4fc, fade: Float, inWorld: Boolean, blendMode: Int) {
    val shader = Programs.text
    val program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
    GL11.glPushAttrib(RenderState.DrawAttribBits)
    Blend(blendMode)
    GL11.glDepthMask(false)
    shader.use()
    GL20.glUniform4f(shader.loc(Programs.textCells), cells.start, 0, columns, lines)
    GL20.glUniform1f(shader.loc(Programs.textFade), fade)
    GL20.glUniform2f(shader.loc(Programs.textGlyphScale), 1f / GlyphAtlas.Width, 1f / atlas.height)
    Fog.upload(shader, Programs.textFog, Programs.textFogColor, inWorld)
    shader.uploadModel(transform)
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, cells.sheet.texture)
    // Never unbind the atlas: under Angelica, changing a non-zero unit's binding flushes and ends TESR batching.
    OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE2)
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.texture)
    OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit)
    quad.drawGeometry()
    GL11.glPopAttrib()
    GL20.glUseProgram(program)
  }
}
