package li.cil.oc.server.component

import java.util

import cpw.mods.fml.common.FMLCommonHandler
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import li.cil.oc.server.agent.Player
import li.cil.oc.util.InventoryUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.CraftingManager
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent

import scala.collection.convert.WrapAsJava._

class UpgradeCrafting(val host: EnvironmentHost with internal.Robot) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("crafting").
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Assembly controller",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "MultiCombinator-9S"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  @Callback(doc = """function([count:number]):number -- Tries to craft the specified number of items in the top left area of the inventory.""")
  def craft(context: Context, args: Arguments): Array[AnyRef] = {
    val count = args.optInteger(0, 64) max 0 min 64
    result(CraftingInventory.craft(count): _*)
  }

  private object CraftingInventory extends inventory.InventoryCrafting(new inventory.Container {
    override def canInteractWith(player: EntityPlayer) = true
  }, 3, 3) {
    var amountPossible = 0

    def craft(wantedCount: Int): Seq[_] = {
      val player = host.player
      load(player.inventory)

      // The result of the recipe the grid holds, or null if it matches none in which case nothing is crafted.
      val firstResult = findRecipeResult()
      if (firstResult == null) return Seq(false, 0)

      var craftedCount = 0
      var result = firstResult

      // Repeat the first craft until the wanted count is reached or the grid no longer makes the same item.
      while (craftedCount < wantedCount && isSameResult(firstResult, result)) {
        craftedCount += result.stackSize
        craftOnce(player, result)
        result = findRecipeResult()
      }

      Seq(true, craftedCount)
    }

    private def findRecipeResult(): ItemStack =
      CraftingManager.getInstance.findMatchingRecipe(this, host.world)

    private def isSameResult(firstResult: ItemStack, result: ItemStack) =
      result != null && result.stackSize > 0 && firstResult.isItemEqual(result)

    private def craftOnce(player: EntityPlayer, result: ItemStack) {
      FMLCommonHandler.instance.firePlayerCraftingEvent(player, result, this)
      val surplus = consumeIngredients(player)
      save(player.inventory)
      (result +: surplus).foreach(InventoryUtils.addToPlayerInventory(_, player))
      load(player.inventory)
    }

    // Takes one item from every grid slot and leaves its container item there. Returns the containers that can't stay.
    private def consumeIngredients(player: EntityPlayer): Seq[ItemStack] =
      (0 until getSizeInventory).flatMap(consumeIngredient(player, _))

    private def consumeIngredient(player: EntityPlayer, slot: Int): Option[ItemStack] = {
      val ingredient = getStackInSlot(slot)
      if (ingredient == null) return None
      decrStackSize(slot, 1)
      containerItem(ingredient).flatMap(returnContainerItem(player, slot, _))
    }

    private def containerItem(stack: ItemStack): Option[ItemStack] =
      if (stack.getItem.hasContainerItem(stack)) Option(stack.getItem.getContainerItem(stack)) else None

    private def returnContainerItem(player: EntityPlayer, slot: Int, container: ItemStack): Option[ItemStack] =
      if (container.isItemStackDamageable && container.getItemDamage > container.getMaxDamage) {
        MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, container))
        None
      }
      else if (container.getItem.doesContainerItemLeaveCraftingGrid(container) || getStackInSlot(slot) != null) {
        Some(container)
      }
      else {
        setInventorySlotContents(slot, container)
        None
      }

    def load(inventory: IInventory) {
      amountPossible = Int.MaxValue
      for (slot <- 0 until getSizeInventory) {
        val stack = inventory.getStackInSlot(toParentSlot(slot))
        setInventorySlotContents(slot, stack)
        if (stack != null) {
          amountPossible = math.min(amountPossible, stack.stackSize)
        }
      }
    }

    def save(inventory: IInventory) {
      for (slot <- 0 until getSizeInventory) {
        inventory.setInventorySlotContents(toParentSlot(slot), getStackInSlot(slot))
      }
    }

    private def toParentSlot(slot: Int) = {
      val col = slot % 3
      val row = slot / 3
      row * 4 + col
    }
  }

}
