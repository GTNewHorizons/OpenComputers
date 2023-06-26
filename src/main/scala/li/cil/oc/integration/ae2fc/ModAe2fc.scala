package li.cil.oc.integration.ae2fc

import li.cil.oc.integration.{Mod, ModProxy, Mods}

object ModAe2fc  extends ModProxy {
  override def getMod: Mod = Mods.Ae2Fc
  override def initialize(): Unit = {}
}
