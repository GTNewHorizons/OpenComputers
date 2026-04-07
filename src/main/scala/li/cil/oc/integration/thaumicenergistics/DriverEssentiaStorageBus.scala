package li.cil.oc.integration.thaumicenergistics

import appeng.api.parts.IPartHost
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.internal.PartStorageBusBase
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection
import thaumicenergistics.api.ThEApi
import thaumicenergistics.common.parts.PartEssentiaStorageBus
import thaumicenergistics.common.storage.AEEssentiaStack

import scala.reflect.ClassTag

object DriverEssentiaStorageBus extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(obj => {
        obj != null
      }).exists(_.isInstanceOf[PartEssentiaStorageBus])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val host: IPartHost)(implicit val tag: ClassTag[PartEssentiaStorageBus]) extends ManagedTileEntityEnvironment[IPartHost](host, "essentia_storagebus") with NamedBlock with PartStorageBusBase[PartEssentiaStorageBus] {
    override def preferredName = "essentia_storagebus"

    override def priority = 2

    @Callback(doc = "function(side:number[, slot:number]):boolean -- Get the configuration of the storage bus pointing in the specified direction.")
    def getStorageConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.getPartConfig(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number]):boolean OR function(side:number[, slot:number][, detail:table]):boolean -- Configure the storage bus pointing in the specified direction to storage item stacks matching the specified descriptor.")
    def setStorageConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.setPartConfig[AEEssentiaStack](context, args)

    @Callback(doc = "function(side:number):boolean -- Get the ore filter of the storage bus pointing in the specified direction.")
    def getStorageOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.getPartOreFilter(context, args)

    @Callback(doc = "function(side:number, filter: String):boolean -- Set the ore filter of the storage bus pointing in the specified direction.")
    def setStorageOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.setPartOreFilter(context, args)

    @Callback(doc = "function(side:number):number -- Get the number of valid slots in this storage bus.")
    def getStorageSlotSize(context: Context, args: Arguments): Array[AnyRef] = this.getSlotSize(context, args)
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (ThEApi.instance.parts.Essentia_StorageBus.getStack.isItemEqual(stack))
        classOf[Environment]
      else null
  }
}
