package li.cil.oc.integration.lwjgl3ify

import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

object ModLwjgl3ify extends ModProxy {
  override def getMod: Mods.SimpleMod = Mods.lwjgl3ify

  override def initialize(): Unit = {
    FileUploadSupport.init()
  }
}
