package li.cil.oc.integration.appeng

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.{IGrid, IGridNode}
import appeng.api.networking.crafting.{ICraftingGrid, ICraftingLink, ICraftingRequester}
import appeng.api.networking.security.IActionHost
import appeng.api.storage.data.IAEStack
import appeng.api.util.AECableType
import appeng.crafting.CraftingLink
import appeng.me.GridAccessException
import appeng.me.cache.CraftingGridCache
import appeng.me.cluster.implementations.CraftingCPUCluster
import appeng.me.helpers.IGridProxyable
import com.google.common.collect.ImmutableSet
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.WorldSavedData
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.common.util.ForgeDirection

import java.lang.ref.WeakReference

import scala.collection.convert.WrapAsScala._
import scala.collection.mutable

/**
  * AE2 only re-links jobs to requesters that are grid blocks, so after a
  * reload it cancels jobs requested from a computer. We hold the requester
  * here and re-link once the controller and CPU are back.
  */
object RequestedJobs {
  private final val DataName = "oc_ae2_requested_jobs"

  // Must be < 60 ticks: AE2 cancels unlinked jobs ~3s after the CPU loads.
  private final val CheckInterval = 20

  // Controller loaded, but no CPU on its grid runs the job.
  private final val GiveUpTicks = 20 * 60 * 5

  // Controller unloaded or off-grid. Often just an unvisited base, so be patient.
  private final val StaleTicks = 20L * 60 * 60 * 24 * 7

  // seenAt is saved at most this often, so it can lag by up to this much.
  private final val SeenResolution = 20L * 60 * 60

  private var ticks = 0

  type Controller = TileEntity with IGridProxyable with IActionHost

  sealed trait State

  case class Running(link: ICraftingLink) extends State

  /** Not tracked any more: the job ended, or was never tracked here. */
  case object Gone extends State

  /** World data can't be read right now. */
  case object Unavailable extends State

  def requester(controller: Controller): Requester = new Requester(ControllerPosition.of(controller))

  def add(requester: Requester, link: ICraftingLink): Unit = link match {
    case l: CraftingLink => data.foreach(d => {
      requester.link = l
      requester.seenAt = worldTime
      d.entries += l.getCraftingID -> requester
      d.markDirty()
    })
    case _ =>
  }

  def state(craftId: String): State = data.fold(Unavailable: State)(d =>
    d.entries.get(craftId).fold(Gone: State)(r => Running(r.link)))

  private def data: Option[Data] = Option(DimensionManager.getWorld(0)).map(world => {
    world.mapStorage.loadData(classOf[Data], DataName) match {
      case d: Data => d
      case _ =>
        val d = new Data(DataName)
        world.mapStorage.setData(DataName, d)
        d
    }
  })

  private def worldTime: Long = Option(DimensionManager.getWorld(0)).fold(0L)(_.getTotalWorldTime)

  @SubscribeEvent
  def onServerTick(e: ServerTickEvent): Unit = if (e.phase == TickEvent.Phase.END) {
    ticks += 1
    if (ticks % CheckInterval == 0) data.foreach(d => d.entries.values.toArray.foreach(check(d, _)))
  }

  private def check(d: Data, requester: Requester): Unit = {
    val link = requester.link
    if (link.isCanceled || link.isDone) {
      d.remove(requester)
      return
    }
    val now = worldTime
    def stale(): Unit = if (now - requester.seenAt > StaleTicks) d.cancel(requester)
    def seen(): Unit = if (now - requester.seenAt > SeenResolution) {
      requester.seenAt = now
      d.markDirty()
    }
    requester.find match {
      case ControllerMissing => d.cancel(requester)
      case ControllerUnloaded => stale()
      case ControllerLoaded(tile) => grid(tile) match {
        case None => stale()
        // Same grid instance means nothing reloaded since we last linked.
        case Some(grid) if grid eq requester.attachedTo.get => seen()
        case Some(grid) => grid.getCache[ICraftingGrid](classOf[ICraftingGrid]) match {
          case cache: CraftingGridCache =>
            val running = cache.getCpus.exists({
              case cpu: CraftingCPUCluster =>
                cpu.getLastCraftingLink != null && cpu.getLastCraftingLink.getCraftingID == link.getCraftingID
              case _ => false
            })
            if (running) {
              requester.missingTicks = 0
              seen()
              cache.addLink(link)
              requester.attachedTo = new WeakReference(grid)
            }
            else {
              requester.missingTicks += CheckInterval
              if (requester.missingTicks > GiveUpTicks) d.cancel(requester)
            }
          case _ =>
        }
      }
    }
  }

