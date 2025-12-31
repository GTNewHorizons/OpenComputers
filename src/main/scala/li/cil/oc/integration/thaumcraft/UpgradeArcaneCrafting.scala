package li.cil.oc.integration.thaumcraft

import com.mojang.authlib.GameProfile
import cpw.mods.fml.common.FMLCommonHandler
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network._
import li.cil.oc.api.{Network, internal, prefab}
import li.cil.oc.server.component.result
import li.cil.oc.util.InventoryUtils
import net.minecraft.entity.player.{EntityPlayer, EntityPlayerMP}
import net.minecraft.inventory
import net.minecraft.inventory.{IInventory, InventoryCrafting}
import net.minecraft.item.crafting.CraftingManager
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.world.WorldServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent
import thaumcraft.api.ThaumcraftApi
import thaumcraft.api.aspects.AspectList
import thaumcraft.api.crafting.IArcaneRecipe
import thaumcraft.common.Thaumcraft
import thaumcraft.common.config.{Config, ConfigBlocks, ConfigItems}
import thaumcraft.common.items.ItemEssence
import thaumcraft.common.items.wands.ItemWandCasting
import thaumcraft.common.tiles.{TileMagicWorkbench, TileMagicWorkbenchCharger}

import java.util
import scala.collection.JavaConverters._
import scala.collection.convert.WrapAsJava._
import scala.collection.mutable.ArrayBuffer


