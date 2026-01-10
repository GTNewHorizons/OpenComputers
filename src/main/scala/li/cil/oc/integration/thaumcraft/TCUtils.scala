package li.cil.oc.integration.thaumcraft

import thaumcraft.api.aspects.AspectList

object TCUtils {
  def convert_aspects(aspects: AspectList): Array[Map[String, Any]] =
    aspects.getAspects.filter(_ != null)
      .map { aspect =>
        Map(
          "name" -> aspect.getName,
          "amount" -> aspects.getAmount(aspect)
        )
      }
      .toArray
}
