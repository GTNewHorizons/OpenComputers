package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import li.cil.oc.common.event.BlockChangeHandler
import li.cil.oc.common.event.BlockChangeHandler.ChangeListener
import li.cil.oc.server.network
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.Settings
import li.cil.oc.api
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.Vec3
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.convert.WrapAsJava._

/**
  * Mostly stolen from {@link li.cil.oc.common.tileentity.Adapter}
  *
  * In an adapter the MFU stays bound to the block a player linked it to. In a robot or drone it is a component that
  * the program can bind to blocks around it.
  *
  * @author Sangar, Vexatos
  */
class UpgradeMF(val host: EnvironmentHost, private var coord: Option[BlockPosition], private var dir: ForgeDirection) extends prefab.ManagedEnvironment with ChangeListener with DeviceInfo {
  private val mobile = host.isInstanceOf[internal.Robot] || host.isInstanceOf[internal.Drone]

  override val node =
    if (mobile) api.Network.newNode(this, Visibility.Network).
      withComponent("mfu", Visibility.Neighbors).
      withConnector().
      create()
    else api.Network.newNode(this, Visibility.None).
      withConnector().
      create()

  private var otherEnv: Option[api.network.Environment] = None
  private var otherDrv: Option[(ManagedEnvironment, api.driver.SidedBlock)] = None
  private var blockData: Option[BlockData] = None
  private var wasInRange = false

  override val canUpdate = true

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Bus,
    DeviceAttribute.Description -> "Remote Adapter",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.Scummtech,
    DeviceAttribute.Product -> "ERR NAME NOT FOUND"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  // ----------------------------------------------------------------------- //

