package li.cil.oc.integration.gregtech

import appeng.api.storage.data.{IAEFluidStack, IAEItemStack, IAEStack}
import gregtech.api.interfaces.tileentity.IGregTechTileEntity
import gregtech.common.tileentities.machines.{MTEHatchInputBusME, MTEHatchInputME}
import li.cil.oc.api.driver.{NamedBlock, SidedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids.FluidStack

import scala.reflect.ClassTag


object DriverMEInputHatch extends SidedBlock {

  final val ComponentBus = "me_inputbus"
  final val ComponentHatch = "me_inputhatch"

  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean =
    metaTileEntity(world, x, y, z) match {
      case _: MTEHatchInputBusME | _: MTEHatchInputME => true
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment = {
    val mte = metaTileEntity(world, x, y, z)
    if (mte.isInstanceOf[MTEHatchInputBusME]) new BusEnvironment(mte.asInstanceOf[MTEHatchInputBusME])
    else if (mte.isInstanceOf[MTEHatchInputME]) new HatchEnvironment(mte.asInstanceOf[MTEHatchInputME])
    else null
  }

  private def metaTileEntity(world: World, x: Int, y: Int, z: Int): AnyRef = world.getTileEntity(x, y, z) match {
    case tile: IGregTechTileEntity => tile.getMetaTileEntity
    case _ => null
  }

  def resolveSlot(args: Arguments, size: Int): Int = {
    val slot = args.checkInteger(0)
    val index = slot - 1
    if (index < 0 || index >= size) {
      throw new IllegalArgumentException("invalid slot")
    }
    index
  }

  def detailTable(args: Arguments): java.util.Map[_, _] = {
    if (args.count() <= 1) null
    else if (args.checkAny(1) == null) null
    else args.checkTable(1)
  }

  def checkPositiveInteger(args: Arguments, index: Int): Int = {
    val value = args.checkInteger(index)
    if (value <= 0) throw new IllegalArgumentException("Bad argument#" + index + ": " + "expected positive integer, got " + value)
    value
  }

  private def parseDetail[T <: IAEStack[T]](table: java.util.Map[_, _])(implicit tag: ClassTag[T]): T =
    if (table == null) null.asInstanceOf[T] else AEStackFactory.parse[T](table)

  abstract class EnvironmentBase[MTE](tile: MTE, name: String) extends ManagedTileEntityEnvironment[MTE](tile, name) with NamedBlock {

    override def priority(): Int = 10

    protected def autoPullAvailable: Boolean

    protected def isAutoPull: Boolean

    protected def setAutoPull(enabled: Boolean): Unit

    protected def getMinAutoPullAmount: Int

    protected def setMinAutoPullAmount(min: Int): Unit

    protected def getAutoPullRefreshTime: Int

    protected def setAutoPullRefreshTime(ticks: Int): Unit

    protected def connectsToAllSides: Boolean

    protected def setConnectsToAllSides(connects: Boolean): Unit
  }

  final class BusEnvironment(tile: MTEHatchInputBusME) extends EnvironmentBase[MTEHatchInputBusME](tile, ComponentBus) {

    override def preferredName(): String = ComponentBus

    override protected def autoPullAvailable: Boolean = tile.autoPullAvailable

    override protected def isAutoPull: Boolean = tile.isAutoPullItemList

    override protected def setAutoPull(enabled: Boolean): Unit = tile.setAutoPullItemList(enabled)

    override protected def getMinAutoPullAmount: Int = tile.getMinAutoPullStackSize

    override protected def setMinAutoPullAmount(min: Int): Unit = tile.setMinAutoPullStackSize(min)

    override protected def getAutoPullRefreshTime: Int = tile.getAutoPullRefreshTime

    override protected def setAutoPullRefreshTime(ticks: Int): Unit = tile.setAutoPullRefreshTime(ticks)

    override protected def connectsToAllSides: Boolean = tile.connectsToAllSides()

    override protected def setConnectsToAllSides(connects: Boolean): Unit = tile.setConnectsToAllSides(connects)

    @Callback(doc = "function():number -- Returns the number of slots this input bus can be configured for.")
    def getSlotSize(context: Context, args: Arguments): Array[AnyRef] = result(MTEHatchInputBusME.SLOT_COUNT)

    @Callback(doc = "function(slot:number):table -- Returns the item configured for the specified slot.")
    def getConfiguration(context: Context, args: Arguments): Array[AnyRef] =
      result(tile.getSlotConfig(resolveSlot(args, MTEHatchInputBusME.SLOT_COUNT)))

    @Callback(doc = "function(slot:number[, detail:table]):boolean -- Configures the ME input bus.")
    def setConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val slot = resolveSlot(args, MTEHatchInputBusME.SLOT_COUNT)
      val stack = parseDetail[IAEItemStack](detailTable(args))
      tile.setSlotConfigAndUpdate(slot, if (stack == null) null else stack.getItemStack.copy())
      result(true)
    }

    @Callback(doc = "function():boolean -- Returns whether this is the advanced variant of the input bus.")
    def isAdvanced(context: Context, args: Arguments): Array[AnyRef] = result(autoPullAvailable)

    @Callback(doc = "function():boolean -- Returns whether this input bus enables the auto-pull feature.")
    def getAutoPull(context: Context, args: Arguments): Array[AnyRef] = result(autoPullAvailable && isAutoPull)

    @Callback(doc = "function(enabled:boolean):boolean -- Sets whether this input bus enables the auto-pull feature.")
    def setAutoPull(context: Context, args: Arguments): Array[AnyRef] = {
      val enabled = args.checkBoolean(0)
      if (autoPullAvailable) setAutoPull(enabled)
      result(true)
    }

    @Callback(doc = "function():number -- Returns the minimum amount of this input bus.")
    def getMinAmount(context: Context, args: Arguments): Array[AnyRef] = result(getMinAutoPullAmount)

    @Callback(doc = "function(min:number):boolean -- Sets the minimum amount of this input bus.")
    def setMinAmount(context: Context, args: Arguments): Array[AnyRef] = {
      setMinAutoPullAmount(checkPositiveInteger(args, 0))
      result(true)
    }

    @Callback(doc = "function():number -- Returns the slot refresh time of this input bus.")
    def getRefreshTime(context: Context, args: Arguments): Array[AnyRef] = result(getAutoPullRefreshTime)

    @Callback(doc = "function(ticks:number):boolean -- Sets the slot refresh time of this input bus.")
    def setRefreshTime(context: Context, args: Arguments): Array[AnyRef] = {
      setAutoPullRefreshTime(checkPositiveInteger(args, 0))
      result(true)
    }

    @Callback(doc = "function():boolean -- Returns whether ME channels can connect to any side.")
    def getAdditionalConnection(context: Context, args: Arguments): Array[AnyRef] = result(connectsToAllSides)

    @Callback(doc = "function(enabled:boolean):boolean -- Sets whether ME channels can connect to any side.")
    def setAdditionalConnection(context: Context, args: Arguments): Array[AnyRef] = {
      setConnectsToAllSides(args.checkBoolean(0))
      result(true)
    }
  }

  final class HatchEnvironment(tile: MTEHatchInputME) extends EnvironmentBase[MTEHatchInputME](tile, ComponentHatch) {

    override def preferredName(): String = ComponentHatch

    override protected def autoPullAvailable: Boolean = tile.autoPullAvailable

    override protected def isAutoPull: Boolean = tile.isAutoPullFluidList

    override protected def setAutoPull(enabled: Boolean): Unit = tile.setAutoPullFluidList(enabled)

    override protected def getMinAutoPullAmount: Int = tile.getMinAutoPullAmount

    override protected def setMinAutoPullAmount(min: Int): Unit = tile.setMinAutoPullAmount(min)

    override protected def getAutoPullRefreshTime: Int = tile.getAutoPullRefreshTime

    override protected def setAutoPullRefreshTime(ticks: Int): Unit = tile.setAutoPullRefreshTime(ticks)

    override protected def connectsToAllSides: Boolean = tile.connectsToAllSides()

    override protected def setConnectsToAllSides(connects: Boolean): Unit = tile.setConnectsToAllSides(connects)

    @Callback(doc = "function():number -- Returns the number of slots this input hatch can be configured for.")
    def getSlotSize(context: Context, args: Arguments): Array[AnyRef] = result(MTEHatchInputME.SLOT_COUNT)

    @Callback(doc = "function(slot:number):table -- Returns the fluid configured for the specified slot.")
    def getConfiguration(context: Context, args: Arguments): Array[AnyRef] =
      result(tile.getSlotConfig(resolveSlot(args, MTEHatchInputME.SLOT_COUNT)))

    @Callback(doc = "function(slot:number[, detail:table]):boolean -- Configures the ME input hatch.")
    def setConfiguration(context: Context, args: Arguments): Array[AnyRef] = {
      val slot = resolveSlot(args, MTEHatchInputME.SLOT_COUNT)
      val stack = parseDetail[IAEFluidStack](detailTable(args))
      val fluid = if (stack == null) null else stack.getFluidStack
      tile.setSlotConfigAndUpdate(slot, if (fluid == null) null else new FluidStack(fluid.getFluid, 1))
      result(true)
    }

    @Callback(doc = "function():boolean -- Returns whether this is the advanced variant of the input hatch.")
    def isAdvanced(context: Context, args: Arguments): Array[AnyRef] = result(autoPullAvailable)

    @Callback(doc = "function():boolean -- Returns whether this input hatch enables the auto-pull feature.")
    def getAutoPull(context: Context, args: Arguments): Array[AnyRef] = result(autoPullAvailable && isAutoPull)

    @Callback(doc = "function(enabled:boolean):boolean -- Sets whether this input hatch enables the auto-pull feature.")
    def setAutoPull(context: Context, args: Arguments): Array[AnyRef] = {
      val enabled = args.checkBoolean(0)
      if (autoPullAvailable) setAutoPull(enabled)
      result(true)
    }

    @Callback(doc = "function():number -- Returns the minimum amount of this input hatch.")
    def getMinAmount(context: Context, args: Arguments): Array[AnyRef] = result(getMinAutoPullAmount)

    @Callback(doc = "function(min:number):boolean -- Sets the minimum amount of this input hatch.")
    def setMinAmount(context: Context, args: Arguments): Array[AnyRef] = {
      setMinAutoPullAmount(checkPositiveInteger(args, 0))
      result(true)
    }

    @Callback(doc = "function():number -- Returns the slot refresh time of this input hatch.")
    def getRefreshTime(context: Context, args: Arguments): Array[AnyRef] = result(getAutoPullRefreshTime)

    @Callback(doc = "function(ticks:number):boolean -- Sets the slot refresh time of this input hatch.")
    def setRefreshTime(context: Context, args: Arguments): Array[AnyRef] = {
      setAutoPullRefreshTime(checkPositiveInteger(args, 0))
      result(true)
    }

    @Callback(doc = "function():boolean -- Returns whether ME channels can connect to any side.")
    def getAdditionalConnection(context: Context, args: Arguments): Array[AnyRef] = result(connectsToAllSides)

    @Callback(doc = "function(enabled:boolean):boolean -- Sets whether ME channels can connect to any side.")
    def setAdditionalConnection(context: Context, args: Arguments): Array[AnyRef] = {
      setConnectsToAllSides(args.checkBoolean(0))
      result(true)
    }
  }
}