  private def grid(tile: Controller): Option[IGrid] =
    try Option(tile.getProxy.getGrid)
    catch {
      case _: GridAccessException => None
    }

  private sealed trait Lookup

  private case class ControllerLoaded(tile: Controller) extends Lookup

  private case object ControllerUnloaded extends Lookup

  private case object ControllerMissing extends Lookup

  class Requester(val position: ControllerPosition) extends ICraftingRequester {
    var link: CraftingLink = _
    var missingTicks = 0
    // World time the job was last seen running on its grid; persisted.
    var seenAt = 0L
    var attachedTo = new WeakReference[IGrid](null)

    // A stale tile after chunk unload isn't invalidated, but its proxy isn't ready.
    private var tile: Controller = _

    private[RequestedJobs] def find: Lookup = {
      if (tile != null && !tile.isInvalid && tile.getProxy.isReady) return ControllerLoaded(tile)
      tile = null
      val world = DimensionManager.getWorld(position.dimension)
      if (world == null || !world.blockExists(position.x, position.y, position.z)) ControllerUnloaded
      else position.controllerIn(world) match {
        case Some(t) =>
          tile = t
          ControllerLoaded(t)
        case _ => ControllerMissing
      }
    }

    def controller: Option[Controller] = find match {
      case ControllerLoaded(tile) => Some(tile)
      case _ => None
    }

    override def getRequestedJobs: ImmutableSet[ICraftingLink] =
      if (link == null) ImmutableSet.of() else ImmutableSet.of(link)

    override def jobStateChange(link: ICraftingLink): Unit = data.foreach(_.remove(this))

    override def injectCraftedItems(link: ICraftingLink, stack: IAEStack[_], mode: Actionable): IAEStack[_] = stack

    override def getActionableNode: IGridNode = controller.map(_.getActionableNode).orNull

    override def getGridNode(side: ForgeDirection): IGridNode = controller.map(_.getGridNode(side)).orNull

    override def getCableConnectionType(side: ForgeDirection): AECableType =
      controller.map(_.getCableConnectionType(side)).getOrElse(AECableType.NONE)

    override def securityBreak(): Unit = controller.foreach(_.securityBreak())
  }

  class Data(name: String) extends WorldSavedData(name) {
    val entries = mutable.LinkedHashMap.empty[String, Requester]

    private[RequestedJobs] def remove(requester: Requester): Unit = {
      val id = requester.link.getCraftingID
      if (entries.get(id).exists(_ eq requester)) {
        entries -= id
        markDirty()
      }
    }

    // Ended by us rather than AE2: cancel the link so whoever holds it sees that.
    private[RequestedJobs] def cancel(requester: Requester): Unit = {
      remove(requester)
      requester.link.cancel()
    }

    override def readFromNBT(nbt: NBTTagCompound): Unit = {
      entries.clear()
      val list = nbt.getTagList("jobs", NBT.TAG_COMPOUND)
      for (i <- 0 until list.tagCount) {
        val tag = list.getCompoundTagAt(i)
        ControllerPosition.load(tag).foreach(position => {
          val requester = new Requester(position)
          try {
            AEApi.instance.storage.loadCraftingLink(tag.getCompoundTag("link"), requester) match {
              case l: CraftingLink =>
                requester.link = l
                requester.seenAt = tag.getLong("seenAt")
                entries += l.getCraftingID -> requester
              case _ =>
            }
          }
          catch {
            case _: IllegalStateException => // not a requester's link
          }
        })
      }
    }

    override def writeToNBT(nbt: NBTTagCompound): Unit = {
      val list = new NBTTagList()
      for (requester <- entries.values) {
        val tag = new NBTTagCompound()
        requester.position.save(tag)
        tag.setLong("seenAt", requester.seenAt)
        val link = new NBTTagCompound()
        requester.link.writeToNBT(link)
        tag.setTag("link", link)
        list.appendTag(tag)
      }
      nbt.setTag("jobs", list)
    }
  }

}
