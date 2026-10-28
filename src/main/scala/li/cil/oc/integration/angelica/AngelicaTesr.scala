package li.cil.oc.integration.angelica

import cpw.mods.fml.common.ModAPIManager
import li.cil.oc.Settings
import li.cil.oc.integration.Mods

object AngelicaTesr {
  val apiVersion: Int = if (Settings.get.angelicaBatching) installedVersion(Mods.IDs.AngelicaTesr) else 0

  val Batching: Boolean = apiVersion >= 1

  val MeshReplacement: Boolean = apiVersion >= 3

  val SourceShaders: Boolean = apiVersion >= 3

  private def installedVersion(id: String): Int = {
    val apis = ModAPIManager.INSTANCE.getAPIList.iterator
    while (apis.hasNext) {
      val api = apis.next()
      if (api.getModId == id) return try api.getVersion.toInt catch {
        case _: NumberFormatException => 0
      }
    }
    0
  }
}
