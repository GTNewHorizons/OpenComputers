package li.cil.oc.integration.appeng

import appeng.util.Platform
import li.cil.oc.api.driver.Converter
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}

import java.util
import scala.collection.convert.WrapAsScala._

object ConverterPattern extends Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = {
    value match {
      case is: ItemStack =>
        try {
          val encodedValue: NBTTagCompound = is.getTagCompound
          if (encodedValue != null) {
            val inTag: NBTTagList = encodedValue.getTagList("in", 10)
            val outTag: NBTTagList = encodedValue.getTagList("out", 10)
            val inputs = Array.tabulate(inTag.tagCount()) { i =>
              Platform.readStackNBT(inTag.getCompoundTagAt(i))
            }
            val outputs = Array.tabulate(outTag.tagCount()) { i =>
              Platform.readStackNBT(outTag.getCompoundTagAt(i))
            }
            output += "inputs" -> inputs
            output += "outputs" -> outputs
            output += "isCraftable" -> Boolean.box(encodedValue.getBoolean("crafting"))
          }
        } catch {
          case ignored: Throwable =>
        }
      case _ =>
    }
  }
}
