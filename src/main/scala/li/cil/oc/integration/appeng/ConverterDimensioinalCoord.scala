package li.cil.oc.integration.appeng

import appeng.api.util.DimensionalCoord
import li.cil.oc.api.driver.Converter

import java.util

object ConverterDimensioinalCoord extends Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case coord: DimensionalCoord =>
      output.put("x", Int.box(coord.x))
      output.put("y", Int.box(coord.y))
      output.put("z", Int.box(coord.z))
      output.put("dimId", Int.box(coord.getDimension))
    case _ =>
  }

  def parse(args: util.Map[_, _], defaultDim: Option[Int] = None): DimensionalCoord = {
    def getInt(key: String):Option[Int] = args.get(key) match {
      case value: java.lang.Number => Some(value.intValue)
      case _ => None
    }
    new DimensionalCoord(
      getInt("x").getOrElse(throw new Exception("Missing x")),
      getInt("y").getOrElse(throw new Exception("Missing y")),
      getInt("z").getOrElse(throw new Exception("Missing z")),
      getInt("dimId").orElse(defaultDim).getOrElse(throw new Exception("Missing dimId")))
  }
  def parse(args: util.Map[_, _], defaultDim: Int): DimensionalCoord = parse(args, Some(defaultDim))
}