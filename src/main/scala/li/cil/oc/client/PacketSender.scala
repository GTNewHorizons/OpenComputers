package li.cil.oc.client

import cpw.mods.fml.common.network.internal.FMLProxyPacket
import li.cil.oc.{Localization, Settings}
import li.cil.oc.common.{CompressedPacketBuilder, PacketFlags, PacketType, SimplePacketBuilder}
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.tileentity._
import li.cil.oc.common.tileentity.traits.Computer
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.util.ForgeDirection

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.{Deflater, DeflaterOutputStream}

object PacketSender {
  // The server can queue this many clipboard chunks. Keep the client-side
  // whole-paste limit in line with that capacity instead of imposing the
  // unrelated historical 64 KiB limit.
  private def maxClipboardLength: Long =
    Settings.get.maxClipboard.toLong * Settings.get.maxSignalQueueSize

  // Timestamp after which the next clipboard message may be sent. Used to
  // avoid spamming large packets on key repeat.
  protected var clipboardCooldown = 0L

  def sendComputerPower(t: Computer, power: Boolean) {
    val pb = new SimplePacketBuilder(PacketType.ComputerPower)

    pb.writeTileEntity(t)
    pb.writeBoolean(power)

    pb.sendToServer()
  }

  def sendDriveMode(unmanaged: Boolean) {
    val pb = new SimplePacketBuilder(PacketType.DriveMode)

    pb.writeBoolean(unmanaged)

    pb.sendToServer()
  }

  def sendDriveLock(): Unit = {
    val pb = new SimplePacketBuilder(PacketType.DriveLock)

    pb.sendToServer()
  }

  def sendDronePower(e: Drone, power: Boolean) {
    val pb = new SimplePacketBuilder(PacketType.DronePower)

    pb.writeEntity(e)
    pb.writeBoolean(power)

    pb.sendToServer()
  }

  def sendKeyDown(address: String, char: Char, code: Int) {
    val pb = new SimplePacketBuilder(PacketType.KeyDown)

    pb.writeUTF(address)
    pb.writeChar(char)
    pb.writeInt(code)

    pb.sendToServer()
  }

  def sendKeyUp(address: String, char: Char, code: Int) {
    val pb = new SimplePacketBuilder(PacketType.KeyUp)

    pb.writeUTF(address)
    pb.writeChar(char)
    pb.writeInt(code)

    pb.sendToServer()
  }

  private def playErrorSound(): Unit = {
    val player = Minecraft.getMinecraft.thePlayer
    val handler = Minecraft.getMinecraft.getSoundHandler
    handler.playSound(new PositionedSoundRecord(new ResourceLocation("note.harp"), 1, 1, player.posX.toFloat, player.posY.toFloat, player.posZ.toFloat))
  }

  def sendClipboard(address: String, value: String) {
    if (value != null && !value.isEmpty) {
      if (value.length.toLong > maxClipboardLength || System.currentTimeMillis() < clipboardCooldown) {
        playErrorSound()
      }
      else {
        clipboardCooldown = System.currentTimeMillis() + value.length / 10
        for (part <- value.grouped(16 * 1024)) {
          val pb = new CompressedPacketBuilder(PacketType.Clipboard)

          pb.writeUTF(address)
          pb.writeUTF(part)

          pb.sendToServer()
        }
      }
    }
  }

  def sendDropFile(address: String, name: String, content: Array[Byte]): Unit = {
      if (content.length > Settings.get.maxDropFileSize) {
        playErrorSound()
        Minecraft.getMinecraft.thePlayer.addChatMessage(Localization.InputBuffer.FileTooLarge)
      }
      else {
        val data = new ByteArrayOutputStream()
        val stream = new DeflaterOutputStream(data, new Deflater(Deflater.BEST_SPEED))
        stream.write(content)
        stream.close()
        // 1(compress) + 38(address) + 514(name) + 8(size) + 1(flag) + 8(size) = 570
        val chunks = data.toByteArray.grouped(31 * 1024).toArray
        val size = chunks.map(_.length).sum
        for (i <- chunks.indices) {
          val chunk = chunks(i)
          val pb = new SimplePacketBuilder(PacketType.DropFile)
          val flag = PacketFlags.DropFile.Chunk |
            (if (i == 0) PacketFlags.DropFile.Start else 0) |
            (if (i == chunks.length - 1) PacketFlags.DropFile.End else 0)
          pb.writeByte(flag)
          if (i == 0) {
            pb.writeUTF(address)
            pb.writeUTF(name)
            pb.writeInt(size)
          }
          pb.writeShort(chunk.length)
          pb.write(chunk)
          if (i == chunks.length - 1)
            pb.writeInt(content.length)
          pb.sendToServer()
        }
      }
  }

