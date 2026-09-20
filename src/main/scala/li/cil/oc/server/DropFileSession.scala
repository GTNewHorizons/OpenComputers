package li.cil.oc.server

import li.cil.oc.{OpenComputers, api}
import net.minecraft.entity.player.EntityPlayer
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BoundedInputStream

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.InflaterInputStream

class DropFileSession(fileName: String, compressedSize: Int, target: api.internal.TextBuffer) {
  private val compressed = new ByteArrayOutputStream(math.min(compressedSize, 32 * 1024))

  def onDropFileChunk(data: Array[Byte], player: EntityPlayer): Boolean = {
    if (compressed.size() + data.length <= compressedSize) {
      compressed.write(data)
      true
    } else {
      OpenComputers.log.warn(s"Receive a corrupt drop file packet from ${player.getCommandSenderName} : buffer overflow.")
      false
    }
  }

  def onDropFileEnd(unCompressedSize: Int, player: EntityPlayer): Unit = {
    if (compressed.size() != compressedSize) {
      OpenComputers.log.warn(s"Incomplete drop file packet from ${player.getCommandSenderName}.")
      return
    }

    val uncompressed = new InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray))
    try {
      val content = IOUtils.toByteArray(new BoundedInputStream(uncompressed, unCompressedSize.toLong + 1))
      if (content.length == unCompressedSize)
        target.dropFile(fileName, content, player)
      else
        OpenComputers.log.warn(s"Receive a corrupt drop file packet from ${player.getCommandSenderName}. Decompressed size mismatch! Expected: $unCompressedSize, actually: ${content.length}.")
    } finally {
      uncompressed.close()
      compressed.reset()
    }
  }
}
