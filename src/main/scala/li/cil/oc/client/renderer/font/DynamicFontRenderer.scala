package li.cil.oc.client.renderer.font

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import gnu.trove.map.hash.TIntObjectHashMap
import li.cil.oc.Settings
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.client.renderer.font.DynamicFontRenderer.CharTexture
import li.cil.oc.util.ExtendedUnicodeHelper
import li.cil.oc.util.FontUtils
import li.cil.oc.util.PackedColor
import li.cil.oc.util.RenderState
import li.cil.oc.util.TextBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.ITextureObject
import net.minecraft.client.resources.IReloadableResourceManager
import net.minecraft.client.resources.IResourceManager
import net.minecraft.client.resources.IResourceManagerReloadListener
import net.minecraft.util.ResourceLocation
import org.lwjgl.BufferUtils
import org.lwjgl.opengl._

import scala.collection.mutable

class DynamicFontRenderer extends IResourceManagerReloadListener {
  private final val basicChars = """☺☻♥♦♣♠•◘○◙♂♀♪♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼ !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~⌂ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■"""

  def charRenderWidth = charWidth / 2

  def charRenderHeight = charHeight / 2

  def generateChars(chars: Array[Char]) {
    for (char <- chars) {
      generateChar(char)
    }
  }

  def generateChars(chars: Array[Int]) {
    for (char <- chars) {
      generateChar(char)
    }
  }

  def emitGeometry(mesh: CapturedMesh, buffer: TextBuffer, viewportWidth: Int, viewportHeight: Int) {
    val format = buffer.format
    val rows = viewportHeight min buffer.height

    // Background first. We try to merge adjacent backgrounds of the same
    // color to reduce the number of quads we have to draw.
    val t = mesh.bucket(DefaultVertexFormat.POSITION_COLOR, null)
    var y = 0
    while (y < rows) {
      val color = buffer.color(y)
      var cbg = 0x000000
      var x = 0
      var width = 0
      var c = 0
      while (c < color.length && x + width < viewportWidth) {
        val col = PackedColor.unpackBackground(color(c), format)
        if (col != cbg) {
          drawQuad(t, cbg, x, y, width)
          cbg = col
          x += width
          width = 0
        }
        width = width + 1
        c += 1
      }
      drawQuad(t, cbg, x, y, width)
      y += 1
    }

    RenderState.checkError(this, ".emitGeometry: background")

    var i = 0
    while (i < textureCount) {
      val pt = mesh.bucket(DefaultVertexFormat.POSITION_TEXTURE_COLOR, textureLocation(i))
      setActivePage(i)
      y = 0
      while (y < rows) {
        val line = buffer.buffer(y)
        val color = buffer.color(y)
        val ty = y * charHeight
        var cfg = -1
        var tx = 0f
        var n = 0
        while (n < viewportWidth) {
          val ch = line(n)
          val col = PackedColor.unpackForeground(color(n), format)
          // Check if color changed.
          if (col != cfg) {
            cfg = col
            pt.setColorOpaque_I(cfg)
          }
          // Don't render whitespace.
          if (ch != ' ') {
            drawChar(pt, tx, ty, ch)
          }
          tx += charWidth
          n += 1
        }
        y += 1
      }
      i += 1
    }

    RenderState.checkError(this, ".emitGeometry: foreground")
  }

  def drawString(s: String, x: Int, y: Int, color: Int): Unit = {
    val sLength = ExtendedUnicodeHelper.length(s)
    val t = Tessellator.instance

    GL11.glPushMatrix()
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)

    GL11.glTranslatef(x, y, 0)
    GL11.glScalef(0.5f, 0.5f, 1)
    GL11.glDepthMask(false)
    GL11.glEnable(GL11.GL_TEXTURE_2D)

    for (i <- 0 until textureCount) {
      bindTexture(i)
      t.startDrawingQuads()
      t.setColorOpaque_I(color)
      var tx = 0f
      var cx = 0
      for (n <- 0 until sLength) {
        val ch = s.codePointAt(cx)
        // Don't render whitespace.
        if (ch != ' ') {
          drawChar(t, tx, 0, ch)
        }
        tx += charWidth
        cx = s.offsetByCodePoints(cx, 1)
      }
      t.draw()
    }

