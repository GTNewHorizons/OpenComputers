package li.cil.oc.server

import com.google.common.cache.CacheBuilder
import li.cil.oc.{OpenComputers, Settings, api}
import net.minecraft.entity.player.EntityPlayer

import java.util.UUID
import java.util.concurrent.TimeUnit

object DropFileManager {
  private val sessions = CacheBuilder.newBuilder()
    .expireAfterAccess(8, TimeUnit.SECONDS)
    .build[UUID, DropFileSession]()

  def onDropFileStart(address: String, fileName: String, compressedSize: Int, player: EntityPlayer): Unit = {
    if (compressedSize > Settings.get.maxDropFileSize || compressedSize < 0) {
      OpenComputers.log.warn(s"Rejected drop file from ${player.getCommandSenderName}: invalid compressed size $compressedSize.")
      return
    }
    ComponentTracker.get(player.worldObj, address) match {
      case Some(buffer: api.internal.TextBuffer) =>
        if (sessions.getIfPresent(player.getUniqueID) != null)
          OpenComputers.log.warn(s"Player ${player.getCommandSenderName} started a new drop file before finishing the previous one. Overwriting.")
        val session = new DropFileSession(fileName, compressedSize, player, buffer)
        sessions.put(player.getUniqueID, session)
      case _ =>
        OpenComputers.log.warn(s"Drop file target not found for address $address")
    }
  }

  def onDropFileChunk(data: Array[Byte], player: EntityPlayer): Unit = {
    val session = sessions.getIfPresent(player.getUniqueID)
    if (session != null) {
      if (!session.onDropFileChunk(data))
        sessions.invalidate(player.getUniqueID)
    }
    else {
      OpenComputers.log.warn(s"Received orphan drop file chunk from ${player.getCommandSenderName}.")
    }
  }

  def onDropFileEnd(unCompressedSize: Int, player: EntityPlayer): Unit = {
    if (unCompressedSize > Settings.get.maxDropFileSize || unCompressedSize < 0) {
      OpenComputers.log.warn(s"Rejected drop file from ${player.getCommandSenderName}: invalid uncompressed size $unCompressedSize.")
      return
    }
    val session = sessions.getIfPresent(player.getUniqueID)
    if (session != null) {
      session.onDropFileEnd(unCompressedSize)
      sessions.invalidate(player.getUniqueID)
    } else {
      OpenComputers.log.warn(s"Received orphan drop file end from ${player.getCommandSenderName}.")
    }
  }
}
