package li.cil.oc.integration.thaumcraft

import li.cil.oc.api.driver.Converter
import net.minecraft.item.ItemStack
import thaumcraft.api.aspects.{AspectList, IAspectContainer}
import thaumcraft.common.items.wands.ItemWandCasting

import java.util
import scala.collection.convert.WrapAsScala._

object ConverterAspectItem extends Converter {
  override def convert(value: scala.Any, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case stack: ItemStack if stack.hasTagCompound =>
      var aspects = new AspectList()
      aspects.readFromNBT(stack.getTagCompound)
      if (aspects.size() > 0)
        output += "aspects" -> TCUtils.convert_aspects(aspects)
      stack.getItem match {
        case wand : ItemWandCasting =>
          aspects = wand.getAllVis(stack)
          if (aspects.size() > 0) {
            output += "aspects" -> TCUtils.convert_aspects(aspects)
          }
        case _ =>
      }

    case _ =>
  }
}
