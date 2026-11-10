package li.cil.oc.integration.nee

import li.cil.oc.api.driver.Converter
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

import java.util
import scala.collection.convert.WrapAsScala._

object ConverterPattern extends Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = {
    value match {
      case is: ItemStack =>
          val encodedValue: NBTTagCompound = is.getTagCompound
          if (encodedValue != null && encodedValue.hasKey("neeExtraTags")) {
            val extraTags = encodedValue.getCompoundTag("neeExtraTags")
            output += "neeExtraTags" -> extraTags
          }
      case _ =>
    }
  }
}
