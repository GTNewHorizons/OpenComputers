package li.cil.oc.integration.appeng

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.IGridNode
import appeng.api.networking.crafting.{CraftingItemList, ICraftingLink, ICraftingRequester}
import appeng.api.networking.security.{BaseActionSource, IActionHost, MachineSource}
import appeng.api.networking.storage.IBaseMonitor
import appeng.api.storage.data.{IAEFluidStack, IAEItemStack, IAEStack, IItemList}
import appeng.api.storage.{IMEMonitor, IMEMonitorHandlerReceiver}
import appeng.api.util.AECableType
import appeng.me.cluster.implementations.CraftingCPUCluster
import appeng.me.helpers.IGridProxyable
import appeng.tile.crafting.TileCraftingMonitorTile
import appeng.util.item.{AEFluidStack, AEItemStack}
import appeng.util.{IterationCounter, Platform}
import com.google.common.collect.ImmutableSet
import li.cil.oc.OpenComputers
import li.cil.oc.api.Persistable
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{ManagedEnvironment, Node}
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.common.EventHandler
import li.cil.oc.integration.Mods
import li.cil.oc.integration.ae2fc.Ae2FcUtil
import li.cil.oc.integration.appeng.NetworkControl._
import li.cil.oc.integration.appeng.internal.SubscriptionBase
import li.cil.oc.integration.ec.ECUtil
import li.cil.oc.server.driver.Registry
import li.cil.oc.util.{AE2Bridge, DatabaseAccess}
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ResultWrapper._
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.nbt.{JsonToNBT, NBTTagCompound}
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids.{FluidRegistry, FluidStack}

import java.lang
import javax.annotation.Nonnull
import scala.collection.convert.WrapAsJava._
import scala.collection.convert.WrapAsScala._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.existentials
import scala.reflect.ClassTag

//noinspection ScalaUnusedSymbol
// Note to self: this class is used by ExtraCells (and potentially others), do not rename / drastically change it.
trait AETypes {
  type AEStack = IAEStack[T] forSome {type T <: IAEStack[T]}
}

trait NetworkControl[AETile >: Null <: TileEntity with IGridProxyable with IActionHost] extends AETypes with Persistable with ManagedEnvironment {
  def tile: AETile

  def node: Node

  private def allItems: IItemList[IAEItemStack] = tile.getProxy.getStorage.getItemInventory.getStorageList

  private def getAvailableItem(@Nonnull request: IAEItemStack, iteration: Int = IterationCounter.fetchNewId()): IAEItemStack = tile.getProxy.getStorage.getItemInventory.getAvailableItem(request, iteration)

  private def allFluids: IItemList[IAEFluidStack] = tile.getProxy.getStorage.getFluidInventory.getStorageList

  private def getAvailableFluid(@Nonnull request: IAEFluidStack, iteration: Int = IterationCounter.fetchNewId()): IAEFluidStack = tile.getProxy.getStorage.getFluidInventory.getAvailableItem(request, iteration)

  @Callback(doc = "function():table -- Get a list of tables representing the available CPUs in the network.")
  def getCpus(context: Context, args: Arguments): Array[AnyRef] = {
    val buffer = new mutable.ListBuffer[Map[String, Any]]
    var index = 0
    tile.getProxy.getCrafting.getCpus.foreach(cpu => {
      buffer.append(Map(
        "name" -> cpu.getName,
        "storage" -> cpu.getAvailableStorage,
        "coprocessors" -> cpu.getCoProcessors,
        "busy" -> cpu.isBusy,
        "cpu" -> new Cpu(tile, index, cpu.asInstanceOf[CraftingCPUCluster])
      ))
      index += 1
    })
    result(buffer.toArray)
  }

