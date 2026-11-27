package li.cil.oc.integration.matter_manipulator

import li.cil.oc.api.Driver
import li.cil.oc.integration.{ModProxy, Mods}

object ModMatterManipulator extends ModProxy {
  override def getMod = Mods.MatterManipulator

  override def initialize() {
    Driver.add(new ConvertMatterManipulator)
    Driver.add(new DriverMatterManipulator)
  }
}