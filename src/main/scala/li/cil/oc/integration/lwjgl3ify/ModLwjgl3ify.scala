package li.cil.oc.integration.lwjgl3ify

import li.cil.oc.integration.{ModProxy, Mods}

object ModLwjgl3ify extends ModProxy {
  override def getMod: Mods.SimpleMod = Mods.lwjgl3ify

  override def initialize(): Unit = {
    try {
      val clazz = Class.forName("li.cil.oc.integration.lwjgl3ify.FileUploadSupport")
      val support = clazz.getDeclaredConstructor().newInstance().asInstanceOf[IFileUpload]

      support.init()
    } catch {
      case _: Throwable =>
    }
  }
}