  @Callback(doc = "function([filter:table]):table -- Get a list of known item recipes. These can be used to issue crafting requests.")
  def getCraftables(context: Context, args: Arguments): Array[AnyRef] = {
    val filter = args.optTable(0, Map.empty[AnyRef, AnyRef]).collect {
      case (key: String, value: AnyRef) => (key, value)
    }

    val builder = mutable.ArrayBuilder.make[AnyRef]
    val types = AEStackFactory.getRegisteredTypes
    types.foreach { tp =>
      val monitor = tile.getProxy.getStorage.getMEMonitor(tp)
      if (monitor != null) {
        val storageList = monitor.getStorageList
        val it = storageList.iterator()

        while (it.hasNext) {
          val s = it.next().asInstanceOf[AEStack]
          if (s.isCraftable) {
            val c = asCraft(s, tile)
            if (matches(convert(c, tile), filter)) {
              builder += new Craftable(tile, c)
            }
          }
        }
      }
    }

    result(builder.result())
  }

  @Callback(doc = "function([filter:table]):table -- Get a list of the stored items in the network.")
  def getItemsInNetwork(context: Context, args: Arguments): Array[AnyRef] = {
    val filter = args.optTable(0, Map.empty[AnyRef, AnyRef]).collect {
      case (key: String, value: AnyRef) => (key, value)
    }
    result(allItems
      .view
      .map(item => convert(item, tile))
      .filter(hash => matches(hash, filter))
      .toArray)
  }

  @Callback(doc = "function(name:string|id:number[, damage:number[, nbt:string]]):table -- Retrieves the stored item in the network by its unlocalized name.")
  def getItemInNetwork(context: Context, args: Arguments): Array[AnyRef] = {
    val item = if (args.isString(0)) Item.itemRegistry.getObject(args.checkString(0))
    else Item.itemRegistry.getObjectById(args.checkInteger(0))
    if (item == null) {
      return result(null)
    }

    val itemStack = new ItemStack(item.asInstanceOf[Item])
    itemStack.setItemDamage(args.optInteger(1, 0))

    // The obfuscated method turns a json string into an NBTBase.
    val nbtBase = JsonToNBT.func_150315_a(args.optString(2, "{}"))
    itemStack.setTagCompound(nbtBase.asInstanceOf[NBTTagCompound])

    val aeItemStack = AEItemStack.create(itemStack)
    val availableItem = getAvailableItem(aeItemStack, IterationCounter.fetchNewId())
    result(if (availableItem != null) convert(availableItem, tile) else null)
  }

  @Callback(doc = "function(filter:table):table -- Get a list of the stored items in the network matching the filter. Filter is an Array of Item IDs")
  def getItemsInNetworkById(context: Context, args: Arguments): Array[AnyRef] = {
    val table = args.checkTable(0)

    val itemFilterSet = mutable.LinkedHashSet.empty[Item]
    for (i <- 0 until table.size()) {
      table.get(i + 1) match {
        case itemNumberId: Number => itemFilterSet += Item.itemRegistry.getObjectById(itemNumberId.intValue()).asInstanceOf[Item]
        case itemStringId: String => itemFilterSet += Item.itemRegistry.getObject(itemStringId).asInstanceOf[Item]
        case other: Any => throw new IllegalArgumentException(s"bad argument in filter table at index ${i + 1} (number or string expected)")
      }
    }
    result(allItems
      .view
      .filter(item => itemFilterSet.contains(item.getItem))
      .map(item => convert(item, tile))
      .toArray)
  }

  @Callback(doc = "function([name:string]):table -- Get a list of the stored fluids in the network.")
  def getFluidInNetwork(context: Context, args: Arguments): Array[AnyRef] = {
    FluidRegistry.getFluid(args.checkString(0)) match {
      case null => result(null)
      case fluid =>
        getAvailableFluid(AEFluidStack.create(new FluidStack(fluid, 0))) match {
          case null => result(null)
          case fluid => result(convert(fluid, tile))
        }
    }
  }

  @Callback(doc = "function():userdata -- Get an iterator object for the list of the items in the network. ")
  def allItems(context: Context, args: Arguments): Array[AnyRef] = {
    result(new ItemNetworkContents(tile))
  }

