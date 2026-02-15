package li.cil.oc.integration.thaumicenergistics

import appeng.api.networking.security.IActionHost
import appeng.me.helpers.IGridProxyable
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.Node
import li.cil.oc.integration.appeng.NetworkControl.NetworkContents
import li.cil.oc.util.ResultWrapper._
import net.minecraft.tileentity.TileEntity
import thaumicenergistics.common.storage.AEEssentiaStack
import thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

// Note to self: this class is used by ExtraCells (and potentially others), do not rename / drastically change it.
trait NetworkControl[AETile >: Null <: TileEntity with IGridProxyable with IActionHost] {
  def tile: AETile

  def node: Node

  @Callback(doc = "function():table -- Get a list of the stored essentia in the network.")
  def getEssentiaInNetwork(context: Context, args: Arguments): Array[AnyRef] =
    result(tile.getProxy.getStorage.getMEMonitor(ESSENTIA_STACK_TYPE).getStorageList.asScala.toArray)

  @Callback(doc = "function([subscribe:bool]):userdata -- Get an iterator object for the list of the essentia in the network. [Event: network_essentia_changed]")
  def allEssentia(context: Context, args: Arguments): Array[AnyRef] = {
    result(new EssentiaNetworkContents(tile, node, args.optBoolean(0, false)))
  }

  private class EssentiaNetworkContents(controller: TileEntity with IGridProxyable with IActionHost, node: Node, subscribe: Boolean) extends NetworkContents[AEEssentiaStack](controller, node, subscribe) {
    def this() = this(null, null, false)

    override val event_name: String = "network_essentia_changed"
  }
}
