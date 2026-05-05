package li.cil.oc.client

import cpw.mods.fml.client.FMLClientHandler
import cpw.mods.fml.common.eventhandler.{EventPriority, SubscribeEvent}
import cpw.mods.fml.common.gameevent.TickEvent.{ClientTickEvent, Phase}
import li.cil.oc.{OpenComputers, Settings}
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.{SoundCategory, SoundManager, SoundPoolEntry}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.integrated.IntegratedServer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.sound.SoundLoadEvent
import net.minecraftforge.event.world.WorldEvent
import paulscode.sound.SoundSystemConfig

import java.net.{MalformedURLException, URL, URLConnection, URLStreamHandler}
import java.util.UUID
import scala.collection.mutable

object Sound {

  private val sources = mutable.Map.empty[TileEntity, PseudoLoopingStream]
  private val commandQueue = mutable.PriorityQueue.empty[Command]
  private var lastVolume = FMLClientHandler.instance.getClient.gameSettings.getSoundLevel(SoundCategory.BLOCKS)

  // Set in init event.
  private var manager: SoundManager = _

  private def soundSystem = if (manager != null) manager.sndSystem else null

  private def updateVolume() {
    val volume =
      if (isGamePaused) 0f
      else FMLClientHandler.instance.getClient.gameSettings.getSoundLevel(SoundCategory.BLOCKS)
    if (volume != lastVolume) {
      lastVolume = volume
      sources.synchronized {
        for (sound <- sources.values) {
          sound.updateVolume()
        }
      }
    }
  }

  private def isGamePaused = MinecraftServer.getServer != null && !MinecraftServer.getServer.isDedicatedServer && (MinecraftServer.getServer match {
    case integrated: IntegratedServer => Minecraft.getMinecraft.isGamePaused
    case _ => false
  })

  private def processQueue() {
    if (commandQueue.nonEmpty) {
      commandQueue.synchronized {
        while (commandQueue.nonEmpty && commandQueue.head.when < System.currentTimeMillis()) {
          try commandQueue.dequeue()() catch {
            case t: Throwable => OpenComputers.log.warn("Error processing sound command.", t)
          }
        }
      }
    }
  }

  def startLoop(tileEntity: TileEntity, name: String, volume: Float = 1f, delay: Long = 0) {
    if (Settings.get.soundVolume > 0 && Minecraft.getMinecraft.theWorld != null) {
      commandQueue.synchronized {
        commandQueue += new StartCommand(System.currentTimeMillis() + delay, tileEntity, name, volume)
      }
    }
  }

  def stopLoop(tileEntity: TileEntity) {
    if (Settings.get.soundVolume > 0 && Minecraft.getMinecraft.theWorld != null) {
      commandQueue.synchronized {
        commandQueue += new StopCommand(tileEntity)
      }
    }
  }

  def updatePosition(tileEntity: TileEntity) {
    if (Settings.get.soundVolume > 0 && Minecraft.getMinecraft.theWorld != null) {
      commandQueue.synchronized {
        commandQueue += new UpdatePositionCommand(tileEntity)
      }
    }
  }

  @SubscribeEvent
  def onSoundLoad(event: SoundLoadEvent) {
    manager = event.manager
  }

  private var tickCount = 0;

  @SubscribeEvent
  def onTick(e: ClientTickEvent) {
    if (e.phase == Phase.START) return
    if (soundSystem != null && Minecraft.getMinecraft.theWorld != null && Settings.get.soundVolume > 0) {
      tickCount = tickCount + 1
      if (tickCount % 10 == 0) {
        sources.synchronized {
          updateVolume()
          processQueue()
        }
      }
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  def onWorldUnload(event: WorldEvent.Unload) {
    sources.synchronized(try sources.foreach(_._2.stop()) catch {
      case _: Throwable => // Ignore.
    })
    sources.synchronized(sources.clear())
    commandQueue.synchronized(commandQueue.clear())
  }

  private abstract class Command(val when: Long, val tileEntity: TileEntity) extends Ordered[Command] {
    def apply(): Unit

    override def compare(that: Command) = (that.when - when).toInt
  }

  private class StartCommand(when: Long, tileEntity: TileEntity, val name: String, val volume: Float) extends Command(when, tileEntity) {
    override def apply() {
      sources.synchronized {
        sources.getOrElseUpdate(tileEntity, new PseudoLoopingStream(tileEntity, volume)).play(name)
      }
    }
  }

  private class StopCommand(tileEntity: TileEntity) extends Command(System.currentTimeMillis() + 1, tileEntity) {
    override def apply() {
      sources.synchronized {
        sources.remove(tileEntity) match {
          case Some(sound) => sound.stop()
          case _ =>
        }
      }
      commandQueue.synchronized {
        // Remove all other commands for this tile entity from the queue. This
        // is inefficient, but we generally don't expect the command queue to
        // be very long, so this should be OK.
        commandQueue ++= commandQueue.dequeueAll.filter(_.tileEntity != tileEntity)
      }
    }
  }

  private class UpdatePositionCommand(tileEntity: TileEntity) extends Command(System.currentTimeMillis(), tileEntity) {
    override def apply() {
      sources.synchronized {
        sources.get(tileEntity) match {
          case Some(sound) => sound.updatePosition()
          case _ =>
        }
      }
    }
  }

  private class PseudoLoopingStream(val tileEntity: TileEntity, val volume: Float, val source: String = UUID.randomUUID.toString) {
    var initialized = false

    def updateVolume() {
      soundSystem.setVolume(source, lastVolume * volume * Settings.get.soundVolume)
    }

    def updatePosition() {
      if (tileEntity != null) soundSystem.setPosition(source, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)
      else soundSystem.setPosition(source, 0, 0, 0)
    }

    def play(name: String) {
      val resourceName = s"${Settings.resourceDomain}:$name"
      val sound = Minecraft.getMinecraft.getSoundHandler.getSound(new ResourceLocation(resourceName))
      val resource = (sound.func_148720_g: SoundPoolEntry).getSoundPoolEntryLocation
      if (!initialized) {
        initialized = true
        if (tileEntity != null) soundSystem.newSource(false, source, toUrl(resource), resource.toString, true, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, SoundSystemConfig.ATTENUATION_LINEAR, 16)
        else soundSystem.newSource(false, source, toUrl(resource), resource.toString, false, 0, 0, 0, SoundSystemConfig.ATTENUATION_NONE, 0)
        updateVolume()
        soundSystem.activate(source)
      }
      soundSystem.play(source)
    }

    def stop() {
      if (soundSystem != null) try {
        soundSystem.stop(source)
        soundSystem.removeSource(source)
      }
      catch {
        case _: Throwable =>
      }
    }
  }

  // This is copied from SoundManager.getURLForSoundResource, which is private.
  private def toUrl(resource: ResourceLocation): URL = {
    val name = s"mcsounddomain:${resource.getResourceDomain}:${resource.getResourcePath}"
    try {
      new URL(null, name, new URLStreamHandler {
        protected def openConnection(url: URL): URLConnection = new URLConnection(url) {
          def connect() {
          }

          override def getInputStream = Minecraft.getMinecraft.getResourceManager.getResource(resource).getInputStream
        }
      })
    }
    catch {
      case _: MalformedURLException => null
    }
  }
}