  @Callback(doc = "function(filter:table, dbAddress:string[, startSlot:number[, count:number]]): Boolean -- Store items in the network matching the specified filter in the database with the specified address.")
  def store(context: Context, args: Arguments): Array[AnyRef] = {
    val filter = args.checkTable(0).collect {
      case (key: String, value: AnyRef) => (key, value)
    }
    DatabaseAccess.withDatabase(node, args.checkString(1), database => {
      val items = allItems
        .collect { case aeItem if matches(convert(aeItem, tile), filter) => aePotential(aeItem, tile) }.toArray
      val offset = args.optSlot(database.data, 2, 0)
      val count = args.optInteger(3, Int.MaxValue) min (database.size - offset) min items.length
      var slot = offset
      for (i <- 0 until count) {
        val stack = Option(items(i)).map(_.asInstanceOf[IAEItemStack].getItemStack.copy()).orNull
        while (database.getStackInSlot(slot) != null && slot < database.size) slot += 1
        if (database.getStackInSlot(slot) == null) {
          database.setStackInSlot(slot, stack)
        }
      }
      result(true)
    })
  }

  private def isFluidVisible(stack: IAEFluidStack) =
    if (Mods.ExtraCells.isAvailable) ECUtil.canSeeFluidInNetwork(stack)
    else if (Mods.Ae2Fc.isAvailable) Ae2FcUtil.canSeeFluidInNetwork(stack)
    else true

  @Callback(doc = "function():table -- Get a list of the stored fluids in the network.")
  def getFluidsInNetwork(context: Context, args: Arguments): Array[AnyRef] =
    result(allFluids
      .view
      .filter(isFluidVisible)
      .map(fs => convert(fs, tile))
      .toArray)

  @Callback(doc = "function():number -- Get the average power injection into the network.")
  def getAvgPowerInjection(context: Context, args: Arguments): Array[AnyRef] =
    result(tile.getProxy.getEnergy.getAvgPowerInjection)

  @Callback(doc = "function():number -- Get the average power usage of the network.")
  def getAvgPowerUsage(context: Context, args: Arguments): Array[AnyRef] =
    result(tile.getProxy.getEnergy.getAvgPowerUsage)

  @Callback(doc = "function():number -- Get the idle power usage of the network.")
  def getIdlePowerUsage(context: Context, args: Arguments): Array[AnyRef] =
    result(tile.getProxy.getEnergy.getIdlePowerUsage)

  @Callback(doc = "function():number -- Get the maximum stored power in the network.")
  def getMaxStoredPower(context: Context, args: Arguments): Array[AnyRef] =
    result(tile.getProxy.getEnergy.getMaxStoredPower)

  @Callback(doc = "function():number -- Get the stored power in the network. ")
  def getStoredPower(context: Context, args: Arguments): Array[AnyRef] =
    result(tile.getProxy.getEnergy.getStoredPower)

  private def matches(stack: java.util.HashMap[String, AnyRef], filter: scala.collection.mutable.Map[String, AnyRef]): Boolean = {
    if (stack == null) return false
    filter.forall {
      case (key: String, value: AnyRef) =>
        val stack_value = stack.get(key)
        if (stack_value == null) false
        else (value, stack_value) match {
          case (number: Number, stack_number: Number) => number.intValue() == stack_number.intValue()
          case (arr: Array[Byte], stack_arr: Array[Byte]) => arr.sameElements(stack_arr)
          case (str: String, stack_arr: Array[Byte]) => str.equals(stack_arr.mkString)
          case (_, _) => value.toString.equals(stack_value.toString)
        }
    }
  }

  private val itemSubscription: SubscriptionBase[IAEItemStack] = new SubscriptionBase[IAEItemStack] {
    implicit val tag: ClassTag[IAEItemStack] = ClassTag(classOf[IAEItemStack])

    override def event_name: String = "network_item_changed"

    override def tile: TileEntity with IGridProxyable = NetworkControl.this.tile

    override def node: Node = NetworkControl.this.node
  }

