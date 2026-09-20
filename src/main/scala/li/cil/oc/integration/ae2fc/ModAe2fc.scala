package li.cil.oc.integration.ae2fc

import li.cil.oc.api.Driver
import li.cil.oc.integration.{Mod, ModProxy, Mods}

object ModAe2fc extends ModProxy {
  override def getMod: Mod = Mods.Ae2Fc

  override def initialize(): Unit = {
    Driver.add(ConverterFluidDrop)
    Driver.add(ConverterFluidPacket)
    Driver.add(new ConverterFluidCellInventory)

    Driver.add(DriverBlockFluidInterface)
    Driver.add(DriverBlockFluidInterface.Provider)
    Driver.add(DriverPartFluidInterface)
    Driver.add(DriverPartFluidInterface.Provider)
    Driver.add(DriverFluidExportBus)
    Driver.add(DriverFluidExportBus.Provider)
    Driver.add(DriverFluidImportBus)
    Driver.add(DriverFluidImportBus.Provider)
    Driver.add(DriverFluidStorageBus)
    Driver.add(DriverFluidStorageBus.Provider)
  }
}
