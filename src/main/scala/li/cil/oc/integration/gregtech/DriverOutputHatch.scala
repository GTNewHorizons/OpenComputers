package li.cil.oc.integration.gregtech

import gregtech.api.interfaces.tileentity.IGregTechTileEntity
import gregtech.api.metatileentity.implementations.{MTEHatchOutput, MTEHatchOutputBus}
import li.cil.oc.api.driver.{NamedBlock, SidedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.vanilla.{ConverterFluidStack, ConverterItemStack}
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids.{Fluid, FluidStack}


object DriverOutputHatch extends SidedBlock {

  final val ComponentBus = "outputbus"
  final val ComponentHatch = "outputhatch"

  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean =
    metaTileEntity(world, x, y, z) match {
      case _: MTEHatchOutputBus | _: MTEHatchOutput => true
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment = {
    val mte = metaTileEntity(world, x, y, z)
    if (mte.isInstanceOf[MTEHatchOutputBus]) new BusEnvironment(mte.asInstanceOf[MTEHatchOutputBus])
    else if (mte.isInstanceOf[MTEHatchOutput]) new HatchEnvironment(mte.asInstanceOf[MTEHatchOutput])
    else null
  }

  private def metaTileEntity(world: World, x: Int, y: Int, z: Int): AnyRef = world.getTileEntity(x, y, z) match {
    case tile: IGregTechTileEntity => tile.getMetaTileEntity
    case _ => null
  }

  def parseItemStack(table: java.util.Map[_, _]): ItemStack =
    if (table == null) null else ConverterItemStack.parse(table)

  def parseFluidStack(table: java.util.Map[_, _]): FluidStack =
    if (table == null) null
    else {
      val stack = ConverterFluidStack.parse(table)
      val fluid: Fluid = if (stack == null) null else stack.getFluid
      if (fluid == null) null else new FluidStack(fluid, 1)
    }

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

  final class HatchEnvironment(tile: MTEHatchOutput)
    extends ManagedTileEntityEnvironment[MTEHatchOutput](tile, ComponentHatch) with NamedBlock {

    override def preferredName(): String = ComponentHatch

    override def priority(): Int = 10

    @Callback(doc = "function():table -- Returns the fluid filter.")
    def getFilter(context: Context, args: Arguments): Array[AnyRef] = result(tile.getFilter)

    @Callback(doc = "function([fluid:table]):boolean -- Sets the fluid filter.")
    def setFilter(context: Context, args: Arguments): Array[AnyRef] =
      result(tile.setFilter(parseFluidStack(args.optTable(0, null))))
  }
}
