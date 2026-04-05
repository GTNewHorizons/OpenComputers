package li.cil.oc.integration.appeng.internal

import appeng.api.networking.security.BaseActionSource
import appeng.api.networking.storage.IBaseMonitor
import appeng.api.storage.IMEMonitorHandlerReceiver
import appeng.api.storage.data.IAEStack
import appeng.me.helpers.IGridProxyable
import li.cil.oc.api.Persistable
import li.cil.oc.api.network.Node
import li.cil.oc.common.EventHandler
import li.cil.oc.integration.appeng.AEUtil
import li.cil.oc.integration.appeng.NetworkControl.convert
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity

import java.lang
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag


trait SubscriptionBase[T <: IAEStack[T]] extends IMEMonitorHandlerReceiver[T] with Persistable {
  implicit def tag: ClassTag[T]

  def tile: TileEntity with IGridProxyable

  def node: Node

  def getSubscribe: Boolean = subscribe

  def setSubscribe(flag: Boolean): Unit = {
    if (subscribe == flag) return
    subscribe = flag
    updateSubscribe()
  }

  private def updateSubscribe(): Unit = {
    if (tile.isInvalid) return
    AEUtil.getMonitor[T](tile) match {
      case Some(inv) =>
        if (subscribe)
          inv.addListener(this, null)
        else
          inv.removeListener(this)
      case None => EventHandler.scheduleServer(() => {
        updateSubscribe()
      })
    }
  }

  private var subscribe = false

  def event_name: String

  override def postChange(monitor: IBaseMonitor[T], change: lang.Iterable[T], actionSource: BaseActionSource): Unit = {
    if (subscribe && tile != null) {
      val flatArgs = ArrayBuffer[Object](event_name)
      flatArgs ++= change.map(convert(_, tile))
      node.sendToReachable("computer.signal", flatArgs: _*)
    }
  }

  override def onListUpdate(): Unit = {}

  override def isValid(verificationToken: Any): Boolean = true

  override def load(nbt: NBTTagCompound): Unit = {
    setSubscribe(nbt.getBoolean(event_name))
  }

  override def save(nbt: NBTTagCompound): Unit = {
    nbt.setBoolean(event_name, subscribe)
  }
}