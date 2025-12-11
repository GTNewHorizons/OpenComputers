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
import net.minecraft.server.MinecraftServer
import scala.collection.mutable
import scala.util.control.Breaks._
import thaumcraft.api.ThaumcraftApi
import thaumcraft.api.crafting.IArcaneRecipe
import scala.collection.JavaConverters._
import thaumcraft.common.tiles.TileMagicWorkbench
import thaumcraft.api.ThaumcraftApiHelper
import thaumcraft.common.items.wands.ItemWandCasting
import thaumcraft.api.aspects.Aspect
import scala.collection.convert.WrapAsJava._


class UpgradeArcaneCrafting(val host: EnvironmentHost with internal.Robot) extends prefab.ManagedEnvironment with DeviceInfo {
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
      var player = host.player
      val wandSlotIndex = 12 // Change this if your wand is in a different slot
      val workbenchTile = new TileMagicWorkbench()  // Simulate arcane workbench for ThaumcraftApi
      val real_player = getRealPlayer

      load(player.inventory)
      loadArcane(player.inventory, workbenchTile)
      val cm = CraftingManager.getInstance
      var countCrafted = 0

      // Find matching arcane recipe, if any
      val recipes = ThaumcraftApi.getCraftingRecipes.asInstanceOf[java.util.List[Any]].asScala
      val arcaneRecipeOpt = recipes.collectFirst {
        case arc: IArcaneRecipe if arc.matches(workbenchTile, host.world, real_player) => arc
      }
      val isArcaneTime = arcaneRecipeOpt.isDefined
      val arcaneRecipe = arcaneRecipeOpt.orNull      

