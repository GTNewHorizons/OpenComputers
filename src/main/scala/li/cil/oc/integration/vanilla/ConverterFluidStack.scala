package li.cil.oc.integration.vanilla

import java.util
import li.cil.oc.Settings
import li.cil.oc.api
import net.minecraftforge.fluids.{FluidRegistry, FluidStack}

import scala.collection.convert.WrapAsScala._
import li.cil.oc.integration.util.MapUtils.MapWrapper

object ConverterFluidStack extends api.driver.Converter {
  override def convert(value: scala.Any, output: util.Map[AnyRef, AnyRef]) =
    value match {
      case stack: FluidStack =>
        if (Settings.get.insertIdsInConverters) {
          output += "id" -> Int.box(stack.getFluid.getID)
        }
        output += "amount" -> Int.box(stack.amount)
        output += "hasTag" -> Boolean.box(stack.tag != null)
        val fluid = stack.getFluid
        if (fluid != null) {
          output += "name" -> fluid.getName
          output += "label" -> fluid.getLocalizedName(stack)
        }
      case _ =>
    }
  def parse(args: util.Map[_, _]): FluidStack = {
    val id = args.getInt("id")
    val name = args.getString("name")
    val fluid = (id, name) match {
      case (Some(i), _) => FluidRegistry.getFluid(i)
      case (_, Some(n)) => FluidRegistry.getFluid(n)
      case _ => throw new IllegalArgumentException("fluid id or name expected")
    }
    if (fluid == null) throw new IllegalArgumentException("fluid not found")
    val amount = args.getInt("amount").getOrElse(0)
    new FluidStack(fluid, amount)
  }
}
