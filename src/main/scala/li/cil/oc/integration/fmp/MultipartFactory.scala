package li.cil.oc.integration.fmp

import codechicken.lib.data.MCDataInput
import codechicken.multipart.MultiPartRegistry
import codechicken.multipart.MultiPartRegistry.IPartFactory2
import codechicken.multipart.TMultiPart
import li.cil.oc.Settings
import net.minecraft.nbt.NBTTagCompound

object MultipartFactory extends IPartFactory2 {
  def init() {
    MultiPartRegistry.registerPartFactory(MultipartFactory,
      Settings.namespace + "cable",
      Settings.namespace + "print"
    )
  }

  override def createPart(name: String, nbt: NBTTagCompound): TMultiPart = createPart(name, client = false)

  override def createPart(name: String, packet: MCDataInput): TMultiPart = createPart(name, client = true)

  // Matches the legacy IPartFactory adapter, which built previews as client parts.
  override def createPartForClientPreview(name: String, nbt: NBTTagCompound): TMultiPart = createPart(name, client = true)

  def createPart(name: String, client: Boolean): TMultiPart = {
    if (name.equals(Settings.namespace + "cable"))
      return new CablePart()
    if (name.equals(Settings.namespace + "print"))
      return new PrintPart()
    null
  }
}
