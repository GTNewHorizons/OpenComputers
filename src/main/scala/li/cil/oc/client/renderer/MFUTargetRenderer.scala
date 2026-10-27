package li.cil.oc.client.renderer

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.util.Constants.NBT
import org.lwjgl.opengl.GL11

object MFUTargetRenderer {
  private val color = 0x00FF00
  private lazy val mfu = api.Items.get(Constants.ItemName.MFU)

  @SubscribeEvent
  def onRenderWorldLastEvent(e: RenderWorldLastEvent) {
    val mc = Minecraft.getMinecraft
    val player = mc.thePlayer
    if (player == null) return
    player.getHeldItem match {
      case stack: ItemStack if api.Items.get(stack) == mfu && stack.hasTagCompound =>
        val data = stack.getTagCompound
        if (data.hasKey(Settings.namespace + "coord", NBT.TAG_INT_ARRAY)) {
          val Array(x, y, z, dimension, side) = data.getIntArray(Settings.namespace + "coord")
          if (player.getEntityWorld.provider.dimensionId != dimension) return
          if (player.getDistance(x, y, z) > 64) return

          val bounds = BlockPosition(x, y, z).bounds.expand(0.1, 0.1, 0.1)

          val px = player.lastTickPosX + (player.posX - player.lastTickPosX) * e.partialTicks
          val py = player.lastTickPosY + (player.posY - player.lastTickPosY) * e.partialTicks
          val pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * e.partialTicks

          RenderState.checkError(this, ".onRenderWorldLastEvent: entering (aka: wasntme)")

          GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
          GL11.glPushMatrix()
          GL11.glTranslated(-px, -py, -pz)
          RenderState.makeItBlend()
          GL11.glDisable(GL11.GL_LIGHTING)
          GL11.glDisable(GL11.GL_TEXTURE_2D)
          GL11.glDisable(GL11.GL_DEPTH_TEST)
          GL11.glDisable(GL11.GL_CULL_FACE)

          GL11.glColor4f(
            ((color >> 16) & 0xFF) / 255f,
            ((color >> 8) & 0xFF) / 255f,
            ((color >> 0) & 0xFF) / 255f,
            0.25f)
          GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
          DebugBox.drawBox(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)
          GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
          DebugBox.drawBoxFace(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ, side)

          GL11.glPopMatrix()
          GL11.glPopAttrib()

          RenderState.checkError(this, ".onRenderWorldLastEvent: leaving")
        }
      case _ => // Nothing
    }
  }
}
