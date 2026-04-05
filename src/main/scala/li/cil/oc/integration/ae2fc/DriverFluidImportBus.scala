package li.cil.oc.integration.ae2fc

import appeng.api.parts.IPartHost
import appeng.api.storage.data.IAEFluidStack
import com.glodblock.github.common.parts.PartFluidImportBus
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.internal.PartSharedItemBusBase
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import scala.reflect.ClassTag

object DriverFluidImportBus extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(obj => {
        obj != null
      }).exists(_.isInstanceOf[PartFluidImportBus])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val host: IPartHost)(implicit val tag: ClassTag[PartFluidImportBus]) extends ManagedTileEntityEnvironment[IPartHost](host, "fluid_importbus") with NamedBlock with PartSharedItemBusBase[PartFluidImportBus] {
    override def preferredName = "fluid_importbus"

    override def priority = 1

    @Callback(doc = "function(side:number[, slot:number]):boolean -- Get the configuration of the import bus pointing in the specified direction.")
    def getImportConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.getPartConfig(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number]):boolean OR function(side:number[, slot:number][, detail:table]):boolean -- Configure the import bus pointing in the specified direction to import item stacks matching the specified descriptor.")
    def setImportConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.setPartConfig[IAEFluidStack](context, args)

    @Callback(doc = "function(side:number):number -- Get the number of valid slots in this import bus.")
    def getImportSlotSize(context: Context, args: Arguments): Array[AnyRef] = getSlotSize(context, args)

    @Callback(doc = "function(side:number):boolean -- Get the ore filter of the import bus pointing in the specified direction.")
    def getImportOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.getPartOreFilter(context, args)

    @Callback(doc = "function(side:number, filter: String):boolean -- Set the ore filter of the import bus pointing in the specified direction.")
    def setImportOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.setPartOreFilter(context, args)
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (Ae2FcUtil.isFluidImportBus(stack))
        classOf[Environment]
      else null
  }

}
