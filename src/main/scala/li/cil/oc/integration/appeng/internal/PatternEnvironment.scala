package li.cil.oc.integration.appeng.internal

import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.parts.IPart
import appeng.api.storage.data.IAEStack
import appeng.tile.misc.TileInterface
import appeng.util.Platform
import appeng.util.item.AEItemStack
import li.cil.oc.api.machine.{Arguments, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT

trait PatternEnvironment extends ManagedEnvironment {
  def getPatternInventory(context: Context, args: Arguments): IInventory

  protected def offset: Int = 0

  private def getPatternNBT(context: Context, args: Arguments, tag: String) = {
    val inv = getPatternInventory(context, args)
    val slot = args.checkSlot(inv, offset)
    val pattern = inv.getStackInSlot(args.checkSlot(inv, offset))
    val index = args.checkInteger(offset + 1) - 1
    if (index < 0 || index > 511)
      throw new IllegalArgumentException("Invalid index!")
    val encodedValue = pattern.getTagCompound
    if (encodedValue == null)
      throw new IllegalArgumentException("No pattern here!")
    val nbt = encodedValue.getTagList(tag, NBT.TAG_COMPOUND)
    (inv, index, slot, pattern, encodedValue, nbt)
  }

  // function(slot:number, index:number, database:address, entry:number):boolean
  def storeInterfacePattern(context: Context, args: Arguments, tag: String): Array[AnyRef] = {
    val (inv, index, slot, pattern, encodedValue, nbt) = getPatternNBT(context, args, tag)
    val stackNBT = nbt.getCompoundTagAt(index)
    val stack = ItemStack.loadItemStackFromNBT(stackNBT)
    stack.stackSize = 1;
    DatabaseAccess.withDatabase(node, args.checkString(offset + 2), database => {
      val slot = args.optSlot(database.data, offset + 3, 0)
      database.setStackInSlot(slot, stack)
      result(true)
    })
  }

  // function(slot:number, index:number[, database:address, entry:number, size:number]):boolean
  // function(slot:number, index:number[, detail:table, type:string]):boolean
  def setPatternSlot(context: Context, args: Arguments, tag: String): Array[AnyRef] = {
    val (inv, index, slot, pattern, encodedValue, inTag) = getPatternNBT(context, args, tag)
    val stack: IAEStack[_] = if (args.count() <= offset + 2) null
    else if (args.isTable(offset + 2)) {
      val table = args.checkTable(offset + 2)
      val tp = args.checkString(offset + 3)
      AEStackFactory.parse(tp, table)
    }
    else {
      AEItemStack.create(DatabaseAccess.getStackFromDatabase(node, args, offset + 2))
    }

    while (inTag.tagCount() <= index)
      inTag.appendTag(new NBTTagCompound())
    if (stack != null) {
      val nbt = new NBTTagCompound()
      Platform.writeStackNBT(stack, nbt)
      inTag.func_150304_a(index, nbt)
    }
    else
      inTag.removeTag(index)
    encodedValue.setTag(tag, inTag)
    pattern.setTagCompound(encodedValue)
    inv.setInventorySlotContents(slot, null)
    inv.setInventorySlotContents(slot, pattern)
    result(true)
  }
}

trait BlockPatternEnvironment extends PatternEnvironment {
  def tile: TileInterface

  override def getPatternInventory(context: Context, args: Arguments): IInventory = {
    tile.getInventoryByName("patterns")
  }
}

trait PartPatternEnvironment[PartType <: ISegmentedInventory with IPart] extends PatternEnvironment with PartEnvironmentBase[PartType] {
  override def offset: Int = 1

  override def getPatternInventory(context: Context, args: Arguments): IInventory = {
    val side = args.checkSideAny(0)
    getPart(side).getInventoryByName("patterns")
  }
}