  private val fluidSubscription: SubscriptionBase[IAEFluidStack] = new SubscriptionBase[IAEFluidStack] {
    implicit val tag: ClassTag[IAEFluidStack] = ClassTag(classOf[IAEFluidStack])

    override def event_name: String = "network_fluid_changed"

    override def tile: TileEntity with IGridProxyable = NetworkControl.this.tile

    override def node: Node = NetworkControl.this.node
  }

  abstract override def load(nbt: NBTTagCompound): Unit = {
    super.load(nbt)
    itemSubscription.load(nbt)
    fluidSubscription.load(nbt)
  }

  abstract override def save(nbt: NBTTagCompound): Unit = {
    super.save(nbt)
    itemSubscription.save(nbt)
    fluidSubscription.save(nbt)
  }

  abstract override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    itemSubscription.setSubscribe(false)
    fluidSubscription.setSubscribe(false)
  }

  @Callback(doc = """function(enabled: bool): nil -- Enable or disable subscription to the "network_fluid_changed" event.""")
  def setFluidEventSubscription(context: Context, args: Arguments): Array[AnyRef] = {
    fluidSubscription.setSubscribe(args.checkBoolean(0))
    null
  }

  @Callback(doc = """function(): boolean -- Returns whether the "network_fluid_changed" event subscription is currently enabled.""", direct = true)
  def isFluidEventSubscription(context: Context, args: Arguments): Array[AnyRef] = {
    result(fluidSubscription.getSubscribe)
  }

  @Callback(doc = """function(enabled: bool): nil -- Enable or disable subscription to the "network_item_changed" event.""")
  def setItemEventSubscription(context: Context, args: Arguments): Array[AnyRef] = {
    itemSubscription.setSubscribe(args.checkBoolean(0))
    null
  }

  @Callback(doc = """function(): boolean -- Returns whether the "network_item_changed" event subscription is currently enabled.""", direct = true)
  def isItemEventSubscription(context: Context, args: Arguments): Array[AnyRef] = {
    result(itemSubscription.getSubscribe)
  }
}

object NetworkControl extends AETypes {

  //noinspection ScalaUnusedSymbol
  private class Craftable(var controller: TileEntity with IGridProxyable with IActionHost, var stack: AEStack) extends AbstractValue with ICraftingRequester {
    def this() = this(null, null)

    private val links = mutable.Set.empty[ICraftingLink]

    // ----------------------------------------------------------------------- //

    //noinspection RedundantCollectionConversion
    override def getRequestedJobs: ImmutableSet[ICraftingLink] = ImmutableSet.copyOf(links.toIterable)

    override def jobStateChange(link: ICraftingLink): Unit = {
      links -= link
    }

    override def injectCraftedItems(link: ICraftingLink, stack: IAEStack[_], p3: Actionable): IAEStack[_] = stack

    override def getActionableNode: IGridNode = controller.getActionableNode

    override def getCableConnectionType(side: ForgeDirection): AECableType = controller.getCableConnectionType(side)

    override def securityBreak(): Unit = controller.securityBreak()

    override def getGridNode(side: ForgeDirection): IGridNode = controller.getGridNode(side)

    // ----------------------------------------------------------------------- //

    @Callback(doc = "function():table -- Returns the item stack representation of the crafting result.")
    def getStack(context: Context, args: Arguments): Array[AnyRef] = result(stack)

    @Callback(doc = "function([amount:int[, prioritizePower:boolean[, cpuName:string]]]):userdata -- Requests the item to be crafted, returning an object that allows tracking the crafting status.")
    def request(context: Context, args: Arguments): Array[AnyRef] = {
      if (controller == null || controller.isInvalid) {
        return result(Unit, "no controller")
      }

      val count = args.optInteger(0, 1)
      val request = stack.copy
      request.setStackSize(count)

      val craftingGrid = controller.getProxy.getCrafting
      val source = new MachineSource(controller)
      val future = craftingGrid.beginCraftingJob(controller.getWorldObj, controller.getProxy.getGrid, source, request, null)
      val prioritizePower = args.optBoolean(1, true)
      val cpuName = args.optString(2, "")
      val cpu = if (cpuName.nonEmpty) {
        controller.getProxy.getCrafting.getCpus.collectFirst({
          case c if cpuName.equals(c.getName) => c
        }).orNull
      } else null

      val status = new CraftingStatus()
      Future {
        try {
          while (!future.isDone) {
            Thread.sleep(10)
          }

          val job = future.get()

          if (future.isCancelled) {
            status.fail("missing resources")
          } else {
            EventHandler.scheduleServer(() => {
              val link = craftingGrid.submitJob(job, Craftable.this, cpu, prioritizePower, source)
              if (link != null) {
                status.setLink(link)
                links += link
              }
              else {
                status.fail("missing resources?")
              }
            })
          }
        }
        catch {
          case e: Exception =>
            OpenComputers.log.debug("Error submitting job to AE2.", e)
            status.fail(e.toString)
        }
      }

      result(status)
    }

