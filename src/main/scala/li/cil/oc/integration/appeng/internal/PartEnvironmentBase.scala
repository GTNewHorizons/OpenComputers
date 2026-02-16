package li.cil.oc.integration.appeng.internal

import appeng.api.config.Upgrades
import appeng.api.parts.IPartHost
import appeng.api.storage.StorageName
import appeng.api.storage.data.{IAEItemStack, IAEStack}
import appeng.parts.automation.PartSharedItemBus
import appeng.tile.inventory.IIAEStackInventory
import appeng.util.item.AEItemStack
import li.cil.oc.api.machine.{Arguments, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments._
import net.minecraftforge.common.util.ForgeDirection

import scala.reflect.ClassTag

trait PartEnvironmentBase[PartType <: IIAEStackInventory] extends ManagedEnvironment {
  def host: IPartHost

  def getPart(side: ForgeDirection): PartType = {
    host.getPart(side) match {
      case part: PartType => part
      case _ => throw new IllegalArgumentException("no matching part")
    }
  }

  private def getConfigAndValidate(part: PartType, slot: Int) = {
    val config = part.getAEInventoryByName(StorageName.CONFIG)
    if (slot < 0 || slot >= config.getSizeInventory) {
      throw new IllegalArgumentException("invalid slot")
    }
    config
  }

  def getPartConfig(part: PartType, slot: Int): IAEStack[_] = {
    val config = getConfigAndValidate(part, slot)
    config.getAEStackInSlot(slot)
  }

  // function(side:number[, slot:number]):table
  def getPartConfig(context: Context, args: Arguments): IAEStack[_] = {
    val side = args.checkSideAny(0)
    val part = getPart(side)
    getPartConfig(part, args.optInteger(1, 0))
  }

  def setPartConfig(part: PartType, slot: Int, stack: IAEStack[_]): Unit = {
    val config = getConfigAndValidate(part, slot)
    config.putAEStackInSlot(slot, stack)
  }

  def setPartConfig[T <: IAEStack[T]: ClassTag](context: Context, args: Arguments): Unit = {
    val side = args.checkSideAny(0)
    val part = getPart(side)
    val (slot, offset) = if (args.isInteger(1)) (args.checkInteger(1), 2) else (0, 1)
    val stack = AEStackFactory.parse[T](args.checkTable(offset))
    setPartConfig(part, slot, stack)
  }
}

trait PartSharedItemBusBase[PartType <: PartSharedItemBus[_]] extends PartEnvironmentBase[PartType] {
  def getSlotSize(part: PartType): Int =
    Math.min(1 + part.getInstalledUpgrades(Upgrades.CAPACITY) * 4, part.getAEInventoryByName(StorageName.CONFIG).getSizeInventory)

  def getSlotSize(context: Context, args: Arguments): Int = {
    val side = args.checkSideAny(0)
    val part = getPart(side)
    getSlotSize(part)
  }
}

trait PartItemBusBase[PartType <: PartSharedItemBus[_]] extends PartSharedItemBusBase[PartType]{
  // function(side:number[, slot:number][, database:address, entry:number[, size:number]]):boolean
  override def setPartConfig[T <: IAEStack[T]: ClassTag](context: Context, args: Arguments): Unit = {
    val side = args.checkSideAny(0)
    val part = getPart(side)
    val (slot, offset) = if (args.isInteger(1)) (args.checkInteger(1), 2) else (0, 1)
    val stack =
      if (args.isTable(offset)) AEStackFactory.parse[IAEItemStack](args.checkTable(offset))
      else AEItemStack.create(DatabaseAccess.getStackFromDatabase(node, args, offset))
    setPartConfig(part, slot, stack)
  }
}