package li.cil.oc.integration.matter_manipulator

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location
import li.cil.oc.api.driver.Converter

import java.util
import scala.collection.convert.WrapAsScala._

class ConvertMatterManipulator extends Converter{
  override def convert(value: Any, output: util.Map[AnyRef, AnyRef]): Unit = {
    value match {
      case location: Location => {
        output += "x" -> Int.box(location.x)
        output += "y" -> Int.box(location.y)
        output += "z" -> Int.box(location.z)
      }
      case _ =>
    }
  }
}