    // ----------------------------------------------------------------------- //

    override def load(nbt: NBTTagCompound): Unit = {
      super.load(nbt)
      if (nbt.hasKey("StackType")) {
        stack = Platform.readStackNBT(nbt, true).asInstanceOf[AEStack]
      }
      else {
        stack = AEItemStack.loadItemStackFromNBT(nbt)
      }
      loadController(nbt, c => controller = c)
      links ++= nbt.getTagList("links", NBT.TAG_COMPOUND).map(
        (nbt: NBTTagCompound) => AEApi.instance.storage.loadCraftingLink(nbt, this))
    }

    override def save(nbt: NBTTagCompound): Unit = {
      super.save(nbt)
      Platform.writeStackNBT(stack, nbt, true)
      saveController(controller, nbt)
      nbt.setNewTagList("links", links.map(link => link.writeToNBT _))
    }
  }

  //noinspection ScalaUnusedSymbol
  private class Cpu(var controller: TileEntity with IGridProxyable, var index: Int, var cpu: CraftingCPUCluster) extends AbstractValue {
    def this() = this(null, 0, null)

    @Callback(doc = "function():boolean -- Cancel this CPU current crafting job.")
    def cancel(context: Context, args: Arguments): Array[AnyRef] = {
      if (!getCpu.isBusy)
        result(false)
      else {
        getCpu.cancel()
        result(true)
      }
    }

    @Callback(doc = "function():boolean -- Is cpu active?")
    def isActive(context: Context, args: Arguments): Array[AnyRef] = {
      result(getCpu.isActive)
    }

    @Callback(doc = "function():boolean -- Is cpu busy?")
    def isBusy(context: Context, args: Arguments): Array[AnyRef] = {
      result(getCpu.isBusy)
    }

    @Callback(doc = "function():table -- Get currently crafted items.")
    def activeItems(context: Context, args: Arguments): Array[AnyRef] = {
      val list = AEApi.instance.storage.createAEStackList().asInstanceOf[IItemList[AEStack]]
      AE2Bridge.getModernListOfItem(getCpu, list, CraftingItemList.ACTIVE)
      result(list.map(item => convert(item, controller)).toArray)
    }

    @Callback(doc = "function():table -- Get pending items.")
    def pendingItems(context: Context, args: Arguments): Array[AnyRef] = {
      val list = AEApi.instance.storage.createAEStackList().asInstanceOf[IItemList[AEStack]]
      AE2Bridge.getModernListOfItem(getCpu, list, CraftingItemList.PENDING)
      result(list.map(item => convert(item, controller)).toArray)
    }

    @Callback(doc = "function():table -- Get stored items.")
    def storedItems(context: Context, args: Arguments): Array[AnyRef] = {
      val list = AEApi.instance.storage.createAEStackList().asInstanceOf[IItemList[AEStack]]
      AE2Bridge.getModernListOfItem(getCpu, list, CraftingItemList.STORAGE)
      result(list.map(item => convert(item, controller)).toArray)
    }

