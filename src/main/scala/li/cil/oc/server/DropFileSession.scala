package li.cil.oc.server

import li.cil.oc.{OpenComputers, api}
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BoundedInputStream

import scala.collection.JavaConverters._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.UUID
import java.util.zip.InflaterInputStream

class DropFileSession(fileName: String, compressedSize: Int, playerUUID: UUID, playerName: String, target: api.internal.TextBuffer) {
  private val compressed = new ByteArrayOutputStream(math.min(compressedSize, 32 * 1024))

  def onDropFileChunk(data: Array[Byte]): Boolean = {
    if (compressed.size() + data.length <= compressedSize) {
      compressed.write(data)
      true
    } else {
      OpenComputers.log.warn(s"Receive a corrupt drop file packet from $playerName:$playerUUID : buffer overflow.")
      false
    }
  }

  def onDropFileEnd(unCompressedSize: Int): Unit = {
    if (compressed.size() != compressedSize) {
      OpenComputers.log.warn(s"Incomplete drop file packet from $playerName:$playerUUID.")
      return
    }

    val player = for {
      server <- Option(MinecraftServer.getServer)
      p <- server.getConfigurationManager.playerEntityList.asScala.collectFirst {
        case p: EntityPlayerMP if p.getUniqueID == playerUUID => p
      }
    } yield p

    player match {
      case Some (player) =>
        val uncompressed = new InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray))
        try {
          val content = IOUtils.toByteArray(new BoundedInputStream(uncompressed, unCompressedSize.toLong + 1))
          if (content.length == unCompressedSize)
            target.dropFile(fileName, content, player)
          else
            OpenComputers.log.warn(s"Receive a corrupt drop file packet from $playerName:$playerUUID. Decompressed size mismatch! Expected: $unCompressedSize, actually: ${content.length}.")
        } finally {
          uncompressed.close()
          compressed.reset()
        }
      case None =>
        OpenComputers.log.debug(s"Player $playerName:$playerUUID disconnected before drop file finished.")
    }
  }
}
