package li.cil.oc.integration.ae2fc

import appeng.api.parts.IPartHost
import appeng.api.storage.data.IAEFluidStack
import com.glodblock.github.common.parts.PartFluidInterface
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.integration.appeng.internal.PartInterfaceEnvironment
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import scala.reflect.ClassTag

object DriverPartFluidInterface extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(obj => {
        obj != null
      }).exists(_.isInstanceOf[PartFluidInterface])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val host: IPartHost)(implicit val tag: ClassTag[PartFluidInterface]) extends ManagedTileEntityEnvironment[IPartHost](host, "fluid_interface") with NamedBlock with PartInterfaceEnvironment[PartFluidInterface] {
    override def preferredName = "fluid_interface"

    override def priority = 6

    @Callback(doc = "function(side:number[, slot:number]):table -- Get the configuration of the fluid interface.")
    def getFluidInterfaceConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val slot = args.optInteger(1, 0)
      result(getPart(side).getConfig.getStackInSlot(slot))
    }

    @Callback(doc = "function(side:number[, slot:number][, detail:table]):boolean -- Configure the fluid interface.")
    def setFluidInterfaceConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val (slot, offset) = if (args.isInteger(1)) (args.checkInteger(1), 2) else (0, 1)
      val stack = if (args.count() <= offset) null.asInstanceOf[IAEFluidStack]
      else AEStackFactory.parse[IAEFluidStack](args.checkTable(offset))
      getPart(side).setConfig(slot, stack)
      result(true)
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (Ae2FcUtil.isPartFluidInterface(stack))
        classOf[Environment]
      else null
  }
}