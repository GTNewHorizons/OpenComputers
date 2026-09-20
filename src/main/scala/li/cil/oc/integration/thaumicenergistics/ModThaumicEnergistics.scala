package li.cil.oc.integration.thaumicenergistics

import li.cil.oc.api.Driver
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.integration.{Mod, ModProxy, Mods}
import thaumicenergistics.common.storage.{AEEssentiaStack, AEEssentiaStackType}

object ModThaumicEnergistics extends ModProxy {
  override def getMod: Mod = Mods.ThaumicEnergistics

  private lazy val lazyRegister = new {
    AEStackFactory.register[AEEssentiaStack](AEEssentiaStackType.ESSENTIA_STACK_TYPE, ConvertAEEssentiaStack.convert, ConvertAEEssentiaStack.parse)
  }
  override def initialize(): Unit = {
    lazyRegister
    Driver.add(DriverController)
    Driver.add(DriverBlockInterface)
    Driver.add(DriverEssentiaExportBus)
    Driver.add(DriverEssentiaImportBus)
    Driver.add(DriverEssentiaStorageBus)

    Driver.add(DriverController.Provider)
    Driver.add(DriverBlockInterface.Provider)
    Driver.add(DriverEssentiaExportBus.Provider)
    Driver.add(DriverEssentiaImportBus.Provider)
    Driver.add(DriverEssentiaStorageBus.Provider)
    Driver.add(ConvertAspectCraftable)
  }
}