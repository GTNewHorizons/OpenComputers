package li.cil.oc.common.item.data

import li.cil.oc.common.item.data.TransposerData.FLUID_TRANSFER_RATE
import li.cil.oc.{Constants, Settings}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

class TransposerData(itemName: String = Constants.BlockName.Transposer) extends ItemData(itemName) {
  def this(stack: ItemStack) {
    this()
    load(stack)
  }

  var fluidTransferRate: Int = Settings.get.transposerFluidTransferRate

  override def load(nbt: NBTTagCompound) {
    if (nbt.hasKey(FLUID_TRANSFER_RATE)) {
      fluidTransferRate = nbt.getInteger(FLUID_TRANSFER_RATE)
    }
  }

  override def save(nbt: NBTTagCompound) {
    nbt.setInteger(FLUID_TRANSFER_RATE, fluidTransferRate)
  }

  def copyItemStack() = {
    val stack = createItemStack()
    val newInfo = new TransposerData(stack)
    newInfo.save(stack)
    stack
  }
}

object TransposerData {
  val FLUID_TRANSFER_RATE: String = Settings.namespace + "fluidTransferRate"
}