    @Callback(doc = "function():table -- Get crafting final output.")
    def finalOutput(context: Context, args: Arguments): Array[AnyRef] = {
      val monitor = getCpu.getTiles.find(t => t.isInstanceOf[TileCraftingMonitorTile])
      if (monitor.isEmpty)
        result(null, "No crafting monitor")
      else {
        val aeStack = monitor.get.asInstanceOf[TileCraftingMonitorTile].getJobProgress
        if (aeStack == null)
          result(null, "Nothing is crafted")
        else
          result(aeStack)
      }
    }

    private def getCpu = {
      if (cpu == null && controller != null) {
        var i = 0
        for (c <- controller.getProxy.getCrafting.getCpus) {
          if (i == index) {
            cpu = c.asInstanceOf[CraftingCPUCluster]
          }
          i += 1
        }
      }
      if (cpu == null)
        throw new Exception("Broken CPU cluster")
      cpu
    }

    override def save(nbt: NBTTagCompound): Unit = {
      super.save(nbt)
      nbt.setInteger("index", index)
      saveController(controller, nbt)
    }

    override def load(nbt: NBTTagCompound): Unit = {
      super.load(nbt)
      index = nbt.getInteger("index")
      loadController(nbt, c => controller = c)
    }
  }

  //noinspection ScalaUnusedSymbol
  private class CraftingStatus extends AbstractValue {
    private var isComputing = true
    private var link: Option[ICraftingLink] = None
    private var failed = false
    private var reason = "no link"

    def setLink(value: ICraftingLink): Unit = {
      isComputing = false
      link = Option(value)
    }

    def fail(reason: String): Unit = {
      isComputing = false
      failed = true
      this.reason = s"request failed ($reason)"
    }

    @Callback(doc = "function():boolean -- Get whether the crafting request is currently computing.")
    def isComputing(context: Context, args: Arguments): Array[AnyRef] = result(isComputing)

    @Callback(doc = "function():boolean -- Get whether the crafting request has failed.")
    def hasFailed(context: Context, args: Arguments): Array[AnyRef] = result(failed, reason)

    @Callback(doc = "function():boolean -- Get whether the crafting request has been canceled.")
    def isCanceled(context: Context, args: Arguments): Array[AnyRef] = {
      if (isComputing) return result(false, "computing")
      link.fold(result(failed, reason))(l => result(l.isCanceled))
    }

    @Callback(doc = "function():boolean -- Get whether the crafting request is done.")
    def isDone(context: Context, args: Arguments): Array[AnyRef] = {
      if (isComputing) return result(false, "computing")
      link.fold(result(!failed, reason))(l => result(l.isDone))
    }

    override def save(nbt: NBTTagCompound): Unit = {
      super.save(nbt)
      failed = link.fold(true)(!_.isDone)
      nbt.setBoolean("failed", failed)
      if (failed && reason != null) {
        nbt.setString("reason", reason)
      }
    }

    override def load(nbt: NBTTagCompound): Unit = {
      super.load(nbt)
      isComputing = false
      failed = nbt.getBoolean("failed")
      if (failed && nbt.hasKey("reason")) {
        reason = nbt.getString("reason")
      }
    }
  }

  //noinspection ConvertNullInitializerToUnderscore
  // Note: only IAEItemStack use the ConcurrentSkipListSet, and this has performance issues.
  // Because AE2 storage doesn't support indexed access, but OC requires it for iteration.
  // Creating a full snapshot for large networks to bridge this gap will introduce significant memory overhead and server-side lag.
  private abstract class NetworkContents[T <: IAEStack[T] : ClassTag](var controller: TileEntity with IGridProxyable with IActionHost) extends AbstractValue with IMEMonitorHandlerReceiver[T] {
    def this() = this(null)

    private def getMonitor: Option[IMEMonitor[T]] = AEUtil.getMonitor[T](controller)

    private var items: IItemList[T] = null
    private var itemIterator: java.util.Iterator[T] = null
    private var index = 0

    private def updateItems(): Unit = {
      val list: Option[IItemList[T]] = getMonitor.map(_.getStorageList)
      items = list.orNull
      if (items != null) {
        itemIterator = items.toSorted.iterator
        itemIterator.drop(index)
      }
    }

