package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.common.item.data.TransposerData
import li.cil.oc.server.component
import li.cil.oc.util.ExtendedNBT.extendNBTTagCompound
import net.minecraft.nbt.NBTTagCompound

class Transposer extends traits.Environment {
  val info = new TransposerData()

  val transposer = new component.Transposer.Block(this)

  def node = transposer.node

  // Used on client side to check whether to render activity indicators.
  var lastOperation = 0L

  override def canUpdate = false

  override def readFromNBTForServer(nbt: NBTTagCompound) {
    super.readFromNBTForServer(nbt)
    info.load(nbt.getCompoundTag(Settings.namespace + "info"))
    transposer.load(nbt)
  }

  override def writeToNBTForServer(nbt: NBTTagCompound) {
    super.writeToNBTForServer(nbt)
    nbt.setNewCompoundTag(Settings.namespace + "info", info.save)
    transposer.save(nbt)
  }
}
