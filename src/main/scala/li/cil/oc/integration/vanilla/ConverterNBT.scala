package li.cil.oc.integration.vanilla

import li.cil.oc.api
import net.minecraft.nbt._

import java.util
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.convert.WrapAsScala._

object ConverterNBT extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]) =
    value match {
      case nbt: NBTTagCompound => output += "oc:flatten" -> convert(nbt)
      case _ =>
    }

  private def convert(nbt: NBTBase): AnyRef = nbt match {
    case tag: NBTTagByte => Byte.box(tag.func_150290_f())
    case tag: NBTTagShort => Short.box(tag.func_150289_e())
    case tag: NBTTagInt => Int.box(tag.func_150287_d())
    case tag: NBTTagLong => Long.box(tag.func_150291_c())
    case tag: NBTTagFloat => Float.box(tag.func_150288_h())
    case tag: NBTTagDouble => Double.box(tag.func_150286_g())
    case tag: NBTTagByteArray => tag.func_150292_c()
    case tag: NBTTagString => tag.func_150285_a_()
    case tag: NBTTagList =>
      val copy = tag.copy().asInstanceOf[NBTTagList]
      (0 until copy.tagCount).map(_ => convert(copy.removeTag(0))).toArray
    case tag: NBTTagCompound =>
      tag.func_150296_c().collect {
        case key: String => key -> convert(tag.getTag(key))
      }.toMap
    case tag: NBTTagIntArray => tag.func_150302_c()
  }

  def convertWithType(nbt: NBTBase): AnyRef = nbt match {
    case tag: NBTTagString => Map("__nbt_type" -> "string", "__value" -> tag.func_150285_a_())
    case tag: NBTTagByte => Map("__nbt_type" -> "byte", "__value" -> Byte.box(tag.func_150290_f()))
    case tag: NBTTagShort =>
      Map("__nbt_type" -> "short", "__value" -> Short.box(tag.func_150289_e()))
    case tag: NBTTagInt =>
      Map("__nbt_type" -> "int", "__value" -> Int.box(tag.func_150287_d()))
    case tag: NBTTagLong =>
      Map("__nbt_type" -> "long", "__value" -> Long.box(tag.func_150291_c()))
    case tag: NBTTagFloat =>
      Map("__nbt_type" -> "float", "__value" -> Float.box(tag.func_150288_h()))
    case tag: NBTTagDouble =>
      Map("__nbt_type" -> "double", "__value" -> Double.box(tag.func_150286_g()))
    case tag: NBTTagByteArray => Map("__nbt_type" -> "byte_array", "__value" -> tag.func_150292_c.map(_.toInt))
    case tag: NBTTagIntArray => Map("__nbt_type" -> "int_array", "__value" -> tag.func_150302_c())
    case tag: NBTTagList =>
      val copy = tag.copy().asInstanceOf[NBTTagList]
      val list = (0 until copy.tagCount).map(_ => convertWithType(copy.removeTag(0))).toArray
      Map("__nbt_type" -> "list", "__value" -> list)
    case tag: NBTTagCompound =>
      val comp = tag.func_150296_c().collect {
        case key: String => key -> convertWithType(tag.getTag(key))
      }.toMap
      Map("__nbt_type" -> "compound", "__value" -> comp)
  }

  def parseWithType(value: Any): NBTBase = value match {
    case s: String => new NBTTagString(s)
    case map: util.Map[_, _] if map.containsKey("__nbt_type") && map.containsKey("__value") =>
      def toNumber(v: Any): Number = v match {
        case n: Number => n
        case s: String => s.toDouble
        case b: Boolean => if (b) 1 else 0
        case _ => throw new IllegalArgumentException("Failed to convert to number: unsupport type")
      }

      val t = map.get("__nbt_type").asInstanceOf[String]
      val v = map.get("__value")
      (t, v) match {
        case ("byte", num) => new NBTTagByte(toNumber(num).byteValue())
        case ("short", num) => new NBTTagShort(toNumber(num).shortValue())
        case ("int", num) => new NBTTagInt(toNumber(num).intValue())
        case ("long", num) => new NBTTagLong(toNumber(num).longValue())
        case ("float", num) => new NBTTagFloat(toNumber(num).floatValue())
        case ("double", num) => new NBTTagDouble(toNumber(num).doubleValue())
        case ("byte_array", arrMap: util.Map[_, _]) if isLuaArray(arrMap) =>
          val arr = new Array[Byte](arrMap.size)
          for (i <- 1 to arrMap.size()) arr(i - 1) = toNumber(arrMap.get(i)).byteValue()
          new NBTTagByteArray(arr)
        case ("int_array", arrMap: util.Map[_, _]) if isLuaArray(arrMap) =>
          val arr = new Array[Int](arrMap.size)
          for (i <- 1 to arrMap.size()) arr(i - 1) = toNumber(arrMap.get(i)).intValue()
          new NBTTagIntArray(arr)
        case ("list", arrMap: util.Map[_, _]) if isLuaArray(arrMap) =>
          val listTag = new NBTTagList()
          for (i <- 1 to arrMap.size()) {
            val baseTag = parseWithType(arrMap.get(i))
            if (baseTag != null) {
              listTag.appendTag(baseTag)
            }
          }
          listTag
        case ("compound", compMap: util.Map[_, _]) =>
          val compound = new NBTTagCompound
          compMap.asScala.foreach { case (key, value) =>
            val tag = parseWithType(value)
            if (tag != null) {
              compound.setTag(key.asInstanceOf[String], tag)
            }
          }
          compound
        case _ => null
      }
    case _ => null
  }

  private def isLuaArray(map: util.Map[_, _]): Boolean
  = {
    if (map.isEmpty) true
    else {
      (1 to map.size).forall(i => map.containsKey(i))
    }
  }
}
