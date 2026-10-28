package li.cil.oc.client.renderer.item

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.item.UpgradeRenderer.MountPointName
import li.cil.oc.api.event.RobotRenderEvent.MountPoint
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.integration.opencomputers.Item
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.Tessellator
import net.minecraft.item.ItemStack
import net.minecraft.util.AxisAlignedBB
import org.lwjgl.opengl.GL11

object UpgradeRenderer {
  lazy val craftingUpgrade = api.Items.get(Constants.ItemName.CraftingUpgrade)
  lazy val generatorUpgrade = api.Items.get(Constants.ItemName.GeneratorUpgrade)
  lazy val inventoryUpgrade = api.Items.get(Constants.ItemName.InventoryUpgrade)

  def preferredMountPoint(stack: ItemStack, availableMountPoints: java.util.Set[String]): String = {
    val descriptor = api.Items.get(stack)

    if (descriptor == craftingUpgrade || descriptor == generatorUpgrade || descriptor == inventoryUpgrade) {
      if (descriptor == generatorUpgrade && availableMountPoints.contains(MountPointName.BottomBack)) MountPointName.BottomBack
      else if (descriptor == inventoryUpgrade && availableMountPoints.contains(MountPointName.TopBack)) MountPointName.TopBack
      else MountPointName.Any
    }
    else MountPointName.None
  }

  def canRender(stack: ItemStack): Boolean = {
    val descriptor = api.Items.get(stack)

    descriptor == craftingUpgrade || descriptor == generatorUpgrade || descriptor == inventoryUpgrade
  }

  def render(stack: ItemStack, mountPoint: MountPoint): Unit = {
    val descriptor = api.Items.get(stack)

    if (descriptor == api.Items.get(Constants.ItemName.CraftingUpgrade)) {
      drawSimpleBlock(mountPoint, Crafting)

      RenderState.checkError(this, ".renderItem: crafting upgrade")
    }

    else if (descriptor == api.Items.get(Constants.ItemName.GeneratorUpgrade)) {
      drawSimpleBlock(mountPoint, if (Item.dataTag(stack).getInteger("remainingTicks") > 0) GeneratorOn else GeneratorOff)

      RenderState.checkError(this, ".renderItem: generator upgrade")
    }

    else if (descriptor == api.Items.get(Constants.ItemName.InventoryUpgrade)) {
      drawSimpleBlock(mountPoint, Inventory)

      RenderState.checkError(this, ".renderItem: inventory upgrade")
    }
  }

  private final val Crafting = 0
  private final val GeneratorOff = 1
  private final val GeneratorOn = 2
  private final val Inventory = 3

  private val textures = Array(Textures.upgradeCrafting, Textures.upgradeGenerator, Textures.upgradeGenerator, Textures.upgradeInventory)

  private val frontOffsets = Array(0f, 0f, 0.5f, 0f)

  private val meshes = new Array[CapturedMesh](textures.length)

  private val bounds = AxisAlignedBB.getBoundingBox(-0.1, -0.1, -0.1, 0.1, 0.1, 0.1)

  private def drawSimpleBlock(mountPoint: MountPoint, variant: Int) {
    GL11.glRotatef(mountPoint.rotation.getW, mountPoint.rotation.getX, mountPoint.rotation.getY, mountPoint.rotation.getZ)
    GL11.glTranslatef(mountPoint.offset.getX, mountPoint.offset.getY, mountPoint.offset.getZ)

    var mesh = meshes(variant)
    if (mesh == null) {
      mesh = CapturedMesh.build(DefaultVertexFormat.POSITION_TEXTURE, textures(variant))(emitSimpleBlock(_, frontOffsets(variant)))
      meshes(variant) = mesh
    }
    mesh.draw()
  }

  private def emitSimpleBlock(t: Tessellator, frontOffset: Float) {
    t.addVertexWithUV(bounds.minX, bounds.minY, bounds.maxZ, frontOffset, 0.5f)
    t.addVertexWithUV(bounds.maxX, bounds.minY, bounds.maxZ, frontOffset + 0.5f, 0.5f)
    t.addVertexWithUV(bounds.maxX, bounds.maxY, bounds.maxZ, frontOffset + 0.5f, 0)
    t.addVertexWithUV(bounds.minX, bounds.maxY, bounds.maxZ, frontOffset, 0)

    t.addVertexWithUV(bounds.maxX, bounds.maxY, bounds.maxZ, 1, 0.5f)
    t.addVertexWithUV(bounds.maxX, bounds.maxY, bounds.minZ, 1, 1)
    t.addVertexWithUV(bounds.minX, bounds.maxY, bounds.minZ, 0.5f, 1)
    t.addVertexWithUV(bounds.minX, bounds.maxY, bounds.maxZ, 0.5f, 0.5f)

    t.addVertexWithUV(bounds.minX, bounds.minY, bounds.maxZ, 0.5f, 0.5f)
    t.addVertexWithUV(bounds.minX, bounds.minY, bounds.minZ, 0.5f, 1)
    t.addVertexWithUV(bounds.maxX, bounds.minY, bounds.minZ, 1, 1)
    t.addVertexWithUV(bounds.maxX, bounds.minY, bounds.maxZ, 1, 0.5f)

    t.addVertexWithUV(bounds.maxX, bounds.maxY, bounds.maxZ, 0, 0.5f)
    t.addVertexWithUV(bounds.maxX, bounds.minY, bounds.maxZ, 0, 1)
    t.addVertexWithUV(bounds.maxX, bounds.minY, bounds.minZ, 0.5f, 1)
    t.addVertexWithUV(bounds.maxX, bounds.maxY, bounds.minZ, 0.5f, 0.5f)

    t.addVertexWithUV(bounds.minX, bounds.minY, bounds.maxZ, 0, 1)
    t.addVertexWithUV(bounds.minX, bounds.maxY, bounds.maxZ, 0, 0.5f)
    t.addVertexWithUV(bounds.minX, bounds.maxY, bounds.minZ, 0.5f, 0.5f)
    t.addVertexWithUV(bounds.minX, bounds.minY, bounds.minZ, 0.5f, 1)
  }
}
