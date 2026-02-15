package li.cil.oc.integration.appeng

import appeng.api.storage.data.IAEStack
import li.cil.oc.api.driver.Converter

import java.util

object ConverterAEStack extends Converter {
  override def convert(value: Any, output: util.Map[AnyRef, AnyRef]): Unit = {
    value match {
      case stack: IAEStack[_] =>
        AEStackFactory.convert(stack, output)
    }
  }
}