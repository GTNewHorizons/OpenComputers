package li.cil.oc.integration.thaumicenergistics

import appeng.api.networking.security.IActionHost
import appeng.api.storage.data.IItemList
import appeng.me.helpers.IGridProxyable
import li.cil.oc.api.Persistable
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{ManagedEnvironment, Node}
import li.cil.oc.integration.appeng.internal.SubscriptionBase
import li.cil.oc.util.ResultWrapper._
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import thaumicenergistics.common.storage.AEEssentiaStack
import thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.reflect.ClassTag

// Note to self: this class is used by ExtraCells (and potentially others), do not rename / drastically change it.
trait NetworkControl[AETile >: Null <: TileEntity with IGridProxyable with IActionHost] extends Persistable with ManagedEnvironment {
  def tile: AETile

  def node: Node

  @Callback(doc = "function([filter: string]):table -- Get a list of the stored essentia in the network.")
  def getEssentiaInNetwork(context: Context, args: Arguments): Array[AnyRef] = {
    val monitor = tile.getProxy.getStorage.getMEMonitor(ESSENTIA_STACK_TYPE)
    if (monitor == null) {
      result(null)
    }
    else {
      val list = monitor.getStorageList.asInstanceOf[IItemList[AEEssentiaStack]].asScala
      val filtered = if (args.isString(0)) {
        val filter = args.checkString(0)
        list.filter(_.getUnlocalizedName == filter)
      } else {
        list
      }
      result(filtered.toArray)
    }
  }

  private val essentiaSubscription: SubscriptionBase[AEEssentiaStack] = new SubscriptionBase[AEEssentiaStack] {
    implicit val tag: ClassTag[AEEssentiaStack] = ClassTag(classOf[AEEssentiaStack])

    override def event_name: String = "network_essentia_changed"

    override def tile: TileEntity with IGridProxyable = NetworkControl.this.tile

    override def node: Node = NetworkControl.this.node
  }

  abstract override def load(nbt: NBTTagCompound): Unit = {
    super.load(nbt)
    essentiaSubscription.load(nbt)
  }

  abstract override def save(nbt: NBTTagCompound): Unit = {
    super.save(nbt)
    essentiaSubscription.save(nbt)
  }

  abstract override def canUpdate: Boolean = {
    val flag = super.canUpdate
    flag || essentiaSubscription.canUpdate
  }

  abstract override def update(): Unit = {
    super.update()
    essentiaSubscription.update()
  }

  @Callback(doc = """function(enabled: bool): nil -- Enable or disable subscription to the "network_essentia_changed" event.""")
  def setEssentiaEventSubscription(context: Context, args: Arguments): Array[AnyRef] = {
    essentiaSubscription.setSubscribe(args.checkBoolean(0))
    null
  }

  @Callback(doc = """function(): boolean -- Returns whether the "network_essentia_changed" event subscription is currently enabled.""", direct = true)
  def isEssentiaEventSubscription(context: Context, args: Arguments): Array[AnyRef] = {
    result(essentiaSubscription.getSubscribe)
  }
}
