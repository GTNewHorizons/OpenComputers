package li.cil.oc.integration.nee

import li.cil.oc.api.Driver
import li.cil.oc.integration.{ModProxy, Mods}

object ModNotEnoughEnergistics extends ModProxy {
  override def getMod: Mods.SimpleMod = Mods.NotEnoughEnergistics

  override def initialize(): Unit = {
    Driver.add(ConverterPattern)
  }
}
