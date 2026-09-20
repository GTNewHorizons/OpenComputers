package li.cil.oc.integration.ae2fc

import appeng.api.storage.data.IAEFluidStack
import com.glodblock.github.common.tile.TileFluidInterface
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.ResultWrapper._
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

object DriverBlockFluidInterface extends DriverSidedTileEntity {
  def getTileEntityClass: Class[_] = classOf[TileFluidInterface]

  def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment =
    new Environment(world.getTileEntity(x, y, z).asInstanceOf[TileFluidInterface])

  final class Environment(val tile: TileFluidInterface) extends ManagedTileEntityEnvironment[TileFluidInterface](tile, "fluid_interface") with NamedBlock {

    override def preferredName = "fluid_interface"

    override def priority = 6

    @Callback(doc = "function([slot:number]):table -- Get the configuration of the fluid interface.")
    def getFluidInterfaceConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val slot = args.optInteger(0, 0)
      result(tile.getConfig.getStackInSlot(slot))
    }

    @Callback(doc = "function([slot:number][, detail:table]):boolean -- Configure the fluid interface.")
    def setFluidInterfaceConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val (slot, offset) = if (args.isInteger(0)) (args.checkInteger(0), 1) else (0, 0)
      val stack = if (args.count() <= offset) null.asInstanceOf[IAEFluidStack]
      else AEStackFactory.parse[IAEFluidStack](args.checkTable(offset))
      tile.setConfig(slot, stack)
      result(true)
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (Ae2FcUtil.isFluidInterface(stack))
        classOf[Environment]
      else null
  }
}
