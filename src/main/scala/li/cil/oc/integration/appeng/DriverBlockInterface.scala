package li.cil.oc.integration.appeng

import appeng.tile.misc.TileInterface
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.internal.{BlockInterfaceEnvironmentAE2, BlockPatternEnvironment}
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

object DriverBlockInterface extends DriverSidedTileEntity {
  def getTileEntityClass: Class[_] = classOf[TileInterface]

  def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment =
    new Environment(world.getTileEntity(x, y, z).asInstanceOf[TileInterface])

  final class Environment(val tile: TileInterface) extends ManagedTileEntityEnvironment[TileInterface](tile, "me_interface") with NamedBlock with NetworkControl[TileInterface] with BlockInterfaceEnvironmentAE2 with BlockPatternEnvironment {
    override def preferredName = "me_interface"

    override def priority = 5

    //noinspection ScalaUnusedSymbol
    @Callback(doc = "function([slot:number]):table -- Get the configuration of the interface.")
    def getInterfaceConfiguration(context: Context, args: Arguments): Array[AnyRef] = getConfig(context, args)

    @Callback(doc = "function([slot:number][, database:address, entry:number[, size:number]]):boolean -- Configure the interface.")
    def setInterfaceConfiguration(context: Context, args: Arguments): Array[AnyRef] = setConfig(context, args)

    //noinspection ScalaUnusedSymbol
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

    @Callback(doc = "function(slot:number, index:number):boolean -- Clear pattern input at the given index.")
    def clearInterfacePatternInput(context: Context, args: Arguments): Array[AnyRef] =
      setPatternSlot(context, args, "in")

    @Callback(doc = "function(slot:number, index:number):boolean -- Clear pattern output at the given index.")
    def clearInterfacePatternOutput(context: Context, args: Arguments): Array[AnyRef] =
      setPatternSlot(context, args, "out")
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (AEUtil.isBlockInterface(stack))
        classOf[Environment]
      else null
  }
}