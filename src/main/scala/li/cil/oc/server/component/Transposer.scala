package li.cil.oc.server.component

import java.util
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.{Items, prefab}
import li.cil.oc.common.item.data.TransposerData.FLUID_TRANSFER_RATE
import li.cil.oc.common.tileentity
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._

import scala.collection.convert.WrapAsJava._
import scala.language.existentials

object Transposer {

  abstract class Common extends prefab.ManagedEnvironment with traits.WorldInventoryAnalytics with traits.WorldTankAnalytics with traits.WorldFluidContainerAnalytics with traits.InventoryTransfer with traits.FluidContainerTransfer with DeviceInfo {
    override val node = api.Network.newNode(this, Visibility.Network).
      withComponent("transposer").
      withConnector().
      create()

    private final lazy val deviceInfo = Map(
      DeviceAttribute.Class -> DeviceClass.Generic,
      DeviceAttribute.Description -> "Transposer",
      DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product -> "TP4k-iX"
    )

    override def getDeviceInfo: util.Map[String, String] = deviceInfo

    override protected def checkSideForAction(args: Arguments, n: Int) =
      args.checkSideAny(n)

    override def onTransferContents(): Option[String] = {
      if (node.tryChangeBuffer(-Settings.get.transposerCost)) None
      else Option("not enough energy")
    }
  }

  class Block(val host: tileentity.Transposer) extends Common {
    override def position = BlockPosition(host)

    override def onTransferContents(): Option[String] = {
      val result = super.onTransferContents()
      if (result.isEmpty) ServerPacketSender.sendTransposerActivity(host)
      result
    }

    override def fluidTransferRate(): Int = host.info.fluidTransferRate
  }

  class Upgrade(val host: EnvironmentHost) extends Common {
    node.setVisibility(Visibility.Neighbors)

    override def position = BlockPosition(host)

    override def fluidTransferRate(): Int = {
      host match {
        case microcontroller: tileentity.Microcontroller =>
          microcontroller
            .info
            .components
            .find(_.isItemEqual(Items.get(Constants.BlockName.Transposer).createItemStack(1)))
            .filter(_.hasTagCompound)
            .map(_.getTagCompound)
            .filter(_.hasKey(FLUID_TRANSFER_RATE))
            .map(_.getInteger(FLUID_TRANSFER_RATE))
            .getOrElse(Settings.get.transposerFluidTransferRate)
        case _ => 0
      }
    }
  }

}
