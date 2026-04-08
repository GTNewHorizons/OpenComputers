package li.cil.oc.integration.appeng

import appeng.api.AEApi
import appeng.api.storage.data.{IAEFluidStack, IAEItemStack}
import appeng.util.item.{AEFluidStackType, AEItemStackType}
import cpw.mods.fml.common.registry.GameRegistry
import li.cil.oc.api.Driver
import li.cil.oc.common.Tier
import li.cil.oc.common.recipe.Recipes.addSubItem
import li.cil.oc.common.tileentity.Print
import li.cil.oc.integration.{ModProxy, Mods}
import li.cil.oc.{Constants, api}

object ModAppEng extends ModProxy {
  override def getMod = Mods.AppliedEnergistics2

  override def initialize() {
    AEStackFactory.register[IAEFluidStack](AEFluidStackType.FLUID_STACK_TYPE, ConverterAEFluidStack.convert, ConverterAEFluidStack.parse)
    AEStackFactory.register[IAEItemStack](AEItemStackType.ITEM_STACK_TYPE, ConverterAEItemStack.convert, ConverterAEItemStack.parse)

    api.IMC.registerWrenchTool("li.cil.oc.integration.appeng.EventHandlerAE2.useWrench")
    api.IMC.registerWrenchToolCheck("li.cil.oc.integration.appeng.EventHandlerAE2.isWrench")

    AEApi.instance.registries.movable.whiteListTileEntity(classOf[Print])

    Driver.add(DriverController)
    Driver.add(DriverExportBus)
    Driver.add(DriverImportBus)
    Driver.add(DriverStorageBus)
    Driver.add(DriverPartInterface)
    Driver.add(DriverPartInterfaceTerminal)
    Driver.add(DriverBlockInterface)
    Driver.add(DriverUpgradeAE)

    Driver.add(ConverterAEItemStack)
    Driver.add(ConverterAEFluidStack)
    Driver.add(new ConverterCellInventory)
    Driver.add(ConverterDimensionalCoord)
    Driver.add(ConverterPattern)

    Driver.add(DriverController.Provider)
    Driver.add(DriverExportBus.Provider)
    Driver.add(DriverImportBus.Provider)
    Driver.add(DriverStorageBus.Provider)
    Driver.add(DriverPartInterface.Provider)
    Driver.add(DriverPartInterfaceTerminal.Provider)
    Driver.add(DriverBlockInterface.Provider)
    Driver.add(DriverUpgradeAE.Provider)

    WirelessHandlerUpgradeAE.register()
    val multi = new li.cil.oc.common.item.Delegator()
    GameRegistry.registerItem(multi, "item.ae")
    addSubItem(new ItemUpgradeAE(multi, Tier.One), Constants.ItemName.UpgradeAE1, "oc:me_upgrade1")
    addSubItem(new ItemUpgradeAE(multi, Tier.Two), Constants.ItemName.UpgradeAE2, "oc:me_upgrade2")
    addSubItem(new ItemUpgradeAE(multi, Tier.Three), Constants.ItemName.UpgradeAE3, "oc:me_upgrade3")
  }
}
