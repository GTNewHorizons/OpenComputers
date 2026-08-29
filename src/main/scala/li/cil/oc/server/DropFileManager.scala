package li.cil.oc.server

import com.google.common.cache.CacheBuilder
import li.cil.oc.{Localization, OpenComputers, Settings, api}
import net.minecraft.entity.player.EntityPlayer

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.mutable

object DropFileManager {
  private val sessions = CacheBuilder.newBuilder()
    .expireAfterAccess(8, TimeUnit.SECONDS)
    .build[UUID, DropFileSession]()
  private val rateLimiters = mutable.Map.empty[UUID, RateLimiter]
  private def getRateLimiter(playerUUID: UUID): RateLimiter = {
    rateLimiters.getOrElseUpdate(playerUUID, new RateLimiter(Settings.get.maxDropFileCount, Settings.get.maxDropFileCount))
  }

  def onDropFileStart(address: String, fileName: String, compressedSize: Int, player: EntityPlayer): Unit = {
    if (!getRateLimiter(player.getUniqueID).tryRequest()) {
      OpenComputers.log.warn(s"Player ${player.getCommandSenderName} is dropping files too fast.");
      player.addChatMessage(Localization.InputBuffer.TooFrequentFiles)
      return
    }
    if (compressedSize > Settings.get.maxDropFileSize || compressedSize < 0) {
      OpenComputers.log.warn(s"Rejected drop file from ${player.getCommandSenderName}: invalid compressed size $compressedSize.")
      player.addChatMessage(Localization.InputBuffer.FileTooLarge)
      return
    }
    ComponentTracker.get(player.worldObj, address) match {
      case Some(buffer: api.internal.TextBuffer) =>
        if (sessions.getIfPresent(player.getUniqueID) != null)
          OpenComputers.log.warn(s"Player ${player.getCommandSenderName} started a new drop file before finishing the previous one. Overwriting.")
        val session = new DropFileSession(fileName, compressedSize, player.getUniqueID, player.getCommandSenderName, buffer)
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
      OpenComputers.log.debug(s"Received orphan drop file chunk from ${player.getCommandSenderName}.")
    }
  }

  def onDropFileEnd(unCompressedSize: Int, player: EntityPlayer): Unit = {
    if (unCompressedSize > Settings.get.maxDropFileSize || unCompressedSize < 0) {
      OpenComputers.log.warn(s"Rejected drop file from ${player.getCommandSenderName}: invalid uncompressed size $unCompressedSize.")
      player.addChatMessage(Localization.InputBuffer.FileTooLarge)
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

  def clearSession(playerUUID: UUID): Unit = {
    val session = sessions.getIfPresent(playerUUID)
    if (session != null) {
      sessions.invalidate(playerUUID)
    }
    rateLimiters -= playerUUID
  }

  private class RateLimiter(val maxRequests: Int, val refillPerSecond: Int) {
    private var allowRequests: Double = maxRequests
    private var lastRequestTime = System.currentTimeMillis()
    def tryRequest(): Boolean = {
      val now = System.currentTimeMillis()
      val time = now - lastRequestTime
      lastRequestTime = now

      allowRequests = math.min(maxRequests, allowRequests + time * refillPerSecond / 1000.0)
      if (allowRequests >= 1){
        allowRequests -= 1
        true
      } else {
        false
      }
    }
  }
}