  def sendMouseClick(address: String, x: Double, y: Double, drag: Boolean, button: Int) {
    val pb = new SimplePacketBuilder(PacketType.MouseClickOrDrag)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat)
    pb.writeFloat(y.toFloat)
    pb.writeBoolean(drag)
    pb.writeByte(button.toByte)

    pb.sendToServer()
  }

  def sendMouseScroll(address: String, x: Double, y: Double, scroll: Int) {
    val pb = new SimplePacketBuilder(PacketType.MouseScroll)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat)
    pb.writeFloat(y.toFloat)
    pb.writeByte(scroll)

    pb.sendToServer()
  }

  def sendMouseUp(address: String, x: Double, y: Double, button: Int) {
    val pb = new SimplePacketBuilder(PacketType.MouseUp)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat)
    pb.writeFloat(y.toFloat)
    pb.writeByte(button.toByte)

    pb.sendToServer()
  }

  def sendCopyToAnalyzer(address: String, line: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.CopyToAnalyzer)

    pb.writeUTF(address)
    pb.writeInt(line)

    pb.sendToServer()
  }

  def sendMultiPlace() {
    val pb = new SimplePacketBuilder(PacketType.MultiPartPlace)
    pb.sendToServer()
  }

  def sendPetVisibility() {
    val pb = new SimplePacketBuilder(PacketType.PetVisibility)

    pb.writeBoolean(!Settings.get.hideOwnPet)

    pb.sendToServer()
  }

  def sendRackMountableMapping(t: Rack, mountableIndex: Int, nodeIndex: Int, side: Option[ForgeDirection]) {
    val pb = new SimplePacketBuilder(PacketType.RackMountableMapping)

    pb.writeTileEntity(t)
    pb.writeInt(mountableIndex)
    pb.writeInt(nodeIndex)
    pb.writeDirection(side)

    pb.sendToServer()
  }

  def sendRackRelayState(t: Rack, enabled: Boolean) {
    val pb = new SimplePacketBuilder(PacketType.RackRelayState)

    pb.writeTileEntity(t)
    pb.writeBoolean(enabled)

    pb.sendToServer()
  }

  def sendDatabaseSetSlot(slot :Int, stack: ItemStack) {
    val pb = new SimplePacketBuilder(PacketType.DatabaseSetSlot)

    pb.writeByte(slot)
    pb.writeItemStack(stack)

    pb.sendToServer()
  }

  def sendRobotAssemblerStart(t: Assembler) {
    val pb = new SimplePacketBuilder(PacketType.RobotAssemblerStart)

    pb.writeTileEntity(t)

    pb.sendToServer()
  }

  def sendRobotStateRequest(dimension: Int, x: Int, y: Int, z: Int) {
    val pb = new SimplePacketBuilder(PacketType.RobotStateRequest)

    pb.writeInt(dimension)
    pb.writeInt(x)
    pb.writeInt(y)
    pb.writeInt(z)

    pb.sendToServer()
  }

  def sendServerPower(t: Rack, mountableIndex: Int, power: Boolean) {
    val pb = new SimplePacketBuilder(PacketType.ServerPower)

    pb.writeTileEntity(t)
    pb.writeInt(mountableIndex)
    pb.writeBoolean(power)

    pb.sendToServer()
  }

  def sendTextBufferInit(address: String) {
    val pb = new SimplePacketBuilder(PacketType.TextBufferInit)

    pb.writeUTF(address)

    pb.sendToServer()
  }

  def sendWaypointLabel(t: Waypoint): Unit = {
    val pb = new SimplePacketBuilder(PacketType.WaypointLabel)

    pb.writeTileEntity(t)
    pb.writeUTF(t.label)

    pb.sendToServer()
  }
}
