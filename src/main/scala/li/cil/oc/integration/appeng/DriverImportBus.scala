package li.cil.oc.integration.appeng

import appeng.api.config._
import appeng.api.parts.IPartHost
import appeng.api.storage.StorageName
import appeng.api.storage.data.IAEItemStack
import appeng.core.settings.TickRates
import appeng.me.GridAccessException
import appeng.parts.automation.PartImportBus
import appeng.util.Platform
import appeng.util.item.AEItemStack
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.internal.PartItemBusBase
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper._
import li.cil.oc.util.{BlockPosition, InventoryUtils}
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import scala.reflect.ClassTag

object DriverImportBus extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(obj => {
        obj != null
      }).exists(_.isInstanceOf[PartImportBus])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val host: IPartHost)(implicit val tag: ClassTag[PartImportBus]) extends ManagedTileEntityEnvironment[IPartHost](host, "me_importbus") with NamedBlock with PartItemBusBase[PartImportBus] {
    override def preferredName = "me_importbus"

    override def priority = 1

    @Callback(doc = "function(side:number[, slot:number]):boolean -- Get the configuration of the import bus pointing in the specified direction.")
    def getImportConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.getPartConfig(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number]):boolean OR function(side:number[, slot:number][, detail:table]):boolean -- Configure the import bus pointing in the specified direction to import item stacks matching the specified descriptor.")
    def setImportConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.setPartConfig[IAEItemStack](context, args)

    @Callback(doc = "function(side:number):number -- Get the number of valid slots in this import bus.")
    def getImportSlotSize(context: Context, args: Arguments): Array[AnyRef] = getSlotSize(context, args)

    @Callback(doc = "function(side:number):boolean -- Get the ore filter of the import bus pointing in the specified direction.")
    def getImportOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.getPartOreFilter(context, args)

    @Callback(doc = "function(side:number, filter: String):boolean -- Set the ore filter of the import bus pointing in the specified direction.")
    def setImportOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.setPartOreFilter(context, args)

    @Callback(doc = "function(side:number, slot:number):boolean, number -- Make the import bus facing the specified direction perform a single import operation from the specified slot.")
    def importFromSlot(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val part = getPart(side)
      InventoryUtils.inventoryAt(BlockPosition(host.getLocation).offset(side)) match {
        case Some(inventory) =>
          try {
            val targetSlot = args.checkSlot(inventory, 1)
            val stack = inventory.getStackInSlot(targetSlot)
            if (stack == null || stack.stackSize == 0) {
              result(false)
            }
            else {
              var isMatch = false
              var configured = false
              val gridInv = getMonitor[IAEItemStack](part)
              if (part.getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) {
                val supportFz = supportFuzzy(part) && part.getInstalledUpgrades(Upgrades.FUZZY) > 0
                val fuzzyMode = part.getConfigManager.getSetting(Settings.FUZZY_MODE).asInstanceOf[FuzzyMode]
                val config = part.getAEInventoryByName(StorageName.CONFIG)
                var slot = 0
                while (!isMatch && slot < getSlotSize(part)) {
                  val filter = config.getAEStackInSlot(slot).asInstanceOf[IAEItemStack]
                  if (filter != null) {
                    configured = true
                    if (supportFz && Platform.isSameItemFuzzy(stack, filter.getItemStack, fuzzyMode))
                      isMatch = true
                    else if (!supportFz && Platform.isSameItemPrecise(stack, filter.getItemStack))
                      isMatch = true
                  }
                  slot += 1
                }
                if (!configured) isMatch = true
              } else if (supportOreDict(part)) {
                val filterPredicate = getOreFilterPredicate(part)
                if (filterPredicate != null) {
                  isMatch = filterPredicate.test(AEItemStack.create(stack))
                }
              }
              if (!isMatch) result(false)
              else {
                var didSomething = false
                var insert = 0
                val energy = part.getProxy.getEnergy
                InventoryUtils.extractFromInventorySlot(extracted => {
                  if (extracted != null) {
                    val before = extracted.stackSize
                    val source = getMySrc(part)
                    val failed = Platform.poweredInsert(energy, gridInv, AEItemStack.create(extracted), source)
                    extracted.stackSize = if (failed == null) 0 else failed.getStackSize.toInt
                    if (before != extracted.stackSize) {
                      insert = before - extracted.stackSize
                      didSomething = true
                    }
                  }
                }, inventory, side.getOpposite, targetSlot, part.calculateAmountToSend())
                if (didSomething) {
                  context.pause((TickRates.ImportBus.getMin - 1) * 0.05)
                }
                result(didSomething, insert)
              }
            }
          }
          catch {
            case _: GridAccessException => result(null, "grid access exception")
          }
        case _ => result(null, "no inventory")
      }
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (AEUtil.isImportBus(stack))
        classOf[Environment]
      else null
  }

}
