package li.cil.oc.integration.thaumicenergistics

import appeng.api.parts.IPartHost
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.integration.appeng.internal.PartSharedItemBusBase
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ResultWrapper._
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection
import thaumicenergistics.api.ThEApi
import thaumicenergistics.common.parts.PartEssentiaExportBus
import thaumicenergistics.common.storage.AEEssentiaStack

import scala.reflect.{ClassTag, classTag}

object DriverEssentiaExportBus extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(obj => {
        obj != null
      }).exists(_.isInstanceOf[PartEssentiaExportBus])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world, world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val world: World, val host: IPartHost)(implicit val tag: ClassTag[PartEssentiaExportBus]) extends ManagedTileEntityEnvironment[IPartHost](host, "essentia_exportbus") with NamedBlock with PartSharedItemBusBase[PartEssentiaExportBus] {
    override def preferredName = "essentia_exportbus"

    override def priority = 2

    @Callback(doc = "function(side:number[, slot:number]):string -- Get the configuration of the export bus pointing in the specified direction.")
    def getExportConfiguration(context: Context, args: Arguments): Array[AnyRef] = result(getPartConfig(context, args))

    @Callback(doc = "function(side:number[, slot:number][, aspect:string]):boolean -- Configure the export bus pointing in the specified direction to export essentia matching the specified type.")
    def setExportConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      setPartConfig[AEEssentiaStack](context, args)
      result(true)
    }

    @Callback(doc = "function(side:number):number -- Get the number of valid slots in this export bus.")
    def getExportSlotSize(context: Context, args: Arguments): Array[AnyRef] = result(getSlotSize(context, args))

    @Callback(doc = "function(side:number):boolean -- Get whether or not essentia exported into a void jar will allow voiding")
    def getVoidAllowed(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val part = getPart(side)
      result(part.getVoidAllowed)
    }

    @Callback(doc = "function(side:number, allowed:boolean):boolean -- Set void mode")
    def setVoidAllowed(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val mode = args.checkBoolean(1)
      val part = getPart(side)
      var didSomething = false
      if (mode != part.getVoidAllowed) {
        part.toggleVoidAllowed()
        didSomething = true
      }
      result(didSomething)
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (ThEApi.instance.parts.Essentia_ExportBus.getStack.isItemEqual(stack))
        classOf[Environment]
      else null
  }
}
