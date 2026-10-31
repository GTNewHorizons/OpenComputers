package li.cil.oc.common.item.data

import li.cil.oc.common.item.data.TransposerData.{FLUID_TRANSFER_RATE, TIER}
import li.cil.oc.{Constants, Settings}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

class TransposerData(itemName: String = Constants.BlockName.Transposer) extends ItemData(itemName) {
  def this(stack: ItemStack) {
    this()
    load(stack)
  }

  var fluidTransferRate: Long = Settings.get.transposerFluidTransferRate
  var tier: Option[String] = None

  def load(nbt: NBTTagCompound): Unit = {
    if (nbt.hasKey(FLUID_TRANSFER_RATE)) {
      fluidTransferRate = nbt.getLong(FLUID_TRANSFER_RATE)
    }
    tier = Option(nbt.getString(TIER)).filter(_.nonEmpty)
  }

  def save(nbt: NBTTagCompound): Unit = {
    nbt.setLong(FLUID_TRANSFER_RATE, fluidTransferRate)
    tier.foreach(nbt.setString(TIER, _))
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
  val TIER: String = Settings.namespace + "tier"
}