    updateItems()
    getMonitor.foreach(_.addListener(this, null))

    override def call(context: Context, arguments: Arguments): Array[AnyRef] = {
      if (controller == null || itemIterator == null)
        return null
      if (!itemIterator.hasNext)
        return null
      index += 1
      result(itemIterator.next())
    }

    override def load(nbt: NBTTagCompound): Unit = {
      super.load(nbt)
      index = nbt.getInteger("index")
      loadController(nbt, c => controller = c)
    }

    override def save(nbt: NBTTagCompound): Unit = {
      super.save(nbt)
      nbt.setInteger("index", index)
      saveController(controller, nbt)
    }

    private var valid = true

    override def dispose(context: Context): Unit = {
      valid = false
    }

    override def isValid(verificationToken: Any): Boolean = valid

    override def onListUpdate(): Unit = {
      updateItems()
    }

    override def postChange(monitor: IBaseMonitor[T], change: lang.Iterable[T], actionSource: BaseActionSource): Unit = {
      updateItems()
    }

    override def toString = "{IAEStack Array}"
  }

  private def asCraft(stack: AEStack, tile: TileEntity with IGridProxyable): AEStack = {
    val outputOpt = tile.getProxy.getCrafting.getCraftingFor(stack, null, 0, tile.getWorldObj).view.map(a => a.getAEOutputs.find(_.isSameType(stack)).get).headOption
    outputOpt match {
      case Some(output) => output.asInstanceOf[AEStack]
      case None =>
        val result = stack.copy()
        result.setStackSize(0)
        result
    }
  }

  private def aePotential(aeItem: AEStack, tile: TileEntity with IGridProxyable): AEStack = {
    if (aeItem.getStackSize > 0 || !aeItem.isCraftable)
      aeItem
    else
      asCraft(aeItem, tile)
  }

  private def hashConvert(value: java.util.HashMap[_, _]) = {
    val hash = new java.util.HashMap[String, AnyRef]
    value.collect { case (k: String, v: AnyRef) => hash += k -> v }
    hash
  }

  def convert(aeItem: AEStack, tile: TileEntity with IGridProxyable): java.util.HashMap[String, AnyRef] = {
    val potentialItem = aePotential(aeItem, tile)
    val result = Registry.convert(Array[AnyRef](potentialItem))
      .collect { case hash: java.util.HashMap[_, _] => hashConvert(hash) }
    if (result.length > 0) {
      val hash = result(0)
      // it would have been nice to put these fields in a registry convert
      // but the potential ae item needs the tile and position data
      hash.update("size", Long.box(aeItem.getStackSize))
      hash.update("isCraftable", Boolean.box(aeItem.isCraftable))
      return hash
    }
    null
  }

  private def loadController(nbt: NBTTagCompound, f: TileEntity with IGridProxyable with IActionHost => Unit): Unit = {
    if (nbt.hasKey("dimension")) {
      val dimension = nbt.getInteger("dimension")
      val x = nbt.getInteger("x")
      val y = nbt.getInteger("y")
      val z = nbt.getInteger("z")
      EventHandler.scheduleServer(() => {
        val world = DimensionManager.getWorld(dimension)
        val tileEntity = world.getTileEntity(x, y, z)
        if (tileEntity != null && tileEntity.isInstanceOf[TileEntity with IGridProxyable with IActionHost]) {
          f(tileEntity.asInstanceOf[TileEntity with IGridProxyable with IActionHost])
        }
      })
    }
  }

  private def saveController(controller: TileEntity, nbt: NBTTagCompound): Unit = {
    if (controller != null && !controller.isInvalid) {
      nbt.setInteger("dimension", controller.getWorldObj.provider.dimensionId)
      nbt.setInteger("x", controller.xCoord)
      nbt.setInteger("y", controller.yCoord)
      nbt.setInteger("z", controller.zCoord)
    }
  }


  private class ItemNetworkContents(controller: TileEntity with IGridProxyable with IActionHost) extends NetworkContents[IAEItemStack](controller) {
    def this() = this(null)
  }
}
