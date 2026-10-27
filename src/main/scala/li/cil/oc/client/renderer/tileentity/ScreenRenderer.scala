package li.cil.oc.client.renderer.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.common.tileentity.Screen
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.util.ForgeDirection
import org.lwjgl.opengl.GL11

object ScreenRenderer extends TileEntitySpecialRenderer {
  private val maxRenderDistanceSq = Settings.get.maxScreenTextRenderDistance * Settings.get.maxScreenTextRenderDistance

  private val fadeDistanceSq = Settings.get.screenTextFadeStartDistance * Settings.get.screenTextFadeStartDistance

  private val fadeRatio = 1.0 / (maxRenderDistanceSq - fadeDistanceSq)

  private var screen: Screen = null

  private lazy val screens = Set(
    api.Items.get(Constants.BlockName.ScreenTier1),
    api.Items.get(Constants.BlockName.ScreenTier2),
    api.Items.get(Constants.BlockName.ScreenTier3))

  // ----------------------------------------------------------------------- //
  // Rendering
  // ----------------------------------------------------------------------- //

  override def renderTileEntityAt(t: TileEntity, x: Double, y: Double, z: Double, f: Float) {
    RenderState.checkError(this, ".renderTileEntityAt: entering (aka: wasntme)")

    screen = t.asInstanceOf[Screen]
    if (!screen.isOrigin) {
      return
    }

    val distance = playerDistanceSq() / math.min(screen.width, screen.height)
    if (distance > maxRenderDistanceSq) {
      return
    }

    // Crude check whether screen text can be seen by the local player based
    // on the player's position -> angle relative to screen.
    val screenFacing = screen.facing.getOpposite
    if (screenFacing.offsetX * (x + 0.5) + screenFacing.offsetY * (y + 0.5) + screenFacing.offsetZ * (z + 0.5) < 0) {
      return
    }

    RenderState.checkError(this, ".renderTileEntityAt: checks")

    GL11.glPushMatrix()
    GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5)

    if (showsUpIndicator) {
      GL11.glPushMatrix()
      transform()
      GL11.glTranslatef(screen.width / 2f - 0.5f, screen.height / 2f - 0.5f, 0.05f)
      MeshBackend.active.drawUpIndicator()
      GL11.glPopMatrix()
    }

    if (screen.buffer.isRenderingEnabled) {
      TextBufferRenderCache.worldFade = fadeAlpha(distance)
      TextBufferRenderCache.inWorld = true
      val profiler = Minecraft.getMinecraft.mcProfiler
      profiler.startSection("opencomputers:screen_text")
      try draw()
      finally {
        TextBufferRenderCache.inWorld = false
        profiler.endSection()
      }
    }

    GL11.glPopMatrix()

    RenderState.checkError(this, ".renderTileEntityAt: leaving")
  }

  private def fadeAlpha(distance: Double): Float =
    if (distance > fadeDistanceSq) math.max(0, 1 - ((distance - fadeDistanceSq) * fadeRatio).toFloat)
    else 1f

  private def transform() {
    BlockTransforms.rotateYaw(screen.yaw)
    BlockTransforms.rotatePitch(screen.pitch)

    // Fit area to screen (bottom left = bottom left).
    GL11.glTranslatef(-0.5f, -0.5f, 0.5f)
    GL11.glTranslatef(0, screen.height, 0)

    // Flip text upside down.
    GL11.glScalef(1, -1, 1)
  }

  private def showsUpIndicator: Boolean = (screen.facing == ForgeDirection.UP || screen.facing == ForgeDirection.DOWN) && {
    val player = Minecraft.getMinecraft.thePlayer
    val stack = player.getHeldItem
    stack != null && (Wrench.holdsApplicableWrench(player, screen.position) || screens.contains(api.Items.get(stack)))
  }

  private def draw() {
    RenderState.checkError(this, ".draw: entering (aka: wasntme)")

    val sx = screen.width
    val sy = screen.height
    val tw = sx * 16f
    val th = sy * 16f

    transform()

    // Offset from border.
    GL11.glTranslatef(sx * 2.25f / tw, sy * 2.25f / th, 0)

    // Inner size (minus borders).
    val isx = sx - (4.5f / 16)
    val isy = sy - (4.5f / 16)

    // Scale based on actual buffer size.
    val sizeX = screen.buffer.renderWidth
    val sizeY = screen.buffer.renderHeight
    val scaleX = isx / sizeX
    val scaleY = isy / sizeY
    if (true) {
      if (scaleX > scaleY) {
        GL11.glTranslatef(sizeX * 0.5f * (scaleX - scaleY), 0, 0)
        GL11.glScalef(scaleY, scaleY, 1)
      }
      else {
        GL11.glTranslatef(0, sizeY * 0.5f * (scaleY - scaleX), 0)
        GL11.glScalef(scaleX, scaleX, 1)
      }
    }
    else {
      // Stretch to fit.
      GL11.glScalef(scaleX, scaleY, 1)
    }

    // Slightly offset the text so it doesn't clip into the screen.
    GL11.glTranslated(0, 0, 0.01)

    RenderState.checkError(this, ".draw: setup")

    // Render the actual text.
    screen.buffer.renderText()

    RenderState.checkError(this, ".draw: text")
  }

  private def playerDistanceSq() = {
    val player = Minecraft.getMinecraft.thePlayer
    val bounds = screen.getRenderBoundingBox

    val px = player.posX
    val py = player.posY
    val pz = player.posZ

    val ex = bounds.maxX - bounds.minX
    val ey = bounds.maxY - bounds.minY
    val ez = bounds.maxZ - bounds.minZ
    val cx = bounds.minX + ex * 0.5
    val cy = bounds.minY + ey * 0.5
    val cz = bounds.minZ + ez * 0.5
    val dx = px - cx
    val dy = py - cy
    val dz = pz - cz

    (if (dx < -ex) {
      val d = dx + ex
      d * d
    }
    else if (dx > ex) {
      val d = dx - ex
      d * d
    }
    else 0) + (if (dy < -ey) {
      val d = dy + ey
      d * d
    }
    else if (dy > ey) {
      val d = dy - ey
      d * d
    }
    else 0) + (if (dz < -ez) {
      val d = dz + ez
      d * d
    }
    else if (dz > ez) {
      val d = dz - ez
      d * d
    }
    else 0)
  }
}