  @Callback(doc = "function(dx:number, dy:number, dz:number[, side:number]):boolean[, string] -- Binds to the block at this offset from the robot in world coordinates (dx = 1 is one block east), as if an adapter touched its given side (default: the top).")
  def bind(context: Context, args: Arguments): Array[AnyRef] = {
    val (dx, dy, dz) = (args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
    if (dx == 0 && dy == 0 && dz == 0) return result(false, "cannot bind to itself")
    val side = if (args.count > 3) ForgeDirection.getOrientation(args.checkInteger(3)) else ForgeDirection.UP
    if (side == ForgeDirection.UNKNOWN) return result(false, "invalid side")
    val position = BlockPosition(host)
    val target = BlockPosition(position.x + dx, position.y + dy, position.z + dz, host.world)
    if (distance(target) > Settings.get.mfuRange) return result(false, "out of range")
    rebind(Some(target), side)
    if (otherDrv.isEmpty) {
      rebind(None, ForgeDirection.UNKNOWN)
      return result(false, "nothing to connect to")
    }
    result(true)
  }

  @Callback(doc = "function():boolean -- Whether the MFU is bound to a block.")
  def isBound(context: Context, args: Arguments): Array[AnyRef] = result(coord.isDefined)

  @Callback(doc = "function() -- Removes the binding.")
  def unbind(context: Context, args: Arguments): Array[AnyRef] = {
    rebind(None, ForgeDirection.UNKNOWN)
    null
  }

  @Callback(doc = "function():number, number, number, number, boolean -- The bound block's offset from the robot in world coordinates, the bound side and whether it is in range. Nothing when unbound.")
  def getBinding(context: Context, args: Arguments): Array[AnyRef] = coord match {
    case Some(target) =>
      val position = BlockPosition(host)
      result(target.x - position.x, target.y - position.y, target.z - position.z, dir.ordinal, inRange)
    case None => null
  }

  // ----------------------------------------------------------------------- //

  private def distance(target: BlockPosition) =
    target.toVec3.distanceTo(Vec3.createVectorHelper(host.xPosition, host.yPosition, host.zPosition))

  private def inRange = coord.exists(target => target.world.exists(_.provider.dimensionId == host.world.provider.dimensionId)
    && distance(target) <= Settings.get.mfuRange)

  private def rebind(target: Option[BlockPosition], side: ForgeDirection) {
    disconnect()
    BlockChangeHandler.removeListener(this)
    coord = target
    dir = side
    blockData = None
    coord.foreach(BlockChangeHandler.addListener(this, _))
    updateBoundState()
  }

  private def otherNode(tile: TileEntity, f: (Node) => Unit) {
    network.Network.getNetworkNode(tile, dir) match {
      case Some(otherNode) => f(otherNode)
      case _ => // Nothing to do here
    }
  }

  private def updateBoundState() {
    wasInRange = inRange
    if (mobile && coord.exists(target => !host.world.blockExists(target.x, target.y, target.z))) return
    if (node != null && node.network != null && wasInRange) {
      val target = coord.get
      host.world.getTileEntity(target) match {
        case env: TileEntity with api.network.Environment if !mobile =>
          otherEnv match {
            case Some(environment: TileEntity) =>
              otherNode(environment, node.disconnect)
              otherEnv = None
            case _ => // Nothing to do here.
          }
          otherEnv = Some(env)
          // Remove any driver that might be there.
          otherDrv match {
            case Some((environment, driver)) =>
              node.disconnect(environment.node)
              environment.save(blockData.get.data)
              Option(environment.node).foreach(_.remove())
              otherDrv = None
            case _ => // Nothing to do here.
          }
          otherNode(env, node.connect)
        case _ =>
          // Remove any environment that might have been there.
          otherEnv match {
            case Some(environment: TileEntity) =>
              otherNode(environment, node.disconnect)
              otherEnv = None
            case _ => // Nothing to do here.
          }
          val (world, x, y, z) = (target.world.get, target.x, target.y, target.z)
          Option(api.Driver.driverFor(world, x, y, z, dir)) match {
            case Some(newDriver) =>
              otherDrv match {
                case Some((oldEnvironment, driver)) =>
                  if (newDriver != driver) {
                    // This is... odd. Maybe moved by some other mod? First, clean up.
                    otherDrv = None
                    blockData = None
                    node.disconnect(oldEnvironment.node)

                    // Then rebuild - if we have something.
                    val environment = newDriver.createEnvironment(world, x, y, z, dir)
                    if (environment != null) {
                      otherDrv = Some((environment, newDriver))
                      blockData = Some(new BlockData(environment.getClass.getName, new NBTTagCompound()))
                      node.connect(environment.node)
                    }
                  } // else: the more things change, the more they stay the same.
                case _ =>
                  // A challenger appears. Maybe.
                  val environment = newDriver.createEnvironment(world, x, y, z, dir)
                  if (environment != null) {
                    otherDrv = Some((environment, newDriver))
                    blockData match {
                      case Some(data) if data.name == environment.getClass.getName =>
                        environment.load(data.data)
                      case _ =>
                    }
                    blockData = Some(new BlockData(environment.getClass.getName, new NBTTagCompound()))
                    node.connect(environment.node)
                  }
              }
            case _ => otherDrv match {
              case Some((environment, driver)) =>
                // We had something there, but it's gone now...
                node.disconnect(environment.node)
                environment.save(blockData.get.data)
                Option(environment.node).foreach(_.remove())
                otherDrv = None
              case _ => // Nothing before, nothing now.
            }
          }
      }
    }
  }

  private def disconnect() {
    otherEnv match {
      case Some(environment: TileEntity) =>
        otherNode(environment, node.disconnect)
        otherEnv = None
      case _ => // Nothing to do here.
    }
    otherDrv match {
      case Some((environment, driver)) =>
        node.disconnect(environment.node)
        environment.save(blockData.get.data)
        Option(environment.node).foreach(_.remove())
        otherDrv = None
      case _ => // Nothing to do here.
    }
  }

  override def onBlockChanged() = updateBoundState()

  override def update() {
    super.update()
    otherDrv match {
      case Some((env, drv)) if env.canUpdate => env.update()
      case _ => // No driver
    }
    if (host.world.getTotalWorldTime % Settings.get.tickFrequency == 0) {
      if (mobile && inRange != wasInRange) {
        if (inRange) {
          updateBoundState()
        } else {
          disconnect()
          wasInRange = false
        }
      } else if (mobile && wasInRange && otherDrv.isEmpty) {
        updateBoundState()
      }
      coord.foreach { target =>
        if ((!mobile || wasInRange) && !node.tryChangeBuffer(-Settings.get.mfuCost * Settings.get.tickFrequency * distance(target))) {
          disconnect()
        }
      }
    }
  }

  override def onConnect(node: Node) {
    super.onConnect(node)
    if (node == this.node) {
      coord.foreach(BlockChangeHandler.addListener(this, _))
      updateBoundState()
    }
  }

  override def onDisconnect(node: Node) {
    super.onDisconnect(node)
    otherEnv match {
      case Some(env: TileEntity) => otherNode(env, (otherNode) => if (node == otherNode) otherEnv = None)
      case _ => // No environment
    }
    otherDrv match {
      case Some((env, drv)) if node == env.node => otherDrv = None
      case _ => // No driver
    }
    if (node == this.node) {
      BlockChangeHandler.removeListener(this)
    }
  }

  override def load(nbt: NBTTagCompound) {
    super.load(nbt)
    if (mobile && nbt.hasKey(Settings.namespace + "mfu.coord")) {
      nbt.getIntArray(Settings.namespace + "mfu.coord") match {
        case Array(x, y, z, dim, side) =>
          coord = Option(DimensionManager.getWorld(dim)).map(BlockPosition(x, y, z, _))
          dir = ForgeDirection.getOrientation(side)
        case _ =>
          coord = None
          dir = ForgeDirection.UNKNOWN
      }
    }
    Option(nbt.getCompoundTag(Settings.namespace + "adapter.block")) match {
      case Some(blockNbt: NBTTagCompound) =>
        if (blockNbt.hasKey("name") && blockNbt.hasKey("data")) {
          blockData = Some(new BlockData(blockNbt.getString("name"), blockNbt.getCompoundTag("data")))
        }
      case _ => // Invalid tag
    }
  }

  override def save(nbt: NBTTagCompound) {
    super.save(nbt)
    if (mobile) {
      nbt.setIntArray(Settings.namespace + "mfu.coord", coord match {
        case Some(target) => Array(target.x, target.y, target.z, target.world.get.provider.dimensionId, dir.ordinal)
        case None => Array.empty[Int]
      })
    }
    val blockNbt = new NBTTagCompound()
    blockData.foreach({ data =>
      otherDrv.foreach(_._1.save(data.data))
      blockNbt.setString("name", data.name)
      blockNbt.setTag("data", data.data)
    })
    nbt.setTag(Settings.namespace + "adapter.block", blockNbt)
  }

  // ----------------------------------------------------------------------- //

  private class BlockData(val name: String, val data: NBTTagCompound)

}
