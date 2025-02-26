package li.cil.oc.common.item.data

import li.cil.oc.{Constants, Settings, api}
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT

class TransposerData(itemName: String = Constants.BlockName.Transposer) extends ItemData(itemName) {
  def this(stack: ItemStack) {
    this()
    load(stack)
  }

  private val FLUID_TRANSFER_RATE: String = Settings.namespace + "fluidTransferRate";

  var fluidTransferRate: Int = Settings.get.transposerFluidTransferRate

  override def load(nbt: NBTTagCompound) {
    if (nbt.hasKey(FLUID_TRANSFER_RATE)) {
      fluidTransferRate = nbt.getInteger(FLUID_TRANSFER_RATE)
    }
  }

  override def save(nbt: NBTTagCompound) {
    nbt.setInteger(Settings.namespace + "fluidTransferRate", fluidTransferRate)
  }

  def copyItemStack() = {
    val stack = createItemStack()
    val newInfo = new TransposerData(stack)
    newInfo.save(stack)
    stack
  }
}
