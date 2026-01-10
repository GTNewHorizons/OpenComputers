package li.cil.oc.integration.thaumcraft

import cpw.mods.fml.common.registry.GameRegistry
import li.cil.oc.Constants
import li.cil.oc.api.{Driver, Items}
import li.cil.oc.api.driver.item.UpgradeRenderer.MountPointName
import li.cil.oc.api.event.RobotRenderEvent.MountPoint
import li.cil.oc.api.internal.Robot
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.integration.{ModProxy, Mods}
import net.minecraft.item.ItemStack
import org.lwjgl.opengl.GL11
import thaumcraft.client.lib.UtilsFX
import thaumcraft.client.renderers.models.ModelArcaneWorkbench

object ModThaumcraft extends ModProxy {
  override def getMod = Mods.Thaumcraft

  override def initialize() {
    Driver.add(new DriverAspectContainer)
    Driver.add(new DriverInfusionMatrix)

    Driver.add(ConverterAspectItem)

    val multi = new li.cil.oc.common.item.Delegator() {
      private lazy val workbenchModel = new ModelArcaneWorkbench
      private lazy val ArcaneUpgrade = Items.get(Constants.ItemName.ArcaneCraftingUpgrade)

      override def computePreferredMountPoint(stack: ItemStack, robot: Robot, availableMountPoints: java.util.Set[String]): String = Items.get(stack) match {
        case ArcaneUpgrade => MountPointName.Any
        case _ => MountPointName.None
      }

      override def render(stack: ItemStack, mountPoint: MountPoint, robot: Robot, pt: Float): Unit = Items.get(stack) match {
        case ArcaneUpgrade =>
          UtilsFX.bindTexture("textures/models/worktable.png")
          GL11.glRotatef(mountPoint.rotation.getW, mountPoint.rotation.getX, mountPoint.rotation.getY, mountPoint.rotation.getZ)
          GL11.glTranslatef(mountPoint.offset.getX, mountPoint.offset.getY, mountPoint.offset.getZ)
          GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F)
          GL11.glTranslatef(0.0F, -0.1F, 0.0F)
          GL11.glScalef(0.2F, 0.2F, 0.2F)
          GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F)
          workbenchModel.renderAll()
        case _ =>
      }
    }

    GameRegistry.registerItem(multi, "item..thaumcraft")
    Recipes.addSubItem(new item.UpgradeArcaneCrafting(multi), Constants.ItemName.ArcaneCraftingUpgrade, "oc:arcaneCraftingUpgrade")
  }
}