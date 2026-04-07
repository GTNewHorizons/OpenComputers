package li.cil.oc.integration.ae2fc

import appeng.api.parts.IPartHost
import appeng.api.storage.data.IAEFluidStack
import com.glodblock.github.common.parts.PartFluidExportBus
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.internal.PartSharedItemBusBase
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import scala.reflect.ClassTag

object DriverFluidExportBus extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(obj => {
        obj != null
      }).exists(_.isInstanceOf[PartFluidExportBus])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val host: IPartHost)(implicit val tag: ClassTag[PartFluidExportBus]) extends ManagedTileEntityEnvironment[IPartHost](host, "fluid_exportbus") with NamedBlock with PartSharedItemBusBase[PartFluidExportBus] {

    override def preferredName = "fluid_exportbus"

    override def priority = 2

    @Callback(doc = "function(side:number, [ slot:number]):boolean -- Get the configuration of the export bus pointing in the specified direction.")
    def getExportConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.getPartConfig(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number]):boolean OR function(side:number[, slot:number][, detail: table):boolean -- Configure the export bus pointing in the specified direction to export item stacks matching the specified descriptor.")
    def setExportConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.setPartConfig[IAEFluidStack](context, args)

    @Callback(doc = "function(side:number):number -- Get the number of valid slots in this export bus.")
    def getExportSlotSize(context: Context, args: Arguments): Array[AnyRef] = getSlotSize(context, args)

    @Callback(doc = "function(side:number):boolean -- Get the ore filter of the export bus pointing in the specified direction.")
    def getExportOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.getPartOreFilter(context, args)

    @Callback(doc = "function(side:number, filter: String):boolean -- Set the ore filter of the export bus pointing in the specified direction.")
    def setExportOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.setPartOreFilter(context, args)
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (Ae2FcUtil.isFluidExportBus(stack))
        classOf[Environment]
      else null
  }
}
