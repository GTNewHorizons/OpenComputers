package li.cil.oc.integration.appeng

import appeng.api.parts.IPartHost
import appeng.parts.misc.PartInterface
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.internal.{PartInterfaceEnvironmentAE2, PartPatternEnvironment}
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

object DriverPartInterface extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(obj => {
        obj != null
      }).exists(_.isInstanceOf[PartInterface])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val host: IPartHost) extends ManagedTileEntityEnvironment[IPartHost](host, "me_interface") with NamedBlock with PartInterfaceEnvironmentAE2 with PartPatternEnvironment[PartInterface] {
    override def preferredName = "me_interface"

    override def priority = 0

    @Callback(doc = "function(side:number[, slot:number]):table -- Get the configuration of the interface pointing in the specified direction.")
    def getInterfaceConfiguration(context: Context, args: Arguments): Array[AnyRef] = getConfig(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number[, size:number]]):boolean -- Configure the interface pointing in the specified direction.")
    def setInterfaceConfiguration(context: Context, args: Arguments): Array[AnyRef] = setConfig(context, args)

    @Callback(doc = "function([slot:number]):table -- Get the given pattern in the interface.")
    def getInterfacePattern(context: Context, args: Arguments): Array[AnyRef] = {
      val inv = getPatternInventory(context, args)
      val slot = args.optSlot(inv, 0, 0)
      val stack = inv.getStackInSlot(slot)
      result(stack)
    }

    // Note: change the index from 4 to 1
    @Callback(doc = "function(slot:number, index:number[, database:address, entry:number, size:number]):boolean OR function(slot:number, index:number[, detail:table, type:string]):boolean -- Set the pattern input at the given index.")
    def setInterfacePatternInput(context: Context, args: Arguments): Array[AnyRef] =
      setPatternSlot(context, args, "in")

    @Callback(doc = "function(slot:number, index:number[, database:address, entry:number, size:number]):boolean OR function(slot:number, index:number[, detail:table, type:string]):boolean -- Set the pattern input at the given index.")
    def setInterfacePatternOutput(context: Context, args: Arguments): Array[AnyRef] =
      setPatternSlot(context, args, "out")

    @Callback(doc = "function(slot:number, index:number, database:address, entry:number):boolean -- Store pattern input at the given index to the database entry.")
    def storeInterfacePatternInput(context: Context, args: Arguments): Array[AnyRef] =
      storeInterfacePattern(context, args, "in")

    @Callback(doc = "function(slot:number, index:number, database:address, entry:number):boolean -- Store pattern output at the given index to the database entry.")
    def storeInterfacePatternOutput(context: Context, args: Arguments): Array[AnyRef] =
      storeInterfacePattern(context, args, "out")
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (AEUtil.isPartInterface(stack))
        classOf[Environment]
      else null
  }

}