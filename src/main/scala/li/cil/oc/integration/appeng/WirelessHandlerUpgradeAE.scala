package li.cil.oc.integration.appeng

import appeng.api.AEApi
import appeng.api.features.IWirelessTermHandler
import appeng.api.util.IConfigManager
import li.cil.oc.common.item.data.{DroneData, RobotData}
import li.cil.oc.{Constants, api}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

class WirelessHandlerUpgradeAE extends IWirelessTermHandler {

  override def canHandle(itemStack: ItemStack): Boolean = {
    if (itemStack == null) return false
    val item = itemStack.getItem
    if (item == api.Items.get(Constants.ItemName.UpgradeAE1).item()) return true
    (AEUtil.isRobot(itemStack) && AEUtil.getAEUpgradeComponent(new RobotData(itemStack)) != null) || (AEUtil.isDrone(itemStack) && AEUtil.getAEUpgradeComponent(new DroneData(itemStack)) != null)
  }

  override def usePower(entityPlayer: EntityPlayer, v: Double, itemStack: ItemStack): Boolean = false

  override def getConfigManager(itemStack: ItemStack): IConfigManager = null

  override def hasPower(entityPlayer: EntityPlayer, v: Double, itemStack: ItemStack): Boolean = true

  override def setEncryptionKey(itemStack: ItemStack, encKey: String, name: String): Unit = {
    if (AEUtil.isRobot(itemStack)) {
      setEncryptionKeyRobot(itemStack, encKey, name)
      return
    }
    if (AEUtil.isDrone(itemStack)) {
      setEncryptionKeyDrone(itemStack, encKey, name)
      return
    }
    if (!itemStack.hasTagCompound) itemStack.setTagCompound(new NBTTagCompound)
    val tagCompound: NBTTagCompound = itemStack.getTagCompound
    tagCompound.setString("key", encKey)
  }

  override def getEncryptionKey(itemStack: ItemStack): String = {
    if (AEUtil.isRobot(itemStack)) return getEncryptionKeyRobot(itemStack)
    if (AEUtil.isDrone(itemStack)) return getEncryptionKeyDrone(itemStack)
    if (!itemStack.hasTagCompound) itemStack.setTagCompound(new NBTTagCompound)
    itemStack.getTagCompound.getString("key")
  }

  private def setEncryptionKeyRobot(itemStack: ItemStack, encKey: String, name: String): Unit = {
    val robot = new RobotData(itemStack)
    val component = AEUtil.getAEUpgradeComponent(robot)
    if (component != null) setEncryptionKey(component, encKey, name)
    robot.save(itemStack)
  }

  def getEncryptionKeyRobot(stack: ItemStack): String = {
    val robot = new RobotData(stack)
    val component = AEUtil.getAEUpgradeComponent(robot)
    if (component == null) return ""
    getEncryptionKey(component)
  }

  private def setEncryptionKeyDrone(itemStack: ItemStack, encKey: String, name: String): Unit = {
    val robot = new RobotData(itemStack)
    val component = AEUtil.getAEUpgradeComponent(robot)
    if (component != null) setEncryptionKey(component, encKey, name)
    robot.save(itemStack)
  }

  def getEncryptionKeyDrone(stack: ItemStack): String = {
    val drone = new DroneData(stack)
    val component = AEUtil.getAEUpgradeComponent(drone)
    if (component == null) "" else getEncryptionKey(component)
  }

  override def hasInfinityPower(itemStack: ItemStack): Boolean = false

  override def hasInfinityRange(itemStack: ItemStack): Boolean = false
}
object WirelessHandlerUpgradeAE
{
  lazy val instance = new WirelessHandlerUpgradeAE
  def register() = {
    AEApi.instance.registries.wireless.registerWirelessHandler(instance)
  }
}