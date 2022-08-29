package li.cil.oc.server.component

import java.util

import cofh.CoFHCore
import li.cil.oc.{Constants, api}
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{EnvironmentHost, Visibility}
import li.cil.oc.api.prefab
import li.cil.oc.server.component.DebugCard.AccessContext
import net.minecraft.entity.Entity
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.DimensionManager

import scala.collection.convert.WrapAsJava._
import scala.collection.convert.WrapAsScala._
import scala.collection.immutable.Map

class TpsCard (val host: EnvironmentHost) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = api.Network.newNode(this, Visibility.Neighbors).
    withComponent("tps_card", Visibility.Neighbors).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "TPS information",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  // Player this card is bound to (if any) to use for permissions.
  implicit var access: Option[AccessContext] = None

  def player = access.map(_.player)

  private def world = host.world

  private def getTickTimeSum(samples: Array[Long]) = samples.sum.toDouble / samples.length

  @Callback(doc = """function(dimension:number):number -- ms taken by the dimension.""")
  def getTickTimeInDim(context: Context, args: Arguments): Array[AnyRef] = {
    val dim = args.optInteger(0, world.provider.dimensionId)
    result(getTickTimeSum(CoFHCore.server.worldTickTimes.get(dim)) * 1.0E-6D)
  }

  @Callback(doc = """function():number -- Overall tick time of the server.""")
  def getOverallTickTime(context: Context, args: Arguments): Array[AnyRef] =
    result(getTickTimeSum(CoFHCore.server.tickTimeArray) * 1.0E-6D)

  @Callback(doc = """function():table -- Returns a table with index corresponding to the dimension id and value at the index being the dimension name.""")
  def getAllDims(context: Context, args: Arguments): Array[AnyRef] =
    result(DimensionManager.getWorlds.map( w => (w.provider.dimensionId, w.provider.getDimensionName())).toMap)

  @Callback(doc = """function(dimension:number):string -- Returns the name corresponding to the dimension.""")
  def getNameForDim(context: Context, args: Arguments): Array[AnyRef] = {
    val dim = args.checkInteger(0)
    result(DimensionManager.getWorld(dim).provider.getDimensionName)
  }

  @Callback(doc = """function(time:number):number -- Takes a number as parameter (in ms), and returns the actual TPS corresponding to it.""")
  def convertTickTimeIntoTps(context: Context, args: Arguments): Array[AnyRef] = {
    val tps = 1000.0 / args.checkDouble(0)
    result( if (tps > 20.0) 20.0 else tps)
  }

  @Callback(doc = """function():number -- Returns the overall amount of TE loaded in memory.""")
  def getOverallTileEntitiesLoaded(context: Context, args: Arguments): Array[AnyRef] =
    result(CoFHCore.server.worldServers.foldLeft(0)((a, w) => a + w.loadedTileEntityList.length))

  @Callback(doc = """function():number -- Returns the overall amount of chunks loaded in memory.""")
  def getOverallChunksLoaded(context: Context, args: Arguments): Array[AnyRef] =
    result(CoFHCore.server.worldServers.foldLeft(0)((a, w) => a + w.getChunkProvider.getLoadedChunkCount))

  @Callback(doc = """function():number -- Returns the overall amount of entities loaded in memory.""")
  def getOverallEntitiesLoaded(context: Context, args: Arguments): Array[AnyRef] =
    result(CoFHCore.server.worldServers.foldLeft(0)((a, w) => a + w.loadedEntityList.length))

  @Callback(doc = """function():number -- Returns the number of dimensions loaded.""")
  def getOverallDimsLoaded(context: Context, args: Arguments): Array[AnyRef] =
    result(CoFHCore.server.worldServers.length)

  @Callback(doc = """function(dimension:number):table -- Returns a table where the index is the name of the entity class, and the value the amount of entities in that dim.""")
  def getEntitiesListForDim(context: Context, args: Arguments): Array[AnyRef] = {
    val dim = args.checkInteger(0)
    val ents = CoFHCore.server.worldServers(dim).loadedEntityList
    result(ents.foldLeft(Map[String, Int]())((m, e) =>
      m.updated(e.getClass.getName, m.getOrElse(e.getClass.getName, 0) + 1) ) )
  }

  @Callback(doc = """function(dimension:number):table -- Returns a table where the index is the name of the TE class, and the value the amount of TE in that dim.""")
  def getTileEntitiesListForDim(context: Context, args: Arguments): Array[AnyRef] = {
    val dim = args.checkInteger(0)
    val ents = CoFHCore.server.worldServers(dim).loadedTileEntityList
    result(ents.foldLeft(Map[String, Int]())((m, e) =>
      m.updated(e.getClass.getName, m.getOrElse(e.getClass.getName, 0) + 1) ) )
  }

  @Callback(doc = """function(dimension:number):number -- Returns the amount of chunks loaded in that dimension.""")
  def getChunksLoadedForDim(context: Context, args: Arguments): Array[AnyRef] = {
    val dim = args.checkInteger(0)
    result(CoFHCore.server.worldServers(dim).getChunkProvider.getLoadedChunkCount)
  }

  @Callback(doc = """function(className:string, dimension:number):table -- Returns return a table with all the coordinates of the entities matching the class name in that dimension.""")
  def getCoordinatesForEntityClassInDim(context: Context, args: Arguments): Array[AnyRef] =
    access match {
      case None => result(null, "Access denied")
      case _ =>
        val className = args.checkString(0)
        val dim = args.checkInteger(1)
        val ents = CoFHCore.server.worldServers(dim).loadedEntityList.filter(e => e.getClass.getName.equals(className))
        result(ents.map{ case e : Entity => (e.posX, e.posY, e.posZ) })
    }


  override def load(nbt: NBTTagCompound): Unit = {
    super.load(nbt)
    access = AccessContext.load(nbt)
  }
  override def save(nbt: NBTTagCompound): Unit = {
    super.save(nbt)
    access.foreach(_.save(nbt))
  }
}
