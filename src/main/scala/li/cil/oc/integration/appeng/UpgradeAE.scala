package li.cil.oc.integration.appeng

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.implementations.tiles.IWirelessAccessPoint
import appeng.api.networking.security.MachineSource
import appeng.api.networking.storage.IStorageGrid
import appeng.api.networking.{IGrid, IGridHost, IGridNode}
import appeng.api.storage.IMEMonitor
import appeng.api.storage.data.{IAEFluidStack, IAEItemStack}
import appeng.api.util.WorldCoord
import appeng.tile.misc.TileSecurity
import li.cil.oc.Constants
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.internal.{Agent, Drone, Robot}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network._
import li.cil.oc.api.prefab.ManagedEnvironment
import li.cil.oc.common.item.Delegator
import li.cil.oc.integration.appeng
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids.FluidContainerRegistry

import java.util
import scala.collection.JavaConversions._

class UpgradeAE(val host: EnvironmentHost, val tier: Int) extends ManagedEnvironment with appeng.NetworkControl[TileSecurity] with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network)
    .withConnector()
    .withComponent("upgrade_me", Visibility.Neighbors)
    .create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Robot ME upgrade",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> ""
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  var isActive = false

  val agent: Agent = host.asInstanceOf[Agent]

  def getComponent: ItemStack = host match {
    case robot: Robot => robot.getStackInSlot(robot.componentSlot(node.address))
    case drone: Drone =>
      for (i <- drone.internalComponents)
        Delegator.subItem(i) match {
          case Some(_: ItemUpgradeAE) => return i
          case _ =>
        }
      null
    case _ => null
  }


  def getSecurity: IGridHost = {
    if (host.world.isRemote) return null
    val component = getComponent
    val sec = AEApi.instance.registries.locatable
      .getLocatableBy(getAEKey(component))
      .asInstanceOf[IGridHost]
    if (checkRange(component, sec))
      sec
    else
      null
  }

  def checkRange(stack: ItemStack, sec: IGridHost): Boolean = {
    val result = for {
      sec_ <- Option(sec)
      gridNode <- Option(sec_.getGridNode(ForgeDirection.UNKNOWN))
      grid <- Option(gridNode.getGrid)
    } yield {
      val wirelessClass = AEApi.instance.definitions.blocks.wireless.maybeEntity.get
      val accessPoints = grid.getMachines(wirelessClass.asInstanceOf[Class[_ <: IGridHost]])
      stack.getItemDamage match {
        case 0 | 1 =>
          val gridBlock = gridNode.getGridBlock
          if (gridBlock == null || gridBlock.getLocation == null)
            false
          else {
            val rangeMultiplier = if (stack.getItemDamage == 0) 0.5 else 1
            accessPoints.exists { node =>
              val accessPoint = node.getMachine.asInstanceOf[IWirelessAccessPoint]
              val distance: WorldCoord = accessPoint.getLocation.subtract(
                agent.xPosition.toInt,
                agent.yPosition.toInt,
                agent.zPosition.toInt
              )
              val squaredDistance: Int =
                distance.x * distance.x + distance.y * distance.y + distance.z * distance.z
              val range = accessPoint.getRange * rangeMultiplier
              squaredDistance <= range * range
            }
          }
        case _ =>
          accessPoints.iterator.hasNext
      }
    }
    result.getOrElse(false)
  }

  def getGrid: IGrid = {
    if (host.world.isRemote) return null
    val securityTerminal = getSecurity
    if (securityTerminal == null) return null
    val gridNode: IGridNode =
      securityTerminal.getGridNode(ForgeDirection.UNKNOWN)
    if (gridNode == null) return null
    gridNode.getGrid
  }

  def getAEKey(stack: ItemStack): Long = {
    try {
      return WirelessHandlerUpgradeAE.instance.getEncryptionKey(stack).toLong
    } catch {
      case _: Throwable =>
    }
    0L
  }

  override def tile: TileSecurity = {
    val sec = getSecurity
    if (sec == null)
      throw new SecurityException("No Security Station")
    val node = sec.getGridNode(ForgeDirection.UNKNOWN)
    if (node == null) throw new SecurityException("No Security Station")
    val gridBlock = node.getGridBlock
    if (gridBlock == null) throw new SecurityException("No Security Station")
    val coord = gridBlock.getLocation
    if (coord == null) throw new SecurityException("No Security Station")
    val tileSecurity = coord.getWorld
      .getTileEntity(coord.x, coord.y, coord.z)
      .asInstanceOf[TileSecurity]
    if (tileSecurity == null) throw new SecurityException("No Security Station")
    tileSecurity
  }

  def getFluidInventory: IMEMonitor[IAEFluidStack] = {
    val grid = getGrid
    if (grid == null) return null
    val storage: IStorageGrid = grid.getCache(classOf[IStorageGrid])
    if (storage == null) return null
    storage.getFluidInventory
  }

  def getItemInventory: IMEMonitor[IAEItemStack] = {
    val grid = getGrid
    if (grid == null) return null
    val storage: IStorageGrid = grid.getCache(classOf[IStorageGrid])
    if (storage == null) return null
    storage.getItemInventory
  }

  @Callback(doc = """function([number:amount]):number -- Transfer selected items to your ae system.""")
  def sendItems(context: Context, args: Arguments): Array[AnyRef] = {
    val selected = agent.selectedSlot
    val invRobot = agent.mainInventory
    if (invRobot.getSizeInventory <= 0) return Array(0.underlying)
    val stack = invRobot.getStackInSlot(selected)
    val inv = getItemInventory
    if (stack == null || inv == null) return Array(0.underlying)
    val amount = Math.min(args.optInteger(0, 64), stack.stackSize)
    val stack2 = stack.copy
    stack2.stackSize = amount
    val notInjected = inv.injectItems(
      AEApi.instance.storage.createItemStack(stack2),
      Actionable.MODULATE,
      new MachineSource(tile)
    )
    if (notInjected == null) {
      stack.stackSize -= amount
      if (stack.stackSize <= 0)
        invRobot.setInventorySlotContents(selected, null)
      else
        invRobot.setInventorySlotContents(selected, stack)
      Array(amount.underlying)
    } else {
      stack.stackSize =
        stack.stackSize - amount + notInjected.getStackSize.toInt
      if (stack.stackSize <= 0)
        invRobot.setInventorySlotContents(selected, null)
      else
        invRobot.setInventorySlotContents(selected, stack)
      Array((stack2.stackSize - notInjected.getStackSize).underlying)
    }
  }

  @Callback(doc = """function(database:address, entry:number[, number:amount]):number OR function(detail:table):number -- Get items from your ae system.""")
  def requestItems(context: Context, args: Arguments): Array[AnyRef] = {
    val invRobot = agent.mainInventory
    if (invRobot.getSizeInventory <= 0) return result(0)
    val inv = getItemInventory
    if (inv == null) return result(0)

    val aestack = {
      if (args.isTable(0)) AEStackFactory.parse[IAEItemStack](args.checkTable(0))
      else AEApi.instance.storage.createItemStack(DatabaseAccess.getStackFromDatabase(node, args, 0))
    }
    if (aestack == null) return result(0)
    if (!args.isInteger(2)) aestack.setStackSize(64)
    val setStack = aestack.getItemStack
    val currentStackOpt = Option(invRobot.getStackInSlot(agent.selectedSlot))
    if (currentStackOpt.exists(!aestack.isSameType(_)))
      return result(0)
    val inSlot = currentStackOpt.map(_.stackSize).getOrElse(0)
    val maxSize = currentStackOpt.map(_.getMaxStackSize).getOrElse(64)
    aestack.setStackSize(Math.min(setStack.stackSize, maxSize - inSlot))
    val extracted = inv.extractItems(
      aestack,
      Actionable.MODULATE,
      new MachineSource(tile)
    )
    if (extracted == null) return result(0)
    val ext = extracted.getStackSize.toInt
    setStack.stackSize = inSlot + ext
    invRobot.setInventorySlotContents(agent.selectedSlot, setStack)
    result(ext)
  }

  @Callback(doc = """function([number:amount]):number -- Transfer selected fluid to your ae system.""")
  def sendFluids(context: Context, args: Arguments): Array[AnyRef] = {
    val selected = agent.selectedTank
    val tanks = agent.tank
    if (tanks.tankCount <= 0) return Array(0.underlying)
    val tank = tanks.getFluidTank(selected)
    val inv = getFluidInventory
    if (tank == null || inv == null || tank.getFluid == null)
      return Array(0.underlying)
    val amount =
      Math.min(args.optInteger(0, tank.getCapacity), tank.getFluidAmount)
    val fluid = tank.getFluid
    val fluid2 = fluid.copy
    fluid2.amount = amount
    val notInjected = inv.injectItems(
      AEApi.instance.storage.createFluidStack(fluid2),
      Actionable.MODULATE,
      new MachineSource(tile)
    )
    if (notInjected == null) {
      tank.drain(amount, true)
      Array(amount.underlying)
    } else {
      tank.drain(amount - notInjected.getStackSize.toInt, true)
      Array((amount - notInjected.getStackSize).underlying)
    }
  }

  @Callback(doc = """function(database:address, entry:number[, number:amount]):number OR function(detail:table):number -- Get fluid from your ae system.""")
  def requestFluids(context: Context, args: Arguments): Array[AnyRef] = {
    val tanks = agent.tank
    if (tanks.tankCount <= 0) return result(0)

    val tank = tanks.getFluidTank(agent.selectedTank)
    val inv = getFluidInventory
    if (tank == null || inv == null) return result(0)

    val aefluid = {
      if (args.isTable(0)) AEStackFactory.parse[IAEFluidStack](args.checkTable(0))
      else {
        val stack = DatabaseAccess.getStackFromDatabase(node, args, 0)
        val fluid = FluidContainerRegistry.getFluidForFilledItem(stack)
        AEApi.instance.storage.createFluidStack(fluid)
      }
    }
    if (aefluid == null) return result(0)
    if (!args.isInteger(2)) aefluid.setStackSize(FluidContainerRegistry.BUCKET_VOLUME)
    val amount = tank.fill(aefluid.getFluidStack, false)
    if (amount == 0) return result(0)
    aefluid.setStackSize(amount)
    val extracted = inv.extractItems(
      aefluid,
      Actionable.MODULATE,
      new MachineSource(tile)
    )
    if (extracted == null) return result(0)
    result(tank.fill(extracted.getFluidStack, true))
  }


  @Callback(doc = """function():boolean -- Return true if the card is linked to your ae network.""")
  def isLinked(context: Context, args: Arguments): Array[AnyRef] = {
    val isLinked = getGrid != null
    Array(boolean2Boolean(isLinked))
  }

  override def update(): Unit = {
    super.update()
    if (host.world.getTotalWorldTime % 10 == 0 && isActive) {
      if (!node.asInstanceOf[Connector].tryChangeBuffer(-getEnergy)) {
        isActive = false
      }
    }
  }

  private def getEnergy = {
    val c = getComponent
    if (c == null)
      .0
    else
      c.getItemDamage match {
        case 0 =>.6
        case 1 =>.3
        case _ =>.05
      }
  }

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "computer.stopped") {
      isActive = false
    } else if (message.name == "computer.started") {
      isActive = true
    }
  }

}
