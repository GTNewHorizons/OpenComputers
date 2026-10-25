package li.cil.oc.client.renderer.gui

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import li.cil.oc.api
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.client.renderer.shading.Blend
import li.cil.oc.client.renderer.shading.MeshDraw
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureManager
import org.lwjgl.opengl.GL11

object BufferRenderer {
  val margin = 7

  val innerMargin = 1

  private var textureManager: Option[TextureManager] = None

  private val background = new CapturedMesh

  private var backgroundWidth = -1

  private var backgroundHeight = -1

  private var backgroundForRobot = false

  def init(tm: TextureManager) = this.synchronized(if (textureManager.isEmpty) {
    textureManager = Some(tm)
    Textures.init(tm)
  })

  def drawBackground(bufferWidth: Int, bufferHeight: Int, forRobot: Boolean = false) =
    if (textureManager.isDefined) {
      val innerWidth = innerMargin * 2 + bufferWidth
      val innerHeight = innerMargin * 2 + bufferHeight

      if (bufferWidth != backgroundWidth || bufferHeight != backgroundHeight || forRobot != backgroundForRobot) {
        background.reset()
        try emitBackground(background.bucket(DefaultVertexFormat.POSITION_TEXTURE, Textures.guiBorders), innerWidth, innerHeight, forRobot)
        finally background.closeBucket()
        backgroundWidth = bufferWidth
        backgroundHeight = bufferHeight
        backgroundForRobot = forRobot
      }
      MeshDraw.drawUnlit(background, MeshDraw.Identity, Blend.Alpha, 1, 1, 1, 1, inWorld = false)
    }

  private def emitBackground(t: Tessellator, innerWidth: Int, innerHeight: Int, forRobot: Boolean) {
    val margin = if (forRobot) 2 else 7
    val (c0, c1, c2, c3) = if (forRobot) (5, 7, 9, 11) else (0, 7, 9, 16)

    // Top border (left corner, middle bar, right corner).
    drawBorder(t,
      0, 0, margin, margin,
      c0, c0, c1, c1)
    drawBorder(t,
      margin, 0, innerWidth, margin,
      c1 + 0.25, c0, c2 - 0.25, c1)
    drawBorder(t,
      margin + innerWidth, 0, margin, margin,
      c2, c0, c3, c1)

    // Middle area (left bar, screen background, right bar).
    drawBorder(t,
      0, margin, margin, innerHeight,
      c0, c1 + 0.25, c1, c2 - 0.25)
    drawBorder(t,
      margin, margin, innerWidth, innerHeight,
      c1 + 0.25, c1 + 0.25, c2 - 0.25, c2 - 0.25)
    drawBorder(t,
      margin + innerWidth, margin, margin, innerHeight,
      c2, c1 + 0.25, c3, c2 - 0.25)

    // Bottom border (left corner, middle bar, right corner).
    drawBorder(t,
      0, margin + innerHeight, margin, margin,
      c0, c2, c1, c3)
    drawBorder(t,
      margin, margin + innerHeight, innerWidth, margin,
      c1 + 0.25, c2, c2 - 0.25, c3)
    drawBorder(t,
      margin + innerWidth, margin + innerHeight, margin, margin,
      c2, c2, c3, c3)
  }

  def drawText(screen: api.internal.TextBuffer) =
    if (textureManager.isDefined) {
      GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT)
      GL11.glDepthMask(false)
      val changed = screen.renderText()
      GL11.glPopAttrib()
      changed
    }
    else false

  private def drawBorder(t: Tessellator, x: Double, y: Double, w: Double, h: Double, u1: Double, v1: Double, u2: Double, v2: Double) = {
    val u1d = u1 / 16.0
    val u2d = u2 / 16.0
    val v1d = v1 / 16.0
    val v2d = v2 / 16.0
    t.addVertexWithUV(x, y + h, 0, u1d, v2d)
    t.addVertexWithUV(x + w, y + h, 0, u2d, v2d)
    t.addVertexWithUV(x + w, y, 0, u2d, v1d)
    t.addVertexWithUV(x, y, 0, u1d, v1d)
  }
}
