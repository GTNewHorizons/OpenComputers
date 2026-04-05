package li.cil.oc.integration.vanilla

import cpw.mods.fml.common.registry.GameData

import java.util
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.integration.util.MapUtils.MapWrapper
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagString
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.oredict.OreDictionary

import scala.collection.convert.WrapAsScala._
import scala.collection.mutable

object ConverterItemStack extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]) =
    value match {
      case stack: ItemStack =>
        if (Settings.get.insertIdsInConverters) {
          output += "id" -> Int.box(Item.getIdFromItem(stack.getItem))
          output += "oreNames" -> OreDictionary.getOreIDs(stack).map(OreDictionary.getOreName)
        }
        output += "damage" -> Int.box(stack.getItemDamage)
        output += "maxDamage" -> Int.box(stack.getMaxDamage)
        output += "size" -> Int.box(stack.stackSize)
        output += "maxSize" -> Int.box(stack.getMaxStackSize)
        output += "hasTag" -> Boolean.box(stack.hasTagCompound)
        output += "name" -> Item.itemRegistry.getNameForObject(stack.getItem)
        output += "label" -> stack.getDisplayName
        if (stack.hasTagCompound &&
          stack.getTagCompound.hasKey("display", NBT.TAG_COMPOUND) &&
          stack.getTagCompound.getCompoundTag("display").hasKey("Lore", NBT.TAG_LIST)) {
          output += "lore" -> stack.getTagCompound.
            getCompoundTag("display").
            getTagList("Lore", NBT.TAG_STRING).map((tag: NBTTagString) => tag.func_150285_a_()).
            mkString("\n")
        }

        val enchantments = mutable.ArrayBuffer.empty[mutable.Map[String, Any]]
        EnchantmentHelper.getEnchantments(stack).collect {
          case (id: Int, level: Int) if id >= 0 && id < Enchantment.enchantmentsList.length && Enchantment.enchantmentsList(id) != null =>
            val enchantment = Enchantment.enchantmentsList(id)
            val map = mutable.Map(
              "name" -> enchantment.getName,
              "label" -> enchantment.getTranslatedName(level),
              "level" -> level
            )
            if (Settings.get.insertIdsInConverters) {
              map += "id" -> id
            }
            enchantments += map
        }
        if (enchantments.nonEmpty) {
          output += "enchantments" -> enchantments
        }

        if (stack.hasTagCompound && Settings.get.allowItemStackNBTTags) {
          output += "tag" -> CompressedStreamTools.compress(stack.getTagCompound)
        }
      case _ =>
    }

  private val ItemRegistry = GameData.getItemRegistry
  def parse(args: util.Map[_, _]): ItemStack =
  {
    val id = args.getInt("id")
    val name = args.getString("name")
    val item = (id, name) match {
      case (Some(i), _) => ItemRegistry.getObjectById(i)
      case (_, Some(n)) => ItemRegistry.getObject(n)
      case _ => throw new IllegalArgumentException("item id or name expected")
    }
    if (item == null) throw new IllegalArgumentException("item not found")
    val amount = args.getInt("size").getOrElse(1)
    val damage = args.getInt("damage").getOrElse(0)
    val stack = new ItemStack(item, amount, damage)
    stack
  }
}