      val originalCraft = 
        if (isArcaneTime && arcaneRecipe.matches(workbenchTile, host.world, real_player)) arcaneRecipe.getCraftingResult(workbenchTile) 
        else cm.findMatchingRecipe(CraftingInventory, host.world)
      if (originalCraft == null) {
        return Seq(false, 0)
      }
      breakable {
        while (countCrafted < wantedCount) {
          val result = 
            if (isArcaneTime && arcaneRecipe.matches(workbenchTile, host.world, real_player)) arcaneRecipe.getCraftingResult(workbenchTile) 
            else cm.findMatchingRecipe(CraftingInventory, host.world)
          if (result == null || result.stackSize < 1) break()
          if (!originalCraft.isItemEqual(result)) {
            break()
          }

          if (isArcaneTime) {
            // Arcane path: vis manipulation
            val wand = getWandFromToolSlot()
            if (wand == null) return Seq(false, countCrafted, "no wand in slot 12")
            if (!checkVis(wand, arcaneRecipe, workbenchTile, real_player)) return Seq(false, countCrafted, "not enough vis for craft: ")
            deductVis(wand, arcaneRecipe, workbenchTile, real_player)
          } 
          
          countCrafted += result.stackSize
          FMLCommonHandler.instance.firePlayerCraftingEvent(real_player, result, this)

          val surplus = mutable.ArrayBuffer.empty[ItemStack]
          for (slot <- 0 until getSizeInventory) {
            val stack = getStackInSlot(slot)
            if (stack != null) {
              decrStackSize(slot, 1)
              val item = stack.getItem
              if (item.hasContainerItem(stack)) {
                val container = item.getContainerItem(stack)
                if (container.isItemStackDamageable && container.getItemDamage > container.getMaxDamage) {
                  MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, container))
                }
                else if (container.getItem.doesContainerItemLeaveCraftingGrid(container) || getStackInSlot(slot) != null) {
                  surplus += container
                }
                else {
                  setInventorySlotContents(slot, container)
                }
              }
            }
          }
          save(player.inventory)
          InventoryUtils.addToPlayerInventory(result, player)
          for (stack <- surplus) {
            InventoryUtils.addToPlayerInventory(stack, player)
          }
          load(player.inventory)
          if (isArcaneTime) loadArcane(player.inventory, workbenchTile)
        }
      }
      Seq(originalCraft != null, countCrafted, if (isArcaneTime) "arcane crafting completed" else "normal crafting completed")
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

    // --- Arcane helpers ---
    def getWandFromToolSlot(): ItemStack = {
      val wandSlot = host.mainInventory.getStackInSlot(12)
      if (wandSlot != null &&  wandSlot.getItem.getClass.getName.contains("Wand")) return wandSlot
      null
    }
    
    // Loads items into arcane crafting table inventory for futher processes
    def loadArcane(inventory: IInventory, workbenchTile: TileMagicWorkbench): Unit = {
      for (slot <- 0 until 9) {
        val stack = inventory.getStackInSlot(toParentSlot(slot))
        workbenchTile.setInventorySlotContentsSoftly(slot, if (stack != null) stack.copy() else null)
      }
    }

    // Real player for reasearch and vis calculation     
    def getRealPlayer: EntityPlayer = {
      val world = host.world
      val playerName = try {
        val n = host.player.getCommandSenderName
        if (n != null && n.nonEmpty) n else null
      } catch { case _: Throwable => null }
      val playerInWorld = world.playerEntities.asScala.collectFirst {
        case p: EntityPlayer if p.getCommandSenderName == playerName => p
      }
      if (playerInWorld.isDefined) return playerInWorld.get
      val server = MinecraftServer.getServer
      if (server != null) {
        val playerMP = server.getConfigurationManager.func_152612_a(playerName)
        if (playerMP != null) return playerMP
      }
      world.playerEntities.asScala.collectFirst {
        case p: EntityPlayer if !p.getCommandSenderName.toLowerCase.contains("[oc]") => p
      }.orNull
    }    


    // Check if wand/scepter does have enought vis
    def checkVis(wand: ItemStack, arcaneRecipe: IArcaneRecipe, workbenchTile: TileMagicWorkbench, player: EntityPlayer): Boolean = {
      val requiredAspectsList = Option(arcaneRecipe.getAspects(workbenchTile))
      val requiredAspects = requiredAspectsList.filter(_ != null).map(_.getAspects.map(a => a.getTag -> requiredAspectsList.get.getAmount(a)).toMap).getOrElse(Map.empty)
      val wandItem = wand.getItem.asInstanceOf[ItemWandCasting]
      val wandAspectList = wandItem.getAllVis(wand)
      var hasAll = true
      for ((aspectTag, baseVis) <- requiredAspects) {
        val aspect = Aspect.getAspect(aspectTag)
        val requiredVis = (baseVis * 100 * wandItem.getConsumptionModifier(wand, player, aspect, true)).toInt
        val wandAmount = wandAspectList.getAmount(aspect)
        if (wandAmount < requiredVis) {
          hasAll = false
        }
      }
      hasAll
    }

    // Syphon vis from wand/scepter based on craft
    def deductVis(wand: ItemStack, arcaneRecipe: IArcaneRecipe, workbenchTile: TileMagicWorkbench, player: EntityPlayer): Unit = {
      val requiredAspectsList = Option(arcaneRecipe.getAspects(workbenchTile))      
      val requiredAspects = requiredAspectsList.filter(_ != null).map(_.getAspects.map(a => a.getTag -> requiredAspectsList.get.getAmount(a)).toMap).getOrElse(Map.empty)
      val wandItem = wand.getItem.asInstanceOf[ItemWandCasting]
      for ((aspectTag, baseVis) <- requiredAspects) {
        val aspect = Aspect.getAspect(aspectTag)
        val requiredVis = (baseVis * 100 * wandItem.getConsumptionModifier(wand, player, aspect, true)).toInt
        val tag = aspect.getTag
        if (wand.getTagCompound.hasKey(tag)) {
          val current = wand.getTagCompound.getInteger(tag)
          val newValue = Math.max(0, current - requiredVis)
          wand.getTagCompound.setInteger(tag, newValue)
        }
      }
    }
  }
}
