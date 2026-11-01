package li.cil.oc.integration.opencomputers

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.{EnvironmentHost, ManagedEnvironment}
import li.cil.oc.common.{Slot, Tier}
import li.cil.oc.server.component
import li.cil.oc.util.BlockPosition
import li.cil.oc.api.internal
import li.cil.oc.{Constants, Settings, api}
import net.minecraft.item.ItemStack
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.util.ForgeDirection

/**
  * @author Vexatos
  */
object DriverUpgradeMF extends Item with HostAware {
  override def worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    api.Items.get(Constants.ItemName.MFU))

  override def worksWith(stack: ItemStack, host: Class[_ <: EnvironmentHost]): Boolean =
    worksWith(stack) && (isAdapter(host) || isRobot(host) || isDrone(host))

  override def slot(stack: ItemStack): String = Slot.Upgrade

  override def tier(stack: ItemStack) = Tier.Three

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment = {
    if (host.world != null && !host.world.isRemote) {
      val linked = linkedBlock(stack)
      host match {
        // Robots and drones bind it themselves, so it works unlinked too.
        case _: internal.Robot | _: internal.Drone =>
          return new component.UpgradeMF(host, linked.map(_._1), linked.map(_._2).getOrElse(ForgeDirection.UNKNOWN))
        case _ => linked.foreach { case (position, side) => return new component.UpgradeMF(host, Some(position), side) }
      }
    }
    null
  }

  // The block and side a player linked the MFU to by sneak-clicking it.
  private def linkedBlock(stack: ItemStack): Option[(BlockPosition, ForgeDirection)] =
    if (!stack.hasTagCompound) None
    else stack.getTagCompound.getIntArray(Settings.namespace + "coord") match {
      case Array(x, y, z, dim, side) =>
        Option(DimensionManager.getWorld(dim)).map(world => (BlockPosition(x, y, z, world), ForgeDirection.getOrientation(side)))
      case _ => None
    }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack))
        classOf[component.UpgradeMF]
      else null
  }

}
