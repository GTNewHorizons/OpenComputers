package li.cil.oc.integration.appeng

import appeng.api.config._
import appeng.api.parts.IPartHost
import appeng.api.storage.StorageName
import appeng.api.storage.data.IAEItemStack
import appeng.core.settings.TickRates
import appeng.helpers.MultiCraftingTracker
import appeng.me.GridAccessException
import appeng.me.cache.NetworkMonitor
import appeng.parts.automation.{PartBaseExportBus, PartExportBus}
import appeng.util.{IterationCounter, Platform}
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.internal.PartItemBusBase
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ResultWrapper._
import li.cil.oc.util.{BlockPosition, InventoryUtils, ReflectionUtils}
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import java.util
import scala.reflect.ClassTag

object DriverExportBus extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).filter(obj => {
        obj != null
      }).exists(_.isInstanceOf[PartExportBus])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = new Environment(world.getTileEntity(x, y, z).asInstanceOf[IPartHost])

  final class Environment(val host: IPartHost)(implicit val tag: ClassTag[PartExportBus]) extends ManagedTileEntityEnvironment[IPartHost](host, "me_exportbus") with NamedBlock with PartItemBusBase[PartExportBus] {
    override def preferredName = "me_exportbus"

    override def priority = 2

    @Callback(doc = "function(side:number, [ slot:number]):boolean -- Get the configuration of the export bus pointing in the specified direction.")
    def getExportConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.getPartConfig(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number):boolean OR function(side:number[, slot:number][, detail: table):boolean -- Configure the export bus pointing in the specified direction to export item stacks matching the specified descriptor.")
    def setExportConfiguration(context: Context, args: Arguments): Array[AnyRef] = this.setPartConfig[IAEItemStack](context, args)

    @Callback(doc = "function(side:number):number -- Get the number of valid slots in this export bus.")
    def getExportSlotSize(context: Context, args: Arguments): Array[AnyRef] = getSlotSize(context, args)

    @Callback(doc = "function(side:number):boolean -- Get the ore filter of the export bus pointing in the specified direction.")
    def getExportOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.getPartOreFilter(context, args)

    @Callback(doc = "function(side:number, filter: String):boolean -- Set the ore filter of the export bus pointing in the specified direction.")
    def setExportOreFilter(context: Context, args: Arguments): Array[AnyRef] = this.setPartOreFilter(context, args)

    private lazy val craftingTrackerGetterHandle = ReflectionUtils.getFieldGetterHandle(classOf[PartBaseExportBus[_]], "craftingTracker", allowSuper = false)

    @Callback(doc = "function(side:number, slot:number):boolean, number -- Make the export bus facing the specified direction perform a single export operation into the specified slot.")
    def exportIntoSlot(context: Context, args: Arguments): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val export = getPart(side)
      InventoryUtils.inventoryAt(BlockPosition(host.getLocation).offset(side)) match {
        case Some(inventory) =>
          try {
            val targetSlot = args.checkSlot(inventory, 1)
            val config = export.getAEInventoryByName(StorageName.CONFIG)
            val gridInv = getMonitor[IAEItemStack](export)
            var count = export.calculateAmountToSend()
            val origin = count
            val mySrc = getMySrc(export)
            var didSomething = false
            val energy = export.getProxy.getEnergy
            val oppositeSide = Option(side.getOpposite)

            def pushItemToTargetSlot(stack: IAEItemStack, simulate: Boolean = false): Boolean = {
              val is = stack.getItemStack
              is.stackSize = count
              if (!InventoryUtils.insertIntoInventorySlot(is, inventory, oppositeSide, targetSlot, simulate = true))
                false
              else if (simulate)
                true
              else {
                val ais = stack.copy()
                ais.setStackSize(count - is.stackSize)
                val eais = Platform.poweredExtraction(energy, gridInv, ais, mySrc)
                if (eais != null) {
                  val eis = eais.getItemStack
                  count -= eis.stackSize
                  didSomething = true
                  InventoryUtils.insertIntoInventorySlot(eis, inventory, oppositeSide, targetSlot)
                }
                true
              }
            }

            if (export.getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) {
              val supportFz = supportFuzzy(export) && export.getInstalledUpgrades(Upgrades.FUZZY) > 0
              val fuzzyMode = export.getConfigManager.getSetting(Settings.FUZZY_MODE).asInstanceOf[FuzzyMode]
              val cg = export.getProxy.getCrafting
              val craftOnly = export.getConfigManager.getSetting(Settings.CRAFT_ONLY) == YesNo.YES;
              val isCraftingEnabled = export.getInstalledUpgrades(Upgrades.CRAFTING) > 0;
              val craftingTracker = craftingTrackerGetterHandle.invoke(export).asInstanceOf[MultiCraftingTracker]
              var slot = 0

              def tryCrafting(slot: Int, filter: IAEItemStack): Boolean = {
                craftingTracker.handleCrafting(slot, count, filter, export.getTile.getWorldObj, export.getProxy.getGrid, cg, mySrc)
              }

              while (count > 0 && slot < getSlotSize(export)) {
                val filter = config.getAEStackInSlot(slot).asInstanceOf[IAEItemStack]
                if (filter != null) {
                  if (craftOnly) {
                    if (isCraftingEnabled && pushItemToTargetSlot(filter, simulate = true)) {
                      didSomething = tryCrafting(slot, filter) || didSomething
                    }
                  }
                  else {
                    val before = count
                    if (supportFz) {
                      gridInv match {
                        case monitor: NetworkMonitor[IAEItemStack] =>
                          val it = monitor.getHandler.getSortedFuzzyItems(new util.ArrayList, filter, fuzzyMode, IterationCounter.fetchNewId).iterator()
                          while (count > 0 && it.hasNext) {
                            val ais = it.next()
                            if (ais != null) pushItemToTargetSlot(ais)
                          }
                        case _ =>
                      }
                    }
                    else pushItemToTargetSlot(filter)

                    if (count == before && isCraftingEnabled) {
                      didSomething = tryCrafting(slot, filter) || didSomething
                    }
                  }
                }
                slot += 1
              }
            }
            else if (supportOreDict(export)) {
              val filterPredicate = getOreFilterPredicate(export)
              if (filterPredicate != null) {
                val it = gridInv.getStorageList.iterator()
                while (count > 0 && it.hasNext) {
                  val ais = it.next()
                  if (ais != null && filterPredicate.test(ais)) pushItemToTargetSlot(ais)
                }
              }
            }
            if (didSomething) {
              context.pause((TickRates.ExportBus.getMin - 1) * 0.05)
            }
            result(didSomething, origin - count)
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
      if (AEUtil.isExportBus(stack))
        classOf[Environment]
      else null
  }

}
