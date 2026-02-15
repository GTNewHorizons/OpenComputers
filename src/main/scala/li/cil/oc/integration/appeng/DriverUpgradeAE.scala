package li.cil.oc.integration.appeng

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.{Slot, Tier}
import li.cil.oc.integration.opencomputers.Item
import li.cil.oc.{Constants, api}
import net.minecraft.item.ItemStack

object DriverUpgradeAE extends Item with HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.UpgradeAE1),
    api.Items.get(Constants.ItemName.UpgradeAE2),
    api.Items.get(Constants.ItemName.UpgradeAE3))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else new UpgradeAE(host, tier(stack))

  override def slot(stack: ItemStack) = Slot.Upgrade

  override def tier(stack: ItemStack) =
    Delegator.subItem(stack) match {
      case Some(card: ItemUpgradeAE) => card.tier
      case _ => Tier.One
    }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack))
        classOf[UpgradeAE]
      else null
  }
}
