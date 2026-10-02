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

  var fluidTransferRate: Long = Settings.get.transposerFluidTransferRate

  def load(nbt: NBTTagCompound): Unit = {
    if (nbt.hasKey(FLUID_TRANSFER_RATE)) {
      fluidTransferRate = nbt.getLong(FLUID_TRANSFER_RATE)
    }
  }

  def save(nbt: NBTTagCompound): Unit = {
    nbt.setLong(FLUID_TRANSFER_RATE, fluidTransferRate)
  }

  def copyItemStack(): ItemStack = {
    val stack = createItemStack()
    val newInfo = new TransposerData(stack)
    newInfo.save(stack)
    stack
  }
}

object TransposerData {
  val FLUID_TRANSFER_RATE: String = Settings.namespace + "fluidTransferRate"

  private val tierNames = Map(
    10240L -> "HV",
    40960L -> "EV",
    163840L -> "IV",
    655360L -> "LuV",
    2621440L -> "ZPM",
    10485760L -> "UV",
    41943040L -> "UHV",
    167772160L -> "UEV",
    671088640L -> "UIV",
    2684354560L -> "UMV",
    10737418240L -> "UXV"
  )

  def tierName(fluidTransferRate: Long): Option[String] = tierNames.get(fluidTransferRate)
}
