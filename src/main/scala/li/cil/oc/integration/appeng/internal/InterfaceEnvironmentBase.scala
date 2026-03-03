package li.cil.oc.integration.appeng.internal

import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.parts.IPart
import li.cil.oc.api.machine.{Arguments, Context}
import li.cil.oc.api.network.{Environment, Node}
import li.cil.oc.integration.vanilla.ConverterItemStack
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

trait InterfaceEnvironmentBase extends Environment {
  protected def getConfigInventory(inv: ISegmentedInventory): IInventory =
    inv.getInventoryByName("config")

  private def getConfigValue(node: Node, args: Arguments, offset: Int): ItemStack =
    if (args.isTable(offset))
      ConverterItemStack.parse(args.checkTable(offset))
    else
      DatabaseAccess.getStackFromDatabase(node, args, offset)

  protected def getConfigInventory(context: Context, args: Arguments): IInventory

  protected def offset: Int = 0

  def getConfig(context: Context, args: Arguments): Array[AnyRef] = {
    val inv = getConfigInventory(context, args)
    val slot = args.optInteger(offset, 0)
    result(inv.getStackInSlot(slot))
  }

  def setConfig(context: Context, args: Arguments): Array[AnyRef] = {
    val inv = getConfigInventory(context, args)
    val (slot, valOffset) = if (args.isInteger(offset)) (args.checkInteger(offset), offset + 1) else (0, offset)
    val stack = getConfigValue(node, args, valOffset)
    inv.setInventorySlotContents(slot, stack)
    result(true)
  }
}

trait PartInterfaceEnvironment[PartType <: IPart] extends InterfaceEnvironmentBase with PartEnvironmentBase[PartType] {
  override def offset: Int = 1

  override protected def getConfigInventory(context: Context, args: Arguments): IInventory = {
    val side = args.checkSideAny(0)
    getPart(side) match {
      case inv: ISegmentedInventory => getConfigInventory(inv)
      case _ => throw new IllegalArgumentException("no matching part")
    }
  }
}

trait BlockInterfaceEnvironment extends InterfaceEnvironmentBase {
  def tile: ISegmentedInventory

  override protected def getConfigInventory(context: Context, args: Arguments): IInventory = getConfigInventory(tile)
}