    GL11.glPopAttrib()
    GL11.glPopMatrix()
  }

  val glyphProvider: IGlyphProvider = new FontParserHex()

  private val textures = mutable.ArrayBuffer.empty[CharTexture]

  private val charMap = new TIntObjectHashMap[DynamicFontRenderer.CharIcon]()

  private var activeTexture: CharTexture = _

  private var generation = 0

  private val pageLocations = mutable.ArrayBuffer.empty[ResourceLocation]

  initialize()

  Minecraft.getMinecraft.getResourceManager match {
    case reloadable: IReloadableResourceManager => reloadable.registerReloadListener(this)
    case _ =>
  }

  def initialize() {
    for (texture <- textures) {
      texture.delete()
    }
    textures.clear()
    charMap.clear()
    generation += 1
    textures += new DynamicFontRenderer.CharTexture(this)
    activeTexture = textures.head
    generateChars(basicChars.toCharArray)
  }

  def onResourceManagerReload(manager: IResourceManager) {
    glyphProvider.initialize()
    initialize()
  }

  private[renderer] def charWidth = glyphProvider.getGlyphWidth

  private[renderer] def charHeight = glyphProvider.getGlyphHeight

  private def textureCount = textures.length

  private def bindTexture(index: Int) {
    activeTexture = textures(index)
    activeTexture.bind()
    RenderState.checkError(this, ".bindTexture")
  }

  def cacheGeneration = generation

  private def textureLocation(page: Int): ResourceLocation = {
    while (pageLocations.length <= page) {
      val index = pageLocations.length
      val location = new ResourceLocation(Settings.resourceDomain, "dynamicfont/" + index)
      Minecraft.getMinecraft.getTextureManager.loadTexture(location, new DynamicFontRenderer.PageTexture(this, index))
      pageLocations += location
    }
    pageLocations(page)
  }

  private def generateChar(char: Int) {
    getOrCreate(char)
  }

  private def setActivePage(page: Int) {
    activeTexture = textures(page)
  }

  private def drawChar(t: Tessellator, tx: Float, ty: Float, char: Int) {
    val icon = charMap.get(char)
    if (icon != null && icon.texture == activeTexture) {
      icon.draw(t, tx, ty)
    }
  }

  private def drawQuad(t: Tessellator, color: Int, x: Int, y: Int, width: Int) = if (color != 0 && width > 0) {
    val x0 = x * charWidth
    val x1 = (x + width) * charWidth
    val y0 = y * charHeight
    val y1 = (y + 1) * charHeight
    t.setColorOpaque_I(color)
    t.addVertex(x0, y1, 0)
    t.addVertex(x1, y1, 0)
    t.addVertex(x1, y0, 0)
    t.addVertex(x0, y0, 0)
  }

  private def getOrCreate(char: Int): DynamicFontRenderer.CharIcon = {
    if (charMap.containsKey(char)) charMap.get(char)
    else {
      val icon = createCharIcon(char)
      charMap.put(char, icon)
      icon
    }
  }

  private def createCharIcon(char: Int): DynamicFontRenderer.CharIcon = {
    if (FontUtils.wcwidth(char) < 1 || !glyphProvider.hasGlyph(char)) {
      if (char == '?') null
      else getOrCreate('?')
    }
    else {
      if (textures.last.isFull(char)) {
        textures += new DynamicFontRenderer.CharTexture(this)
        textures.last.bind()
      }
      textures.last.add(char)
    }
  }
}

object DynamicFontRenderer {
  private val size = 256

  class PageTexture(owner: DynamicFontRenderer, index: Int) extends ITextureObject {
    def loadTexture(manager: IResourceManager): Unit = {}

    def getGlTextureId: Int = if (index < owner.textures.length) owner.textures(index).id else 0
  }

  class CharTexture(val owner: DynamicFontRenderer) {
    private[font] val id = GL11.glGenTextures()
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
    if (Settings.get.textLinearFiltering) {
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
    } else {
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
    }
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(size * size * 4))

    RenderState.checkError(this, ".<init>: create texture")

    // Some padding to avoid bleeding.
    private val cellWidth = owner.charWidth + 2
    private val cellHeight = owner.charHeight + 2
    private val cols = size / cellWidth
    private val rows = size / cellHeight
    private val uStep = cellWidth / size.toDouble
    private val vStep = cellHeight / size.toDouble
    private val pad = 1.0 / size
    private val capacity = cols * rows

    private var chars = 0

    def delete() {
      GL11.glDeleteTextures(id)
    }

    def bind() {
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
    }

    def isFull(char: Int) = chars + FontUtils.wcwidth(char) > capacity

    def add(char: Int) = {
      val glyphWidth = FontUtils.wcwidth(char)
      val w = owner.charWidth * glyphWidth
      val h = owner.charHeight
      // Force line break if we have a char that's wider than what space remains in this row.
      if (chars % cols + glyphWidth > cols) {
        chars += 1
      }
      val x = chars % cols
      val y = chars / cols

      GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
      GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 1 + x * cellWidth, 1 + y * cellHeight, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, owner.glyphProvider.getGlyph(char))

      chars += glyphWidth

      new CharIcon(this, w, h, pad + x * uStep, pad + y * vStep, (x + glyphWidth) * uStep - pad, (y + 1) * vStep - pad)
    }
  }

  class CharIcon(val texture: CharTexture, val w: Int, val h: Int, val u1: Double, val v1: Double, val u2: Double, val v2: Double) {
    def draw(t: Tessellator, tx: Float, ty: Float) {
      t.addVertexWithUV(tx, ty + h, 0, u1, v2)
      t.addVertexWithUV(tx + w, ty + h, 0, u2, v2)
      t.addVertexWithUV(tx + w, ty, 0, u2, v1)
      t.addVertexWithUV(tx, ty, 0, u1, v1)
    }
  }

}