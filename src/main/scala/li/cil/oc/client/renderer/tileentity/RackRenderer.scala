package li.cil.oc.client.renderer.tileentity

import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.client.renderer.shading.FixedFunction
import li.cil.oc.common.tileentity.Rack
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.MinecraftForge
import org.lwjgl.opengl.GL11

object RackRenderer extends TileEntitySpecialRenderer {
  private final val vOffset = 2 / 16f
  private final val vSize = 3 / 16f

  private val Front = new FrontTransforms((0.505 - 1 / 16f).toFloat)

  override def renderTileEntityAt(tileEntity: TileEntity, x: Double, y: Double, z: Double, f: Float) = {
    RenderState.checkError(this, ".renderTileEntityAt: entering (aka: wasntme)")

    val rack = tileEntity.asInstanceOf[Rack]
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)

    GL11.glPushMatrix()

    GL11.glTranslated(x, y, z)
    FixedFunction.multMatrix(Front(rack.yaw))

    // Note: we manually sync the rack inventory for this to work.
    val slots = rack.getSizeInventory
    var i = 0
    while (i < slots) {
      if (rack.getStackInSlot(i) != null) {
        GL11.glPushMatrix()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)

        val v0 = vOffset + i * vSize
        val v1 = vOffset + (i + 1) * vSize
        val event = new RackMountableRenderEvent.TileEntity(rack, i, rack.lastData(i), v0, v1)
        MinecraftForge.EVENT_BUS.post(event)

        GL11.glPopAttrib()
        GL11.glPopMatrix()
      }
      i += 1
    }

    GL11.glPopMatrix()
    GL11.glPopAttrib()

    RenderState.checkError(this, ".renderTileEntityAt: leaving")
  }
}
