package li.cil.oc.integration.thaumicenergistics

import li.cil.oc.api.driver.Converter
import li.cil.oc.integration.util.MapUtils.MapWrapper
import thaumcraft.api.aspects.Aspect
import thaumicenergistics.common.storage.AEEssentiaStack

import java.util
import scala.collection.JavaConversions.mapAsScalaMap

object ConvertAEEssentiaStack extends Converter {
  override def convert(value: Any, output: util.Map[AnyRef, AnyRef]): Unit = {
    value match {
      case stack: AEEssentiaStack =>
        output += "name" -> stack.getAspect.getName
        output += "amount" -> Long.box(stack.getStackSize)
        output += "label" -> stack.getLocalizedName
      case _ =>
    }
  }

  def parse(args: util.Map[_, _]): AEEssentiaStack = {
    val name = args.getString("name") match {
      case Some(n) => n
      case None => throw new IllegalArgumentException("aspect name expected")
    }
    val aspect = Aspect.getAspect(name)
    if (aspect == null) throw new IllegalArgumentException("aspect not found")
    val amount = args.getInt("amount").getOrElse(0)
    new AEEssentiaStack(aspect, amount)
  }
}
