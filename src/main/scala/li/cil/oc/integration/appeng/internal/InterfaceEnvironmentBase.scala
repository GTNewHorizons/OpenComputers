package li.cil.oc.integration.appeng.internal

import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.parts.{IPart, IPartHost}
import li.cil.oc.api.machine.{Arguments, Context}
import li.cil.oc.api.network.{ManagedEnvironment, Node}
import li.cil.oc.integration.vanilla.ConverterItemStack
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

trait InterfaceEnvironmentBase[V] extends ManagedEnvironment {
  def getConfig(context: Context, args: Arguments): Array[AnyRef]

  def setConfig(context: Context, args: Arguments): Array[AnyRef]

  protected def getConfigValue(args: Arguments, offset: Int): V
}

trait PartInterfaceEnvironmentBase[V] extends InterfaceEnvironmentBase[V] {
  def host: IPartHost

  override def getConfig(context: Context, args: Arguments): Array[AnyRef] = {
    val side = args.checkSideAny(0)
    val slot = args.optInteger(1, 0)
    result(readFromPart(host.getPart(side), slot))
  }

  protected def readFromPart(part: IPart, slot: Int): AnyRef

  override def setConfig(context: Context, args: Arguments): Array[AnyRef] = {
    val side = args.checkSideAny(0)
    val (slot, offset) = if (args.isInteger(1)) (args.checkInteger(1), 2) else (0, 1)
    val stack = getConfigValue(args, offset)
    result(setToPart(host.getPart(side), slot, stack))
  }

  protected def setToPart(part: IPart, slot: Int, stack: V): AnyRef
}

trait BlockInterfaceEnvironmentBase[V] extends InterfaceEnvironmentBase[V] {
  def tile: AnyRef

  override def getConfig(context: Context, args: Arguments): Array[AnyRef] = {
    val slot = args.optInteger(0, 0)
    result(readFromTile(slot))
  }

  protected def readFromTile(slot: Int): AnyRef

  override def setConfig(context: Context, args: Arguments): Array[AnyRef] = {
    val (slot, offset) = if (args.isInteger(0)) (args.checkInteger(0), 1) else (0, 0)
    val stack = getConfigValue(args, offset)
    result(setToTile(slot, stack))
  }

  protected def setToTile(slot: Int, stack: V): AnyRef
}

trait AE2InterfaceUtils {
  protected def getConfigInventory(inv: ISegmentedInventory): IInventory =
    inv.getInventoryByName("config")

  protected def getConfigValue(node: Node, args: Arguments, offset: Int): ItemStack =
    if (args.isTable(offset))
      ConverterItemStack.parse(args.checkTable(offset))
    else
      DatabaseAccess.getStackFromDatabase(node, args, offset)
}

trait PartInterfaceEnvironmentAE2 extends PartInterfaceEnvironmentBase[ItemStack] with AE2InterfaceUtils {
  private def getConfigInventory(part: IPart): IInventory = part match {
    case inv: ISegmentedInventory => getConfigInventory(inv: ISegmentedInventory)
    case _ => throw new IllegalArgumentException("no matching part")
  }

  override def readFromPart(part: IPart, slot: Int): AnyRef = {
    getConfigInventory(part).getStackInSlot(slot)
  }

  override def getConfigValue(args: Arguments, offset: Int): ItemStack = getConfigValue(node, args, offset)

  override def setToPart(part: IPart, slot: Int, stack: ItemStack): AnyRef = {
    getConfigInventory(part).setInventorySlotContents(slot, stack)
    result(true)
  }
}

trait BlockInterfaceEnvironmentAE2 extends BlockInterfaceEnvironmentBase[ItemStack] with AE2InterfaceUtils {
  override def tile: ISegmentedInventory

  private def getConfigInventory: IInventory = getConfigInventory(tile)

  override def readFromTile(slot: Int): AnyRef = {
    getConfigInventory.getStackInSlot(slot)
  }

  override def getConfigValue(args: Arguments, offset: Int): ItemStack = getConfigValue(node, args, offset)

  override def setToTile(slot: Int, stack: ItemStack): AnyRef = {
    getConfigInventory.setInventorySlotContents(slot, stack)
    result(true)
  }
}