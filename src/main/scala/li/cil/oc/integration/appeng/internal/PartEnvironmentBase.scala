package li.cil.oc.integration.appeng.internal

import appeng.api.config.Upgrades
import appeng.api.parts.{IPart, IPartHost}
import appeng.api.storage.StorageName
import appeng.api.storage.data.{IAEItemStack, IAEStack}
import appeng.helpers.IOreFilterable
import appeng.parts.automation.PartSharedItemBus
import appeng.parts.misc.PartStorageBus
import appeng.tile.inventory.IIAEStackInventory
import appeng.util.item.AEItemStack
import li.cil.oc.api.machine.{Arguments, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ResultWrapper.result
import net.minecraftforge.common.util.ForgeDirection

import scala.language.implicitConversions
import scala.reflect.ClassTag

trait PartEnvironmentBase[PartType <: IPart] extends ManagedEnvironment {
  implicit def tag: ClassTag[PartType]

  def host: IPartHost

  def getPart(side: ForgeDirection): PartType = {
    host.getPart(side) match {
      case part: PartType => part
      case _ => throw new IllegalArgumentException("no matching part")
    }
  }
}

object PartEnvironmentBase {
  implicit class OreFilterOps[PartType <: IPart](val env: PartEnvironmentBase[PartType]) extends AnyVal {
    def getPartOreFilter(context: Context, args: Arguments)(implicit ev: PartType <:< IOreFilterable): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val part = env.getPart(side)
      result(part.getFilter)
    }

    def setPartOreFilter(context: Context, args: Arguments)(implicit ev: PartType <:< IOreFilterable): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val filter = args.checkString(1)
      val part = env.getPart(side)
      part.setFilter(filter)
      result(true)
    }
  }

  implicit class ConfigOps[PartType <: IPart](val env: PartEnvironmentBase[PartType]) extends AnyVal {
    private def getConfigAndValidate(part: PartType, slot: Int)(implicit ev: PartType <:< IIAEStackInventory) = {
      val config = part.getAEInventoryByName(StorageName.CONFIG)
      if (slot < 0 || slot >= config.getSizeInventory) {
        throw new IllegalArgumentException("invalid slot")
      }
      config
    }

    def getPartConfigInternal(part: PartType, slot: Int)(implicit ev: PartType <:< IIAEStackInventory): IAEStack[_] = {
      val config = getConfigAndValidate(part, slot)
      config.getAEStackInSlot(slot)
    }

    // function(side:number[, slot:number]):table
    def getPartConfig(context: Context, args: Arguments)(implicit ev: PartType <:< IIAEStackInventory): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val part = env.getPart(side)
      result(getPartConfigInternal(part, args.optInteger(1, 0)))
    }

    // NOTE: Setting a config to null won't sync to the client properly.
    // Because updateVirtualSlots currently skips null entries during init, so the client might still show an item that was actually cleared on the server.
    def setPartConfigInternal(part: PartType, slot: Int, stack: IAEStack[_])(implicit ev: PartType <:< IIAEStackInventory): Unit = {
      val config = getConfigAndValidate(part, slot)
      config.putAEStackInSlot(slot, stack)
    }

    def setPartConfig[T <: IAEStack[T] : ClassTag](context: Context, args: Arguments)(implicit ev: PartType <:< IIAEStackInventory): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val part = env.getPart(side)
      val (slot, offset) = if (args.isInteger(1)) (args.checkInteger(1), 2) else (0, 1)
      val stack: T = if (args.count() <= offset) null.asInstanceOf[T]
      else AEStackFactory.parse[T](args.checkTable(offset))
      setPartConfigInternal(part, slot, stack)
      result(true)
    }
  }
}

trait PartSharedItemBusBase[PartType <: PartSharedItemBus[_]] extends PartEnvironmentBase[PartType] {
  implicit def tag: ClassTag[PartType]

  def getSlotSize(part: PartType): Int =
    Math.min(1 + part.getInstalledUpgrades(Upgrades.CAPACITY) * 4, part.getAEInventoryByName(StorageName.CONFIG).getSizeInventory)

  def getSlotSize(context: Context, args: Arguments): Array[AnyRef] = {
    val side = args.checkSideAny(0)
    val part = getPart(side)
    result(getSlotSize(part))
  }
}

object PartItemConfigurablePart {
  implicit class ConfigOps[PartType <: IPart](val env: PartEnvironmentBase[PartType]) {
    def setPartConfig[T <: IAEStack[T] : ClassTag](context: Context, args: Arguments)(implicit ev: PartType <:< IIAEStackInventory): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val part = env.getPart(side)
      val (slot, offset) = if (args.isInteger(1)) (args.checkInteger(1), 2) else (0, 1)
      val stack =
        if (args.isTable(offset)) AEStackFactory.parse[IAEItemStack](args.checkTable(offset))
        else AEItemStack.create(DatabaseAccess.getStackFromDatabase(env.node, args, offset))
      env.setPartConfigInternal(part, slot, stack)
      result(true)
    }
  }
}

trait PartItemBusBase[PartType <: PartSharedItemBus[_]] extends PartSharedItemBusBase[PartType] {
  implicit def tag: ClassTag[PartType]
}

object PartItemBusBase {
  implicit def ConfigOps[PartType <: PartSharedItemBus[_]](env: PartItemBusBase[PartType]): PartItemConfigurablePart.ConfigOps[PartType] = new PartItemConfigurablePart.ConfigOps[PartType](env)
}

trait PartStorageBusBase[PartType <: PartStorageBus] extends PartEnvironmentBase[PartType] {
  implicit def tag: ClassTag[PartType]

  def getSlotSize(part: PartType): Int =
    Math.min(18 + part.getInstalledUpgrades(Upgrades.CAPACITY) * 9, part.getAEInventoryByName(StorageName.CONFIG).getSizeInventory)

  def getSlotSize(context: Context, args: Arguments): Array[AnyRef] = {
    val side = args.checkSideAny(0)
    val part = getPart(side)
    result(getSlotSize(part))
  }
}

trait PartItemStorageBusBusBase[PartType <: PartStorageBus] extends PartStorageBusBase[PartType] {
  implicit def tag: ClassTag[PartType]
}

object PartItemStorageBusBusBase {
  implicit def ConfigOps[PartType <: PartStorageBus](env: PartItemStorageBusBusBase[PartType]): PartItemConfigurablePart.ConfigOps[PartType] = new PartItemConfigurablePart.ConfigOps[PartType](env)
}