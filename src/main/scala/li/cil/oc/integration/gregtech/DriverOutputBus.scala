package li.cil.oc.integration.gregtech

import gregtech.api.interfaces.tileentity.IGregTechTileEntity
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus
import li.cil.oc.api.driver.{NamedBlock, SidedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.vanilla.ConverterItemStack
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection


object DriverOutputBus extends SidedBlock {

  final val ComponentBus = "outputbus"

  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean =
    metaTileEntity(world, x, y, z) match {
      case _: MTEHatchOutputBus => true
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment = {
    val mte = metaTileEntity(world, x, y, z)
    if (mte.isInstanceOf[MTEHatchOutputBus]) new BusEnvironment(mte.asInstanceOf[MTEHatchOutputBus])
    else null
  }

  private def metaTileEntity(world: World, x: Int, y: Int, z: Int): AnyRef = world.getTileEntity(x, y, z) match {
    case tile: IGregTechTileEntity => tile.getMetaTileEntity
    case _ => null
  }

  def parseItemStack(table: java.util.Map[_, _]): ItemStack =
    if (table == null) null else ConverterItemStack.parse(table)

  final class BusEnvironment(tile: MTEHatchOutputBus)
    extends ManagedTileEntityEnvironment[MTEHatchOutputBus](tile, ComponentBus) with NamedBlock {

    override def preferredName(): String = ComponentBus

    override def priority(): Int = 10

    @Callback(doc = "function():table -- Returns the item filter.")
    def getFilter(context: Context, args: Arguments): Array[AnyRef] = result(tile.getFilter)

    @Callback(doc = "function([item:table]):boolean -- Sets the item filter.")
    def setFilter(context: Context, args: Arguments): Array[AnyRef] = {
      tile.setFilter(parseItemStack(args.optTable(0, null)))
      result(true)
    }
  }
}
