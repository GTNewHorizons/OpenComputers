package li.cil.oc.integration.util

import li.cil.oc.integration.Mods
import net.minecraft.item.ItemStack
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator
import com.recursive_pineapple.matter_manipulator.common.items.MMUpgrades
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode

object MatterManipulator {

  def isMatterManipulator(stack: ItemStack) =
    stack != null && stack.stackSize > 0 &&
      Mods.MatterManipulator.isAvailable &&
      stack.getItem.isInstanceOf[ItemMatterManipulator]

  def getPlaceTicks(stack: ItemStack) =
    stack.getItem match {
    case item_mm : ItemMatterManipulator => {
      var placeTicks = item_mm.tier.placeTicks
      var state = ItemMatterManipulator.getState(stack)
      if (state.hasUpgrade(MMUpgrades.Speed)) placeTicks = placeTicks / 2
      placeTicks
    }
    case _ => 0
  }
}
