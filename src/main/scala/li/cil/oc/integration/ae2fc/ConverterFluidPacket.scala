package li.cil.oc.integration.ae2fc

import com.glodblock.github.common.item.ItemFluidPacket
import li.cil.oc.api.driver.Converter
import net.minecraft.item.ItemStack

import java.util
import scala.collection.convert.WrapAsScala._

object ConverterFluidPacket extends Converter {

  override def convert(value: Any, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case stack: ItemStack if stack.getItem.isInstanceOf[ItemFluidPacket] =>
      output += "fluidPacket" -> ItemFluidPacket.getFluidStack(stack)
    case _ =>
  }
}