class UpgradeArcaneCrafting(val host: EnvironmentHost with internal.Robot) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("arcane_crafting").
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Arcane matrix controller",
    DeviceAttribute.Vendor -> "Thaumic Logic Industries",
    DeviceAttribute.Product -> "ArcaneCombinator-X1"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  @Callback(doc = """function([count:number]):boolean, number, string -- Tries to craft the specified number of items in the top left area of the inventory using both normal and arcane recipes.""")
  def craft(context: Context, args: Arguments): Array[AnyRef] = {
    val wantedCount = args.optInteger(0, 64) max 0 min 64
    var res = CraftingInventory.craftNormal(wantedCount)
    if (res.count == 0) res = CraftingInventory.craftArcane(wantedCount)
    res.error match {
      case Some(msg) => result(res.count != 0, res.count, msg)
      case None => result(res.count != 0, res.count)
    }
  }

  @Callback(doc = """function([count:number]):boolean, number, string -- Tries to craft the specified number of items in the top left area of the inventory using only vanilla crafting recipes.""")
  def craftNormal(context: Context, args: Arguments): Array[AnyRef] = {
    val wantedCount = args.optInteger(0, 64) max 0 min 64
    var res = CraftingInventory.craftNormal(wantedCount)
    res.error match {
      case Some(msg) => result(res.count != 0, res.count, msg)
      case None => result(res.count != 0, res.count)
    }
  }

  @Callback(doc = """function([count:number]):boolean, number, string -- Tries to craft the specified number of items in the top left area of the inventory using only Thaumcraft crafting recipes.""")
  def craftArcane(context: Context, args: Arguments): Array[AnyRef] = {
    val wantedCount = args.optInteger(0, 64) max 0 min 64
    var res = CraftingInventory.craftArcane(wantedCount)
    res.error match {
      case Some(msg) => result(res.count != 0, res.count, msg)
      case None => result(res.count != 0, res.count)
    }
  }

  override val canUpdate = true

  private var checkDelay = 0

  override def update(): Unit = {
    super.update()
    if (checkDelay > 0) {
      checkDelay -= 1
      return
    }
    val hasWork = for {
      tm <- Option(host.world().getTileEntity(host.xPosition.toInt, host.yPosition.toInt + 1, host.zPosition.toInt))
        .collect { case t: TileMagicWorkbenchCharger => t }
      wandStack <- Option(host.getStackInSlot(0))
      wand <- Option(wandStack.getItem).collect { case w: ItemWandCasting => w }
    } yield {
      val al = wand.getAspectsWithRoom(wandStack)
      if (al.size > 0) {
        for (aspect <- al.getAspects) {
          val drain = Math.min(5, wand.getMaxVis(wandStack) - wand.getVis(wandStack, aspect))
          if (drain > 0) wand.addRealVis(wandStack, aspect, tm.consumeVis(aspect, drain), true)
        }
      }
      true
    }
    if (hasWork.isEmpty) {
      checkDelay = 20
    }
  }

  private object CraftingInventory extends inventory.InventoryCrafting(new inventory.Container {
    override def canInteractWith(player: EntityPlayer) = true
  }, 3, 3) {
    def craftNormal(wantedCount: Int): CraftResult = {
      craft(wantedCount) { (inv, player) =>
        val result = CraftingManager.getInstance.findMatchingRecipe(inv, player.worldObj)
        Option(result)
          .map(r => RecipeMatch(r, () => {}))
          .getOrElse(RecipeNotFound)
      }
    }

    def craftArcane(wantedCount: Int): CraftResult = {
      craft(wantedCount) { (inv, player) =>
        Option(findArcaneRecipe(inv, player)) match {
          case Some(recipe) =>
            val result = recipe.getCraftingResult(inv)
            val wandStack = host.getStackInSlot(0)
            val aspects = Option(recipe.getAspects(inv)).getOrElse(recipe.getAspects)

            if (wandStack == null || !wandStack.getItem.isInstanceOf[ItemWandCasting]) {
              RecipeMissingRequirement("Missing a valid Wand to craft.")
            } else if (!consumeAllVis(wandStack, aspects, doit = false)) {
              RecipeMissingRequirement("Insufficient Vis in wand to perform crafting.")
            } else {
              RecipeMatch(result, () => {
                consumeAllVis(wandStack, aspects, doit = true)
                onCrafting(result)
              })
            }
          case None => RecipeNotFound
        }
      }
    }

    sealed trait RecipeResult

    case object RecipeNotFound extends RecipeResult

    case class RecipeMissingRequirement(reason: String) extends RecipeResult

    case class RecipeMatch(output: ItemStack, onCraft: () => Unit) extends RecipeResult

    case class CraftResult(count: Int = 0, error: Option[String] = None)

    def craft(wantedCount: Int)(findResult: (InventoryCrafting, EntityPlayer) => RecipeResult): CraftResult = {
      val player = host.player
      var countCrafted = 0
      var firstResult: ItemStack = null

      while (countCrafted < wantedCount) {
        load(player.inventory)
        findResult(this, player) match {
          case RecipeMatch(result, onCraft) =>
            if (firstResult == null) firstResult = result
            if (result.isItemEqual(firstResult) && result.stackSize > 0) {
              onCraft()
              FMLCommonHandler.instance.firePlayerCraftingEvent(player, result, this)
              val surplus = consumeGrid(player)
              countCrafted += result.stackSize
              save(player.inventory)
              InventoryUtils.addToPlayerInventory(result, player)
              surplus.foreach(InventoryUtils.addToPlayerInventory(_, player))
            }
            else return CraftResult(count = countCrafted)
          case RecipeNotFound =>
            val err = if (countCrafted == 0) Some("Can't find the recipe.") else None
            return CraftResult(count = countCrafted, error = err)
          case RecipeMissingRequirement(error) => return CraftResult(count = countCrafted, error = Some(error))
        }
      }
      CraftResult(count = countCrafted)
    }

    private def consumeGrid(player: EntityPlayer): ArrayBuffer[ItemStack] = {
      def isDestroyed(s: ItemStack): Boolean =
        s.isItemStackDamageable && s.getItemDamage > s.getMaxDamage

      def shouldLeaveGrid(slot: Int, s: ItemStack): Boolean =
        s.getItem.doesContainerItemLeaveCraftingGrid(s) || getStackInSlot(slot) != null

      val surplus = new ArrayBuffer[ItemStack]
      for (slot <- 0 until getSizeInventory) {
        Option(getStackInSlot(slot)).foreach { stack =>
          decrStackSize(slot, 1)
          val item = stack.getItem
          if (item.hasContainerItem(stack)) {
            val container = item.getContainerItem(stack)
            if (isDestroyed(container)) {
              MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, container))
            } else if (shouldLeaveGrid(slot, container)) {
              surplus += container
            } else {
              setInventorySlotContents(slot, container)
            }
          }
        }
      }
      surplus
    }

    private def load(inventory: IInventory): Unit = {
      for (slot <- 0 until getSizeInventory) {
        setInventorySlotContents(slot, inventory.getStackInSlot(toParentSlot(slot)))
      }
    }

    private def save(inventory: IInventory): Unit = {
      for (slot <- 0 until getSizeInventory) {
        inventory.setInventorySlotContents(toParentSlot(slot), getStackInSlot(slot))
      }
    }

    private def toParentSlot(slot: Int) = {
      val col = slot % 3
      val row = slot / 3
      row * 4 + col
    }

    private def findArcaneRecipe(inv: IInventory, player: EntityPlayer): IArcaneRecipe = {
      val workbenchTile = new TileMagicWorkbench;
      for (slotIndex <- 0 until 9) {
        workbenchTile.setInventorySlotContentsSoftly(slotIndex, inv.getStackInSlot(slotIndex))
      }
      ThaumcraftApi.getCraftingRecipes.asScala.collectFirst {
        case r: IArcaneRecipe if r.matches(workbenchTile, player.worldObj, ArcaneProxy) => r
      }.orNull
    }
  }

  private def consumeAllVis(is: ItemStack, aspects: AspectList, doit: Boolean): Boolean = {
    if (aspects == null || aspects.size == 0) return false
    val wand = is.getItem match {
      case w: ItemWandCasting => w
      case _ => return false
    }
    val finalCosts = aspects.getAspects.map { aspect =>
      val baseCost = aspects.getAmount(aspect) * 100
      val actualCost = (baseCost * wand.getConsumptionModifier(is, null, aspect, true)).toInt
      if (wand.getVis(is, aspect) < actualCost) return false
      aspect -> actualCost
    }.toMap
    if (doit) {
      finalCosts.foreach { case (aspect, cost) =>
        val currentVis = wand.getVis(is, aspect)
        wand.storeVis(is, aspect, currentVis - cost)
      }
    }
    true
  }

  private def onCrafting(crafting: ItemStack): Unit = {
    val warp = ThaumcraftApi.getWarp(crafting)
    if (!Config.wuss && warp > 0) addStickyWarpToOwner(warp)
    if ((crafting.getItem eq ConfigItems.itemResource) && crafting.getItemDamage == 13 && crafting.hasTagCompound) for (var2 <- 0 until 9) {
      val var3 = CraftingInventory.getStackInSlot(var2)
      if (var3 != null && var3.getItem.isInstanceOf[ItemEssence]) {
        var3.stackSize += 1
        CraftingInventory.setInventorySlotContents(var2, var3)
      }
    }
    if ((crafting.getItem eq Item.getItemFromBlock(ConfigBlocks.blockMetalDevice)) && crafting.getItemDamage == 3) {
      val var3 = CraftingInventory.getStackInSlot(4)
      var3.stackSize += 1
      CraftingInventory.setInventorySlotContents(4, var3)
    }
  }

  object ArcaneProxy extends FakePlayer(host.world.asInstanceOf[WorldServer], new GameProfile(null, "ArcaneProxy")) {
    override def getCommandSenderName: String = host.ownerName()
  }

  private var cachedStickyWarp = 0

  private def findOwner(): Option[EntityPlayerMP] = Option(MinecraftServer.getServer.getConfigurationManager.func_152612_a(host.ownerName()))

  private def addStickyWarpToOwner(amount: Int): Unit = {
    cachedStickyWarp = cachedStickyWarp + amount
    findOwner().foreach {
      owner =>
        Thaumcraft.addStickyWarpToPlayer(owner, cachedStickyWarp)
        cachedStickyWarp = 0
    }
  }

  override def load(nbt: NBTTagCompound): Unit = {
    super.load(nbt)
    cachedStickyWarp = nbt.getInteger("cachedStickyWarp")
  }

  override def save(nbt: NBTTagCompound): Unit = {
    super.save(nbt)
    nbt.setInteger("cachedStickyWarp", cachedStickyWarp)
  }
}
