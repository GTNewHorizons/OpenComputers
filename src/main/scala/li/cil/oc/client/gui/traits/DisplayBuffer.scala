package li.cil.oc.client.gui.traits

import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.opengl.GL11

trait DisplayBuffer extends GuiScreen {
  protected def bufferX: Int

  protected def bufferY: Int

  protected def bufferColumns: Int

  protected def bufferRows: Int

  protected var scale = 0.0

  override def initGui() = {
    super.initGui()
    BufferRenderer.init(Minecraft.getMinecraft.renderEngine)
  }

  protected def drawBufferLayer() {
    scale = changeSize(bufferColumns, bufferRows)

    RenderState.checkError(this, ".drawBufferLayer: entering (aka: wasntme)")

    GL11.glPushMatrix()
    RenderState.disableLighting()
    drawBuffer()
    GL11.glPopMatrix()

    RenderState.checkError(this, ".drawBufferLayer: buffer layer")
  }

  protected def drawBuffer()

  protected def changeSize(w: Double, h: Double): Double
}
