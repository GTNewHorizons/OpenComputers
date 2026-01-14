package li.cil.oc.integration.appeng

import appeng.api.parts.IPartHost
import appeng.api.util.{DimensionalCoord, IInterfaceViewable}
import appeng.core.features.registries.InterfaceTerminalRegistry
import appeng.parts.AEBasePart
import appeng.parts.p2p.PartP2PTunnel
import appeng.parts.reporting.PartInterfaceTerminal
import li.cil.oc.api.driver
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.world.World
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.common.util.ForgeDirection

import java.util
import scala.collection.JavaConversions.asScalaSet
import scala.collection.JavaConverters._
import scala.collection.mutable

object DriverPartInterfaceTerminal extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).exists(_.isInstanceOf[PartInterfaceTerminal])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = {
    val host = world.getTileEntity(x, y, z).asInstanceOf[IPartHost]
    val side = ForgeDirection.VALID_DIRECTIONS.find { dir => host.getPart(dir).isInstanceOf[PartInterfaceTerminal] }.get
    new Environment(host, host.getPart(side).asInstanceOf[PartInterfaceTerminal])
  }

  final class Environment(val host: IPartHost, val part: PartInterfaceTerminal) extends ManagedTileEntityEnvironment[IPartHost](host, "me_interface_terminal") with NamedBlock with PartEnvironmentBase {
    override def preferredName = "me_interface_terminal"

    override def priority = 0

    @Callback(doc = "function():table -- Returns a list of all interface")
    def getInterfaces(context: Context, args: Arguments): Array[AnyRef] = {
      result(new InterfaceViewableArrayValue(allMachines))
    }

    @Callback(doc = "function(filter:string):table -- Returns a list of interfaces with the specified display name.")
    def getInterfacesByName(context: Context, args: Arguments): Array[AnyRef] = {
      val name = args.checkString(0)
      result(new InterfaceViewableArrayValue(allMachines.filter(_.getName == name)))
    }

    @Callback(doc = "function(location:table{x:number, y:number: z:number[, dimId:number]}[, side:number|string]):table -- Returns a list of interfaces at the specified location. 'side' can be a Forge direction number or name.")
    def getInterfacesByLocation(context: Context, args: Arguments): Array[AnyRef] = {
      val location = ConverterDimensioinalCoord.parse(args.checkTable(0), host.getLocation.getDimension)
      var filtered = allMachines.filter(_.getLocation == location)
      if (args.count() >= 2) {
        val side = ForgeDirection.getOrientation(args.checkInteger(1))
        filtered = filtered.filter {
          case part: AEBasePart => part.getSide == side
          case _ => ForgeDirection.UNKNOWN == side
        }
      }
      result(new InterfaceViewableArrayValue(filtered))
    }

    @Callback(doc = "function(source:table{location:table, slot:number}, target:table{location:table[, slot:number]}):boolean, number|string -- Sends a pattern from source to target. Returns transfer result and target slot or error message.")
    def send(context: Context, args: Arguments): Array[AnyRef] = {
      val sourceData = TransferData.create(args.checkTable(0))
      if (sourceData.slot == -1) return result(false, "Source slot cannot be empty")
      val targetData = TransferData.create(args.checkTable(1))
      sendInternal(Array(TransferTask(sourceData, targetData))).head
    }

    @Callback(doc = "function(tasks:table{{source:table, target:table}, ...}):table -- Executes multiple transfers in one batch. Returns an array of results, each containing [success:boolean, slotOrError:any].")
    def sendBatch(context: Context, args: Arguments): Array[AnyRef] = {
      val tasks = mutable.ArrayBuffer[TransferTask]()
      args.checkTable(0).asScala.foreach {
        case (_, task: util.Map[_, _]) =>
          val source = task.get("source") match {
            case data: util.Map[_, _] => TransferData.create(data)
            case _ => throw new Exception("Missing source")
          }
          val target = task.get("target") match {
            case data: util.Map[_, _] => TransferData.create(data)
            case _ => throw new Exception("Missing target")
          }
          tasks += TransferTask(source, target)
        case _ =>
      }
      result(sendInternal(tasks))
    }

    private case class TransferData(location: DimensionalCoord, side: ForgeDirection)(val slot: Int)

    private object TransferData {
      def create(args: java.util.Map[_, _]): TransferData = {
        val location: DimensionalCoord = args.get("location") match {
          case location: util.Map[_, _] => ConverterDimensioinalCoord.parse(location, host.getLocation.getDimension)
          case _ => throw new Exception("location is missing")
        }
        val side: ForgeDirection = args.get("side") match {
          case value: java.lang.Number => ForgeDirection.getOrientation(value.intValue)
          case str: String => ForgeDirection.valueOf(str)
          case _ => ForgeDirection.UNKNOWN
        }
        val slot: Integer = args.get("slot") match {
          case value: java.lang.Number => value.intValue
          case _ => -1
        }
        TransferData(location, side)(slot)
      }
    }

    private case class TransferTask(source: TransferData, target: TransferData)

    private def findMachines(targets: Array[TransferData]) = {
      val result = mutable.Map[TransferData, IInterfaceViewable]()
      val it = allMachines.iterator

      while (it.hasNext && result.size < targets.length) {
        val machine = it.next()
        val location = machine.getLocation
        val side = machine match {
          case part: AEBasePart => part.getSide
          case _ => ForgeDirection.UNKNOWN
        }
        targets.find(t => t.location == location && t.side == side).foreach { matchedData =>
          result(matchedData) = machine
        }
      }
      result
    }

    private def transferPattern(sourceInv: IInventory, sourceSlot: Int, targetInv: IInventory, targetSlot: Int): Either[String, Int] = {
      if (sourceSlot == -1) return Left("Source slot cannot be empty")
      if (sourceSlot < 0 || sourceSlot >= sourceInv.getSizeInventory) return Left("Source slot out of bounds")
      val sourceStack = sourceInv.getStackInSlot(sourceSlot)
      if (sourceStack == null) return Left("Can't find source pattern")
      val targetSlotActual: Int = if (targetSlot == -1) {
        (0 until targetInv.getSizeInventory).find(i => targetInv.getStackInSlot(i) == null) match {
          case Some(emptyIndex) => emptyIndex
          case None => return Left("No empty slot in target machine")
        }
      } else {
        if (targetSlot < 0 || targetSlot >= targetInv.getSizeInventory) return Left("Target slot out of bounds")
        val targetStack = targetInv.getStackInSlot(targetSlot)
        if (targetStack != null) return Left("Target slot is not empty")
        targetSlot
      }
      targetInv.setInventorySlotContents(targetSlotActual, sourceStack)
      sourceInv.setInventorySlotContents(sourceSlot, null)
      Right(targetSlotActual)
    }

    private def sendInternal(tasks: Seq[TransferTask]) = {
      val finds = findMachines(tasks.flatMap(t => Iterator(t.source, t.target)).distinct.toArray)
      tasks.view.map { task =>
        val sourceData = task.source
        val targetData = task.target
        (finds.get(sourceData), finds.get(targetData)) match {
          case (Some(source), Some(target)) =>
            transferPattern(source.getPatterns, sourceData.slot, target.getPatterns, targetData.slot) match {
              case Right(slot) => result(true, slot)
              case Left(msg) => result(false, msg)
            }
          case (None, None) => result(false, "Both machines not found")
          case (None, _) => result(false, "Source machine not found")
          case (_, None) => result(false, "Target machine not found")
        }
      }.toArray
    }

    private def allMachines: Iterable[IInterfaceViewable] = {
      val grid = getGrid
      if (grid == null) return Array.empty[IInterfaceViewable]
      InterfaceTerminalRegistry.instance.getSupportedClasses.view
        .flatMap(c => grid.getMachines(c).asScala)
        .map(_.getMachine.asInstanceOf[IInterfaceViewable])
        .filter { m =>
          m.shouldDisplay && (m match {
            case p: PartP2PTunnel[_] => !p.isOutput
            case _ => true
          })
        }
    }

    private def getGrid = part.getActionableNode.getGrid
  }
  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (AEUtil.isPartInterfaceTerminal(stack))
        classOf[Environment]
      else null
  }
  class InterfaceViewableArrayValue(it: Iterable[IInterfaceViewable]) extends AbstractValue {
    def this() = this(Iterable.empty)
    private case class InterfaceViewableInfo(name: String, location: DimensionalCoord, side: ForgeDirection, patterns: Array[ItemStack])

    private def convert(info: InterfaceViewableInfo, includePatterns: Boolean = true) = {
      if (includePatterns) {
        Map(
          "name" -> info.name,
          "location" -> info.location,
          "side" -> info.side,
          "patterns" -> info.patterns.zipWithIndex.map(_.swap).toMap
        )
      } else {
        Map(
          "name" -> info.name,
          "location" -> info.location,
          "side" -> info.side
        )
      }
    }

    private var array = it.map { machine =>
      val name = machine.getName
      val location = machine.getLocation
      val side = machine match {
        case part: AEBasePart => part.getSide
        case _ => ForgeDirection.UNKNOWN
      }
      val patterns = machine.getPatterns
      val patternsArray = Array.tabulate(machine.rows() * machine.rowSize()) { index =>
        val stack = patterns.getStackInSlot(index)
        if (stack == null) null else stack.copy()
      }
      InterfaceViewableInfo(name, location, side, patternsArray)
    }.toArray
    private var index = 0

    override def call(context: Context, arguments: Arguments): Array[AnyRef] = {
      array.lift(index) match {
        case Some(info) =>
          index += 1
          result(convert(info))
        case None => result(null)
      }
    }

    override def apply(context: Context, arguments: Arguments): AnyRef = {
      if (arguments.count() == 0) return null
      if (arguments.isInteger(0)) {
        val luaIndex = arguments.checkInteger(0)
        return array.lift(luaIndex - 1) match {
          case Some(info) => convert(info)
          case None => null
        }
      }
      if (arguments.isString(0)) {
        val arg = arguments.checkString(0)
        if (arg == "n") return Int.box(array.length)
      }
      null
    }

    override def load(nbt: NBTTagCompound): Unit = {
      index = nbt.getInteger("index")

      val tagList = nbt.getTagList("array", NBT.TAG_LIST)
      array = Array.tabulate(tagList.tagCount) { i =>
        val el = tagList.getCompoundTagAt(i)
        if (el.hasNoTags) {
          null
        }
        else {
          val name = el.getString("name")
          val location = DimensionalCoord.readFromNBT(el)
          val side = ForgeDirection.getOrientation(nbt.getInteger("side"))
          val patternList = nbt.getTagList("patterns", NBT.TAG_COMPOUND)
          val patterns = Array.tabulate(patternList.tagCount) { j =>
            val itemTag = patternList.getCompoundTagAt(j)
            if (itemTag.hasNoTags) null else ItemStack.loadItemStackFromNBT(itemTag)
          }
          InterfaceViewableInfo(name, location, side, patterns)
        }
      }
    }

    override def save(nbt: NBTTagCompound): Unit = {
      nbt.setInteger("index", index)

      val machinesList = new NBTTagList
      for (info <- this.array if info != null) {
        val machineTag = new NBTTagCompound
        machineTag.setString("name", info.name)
        info.location.writeToNBT(machineTag)
        nbt.setInteger("side", info.side.ordinal())
        val patternsList = new NBTTagList
        info.patterns.foreach { pattern =>
          val stackTag = new NBTTagCompound
          if (pattern != null) pattern.writeToNBT(stackTag)
          patternsList.appendTag(stackTag)
        }
        machineTag.setTag("patterns", patternsList)
        machinesList.appendTag(machineTag)
      }
      nbt.setTag("array", machinesList)
    }

    @Callback(doc = "function():nil -- Reset the iterator index so that the next call will return the first element.")
    def reset(context: Context, arguments: Arguments): Array[AnyRef] = {
      index = 0
      null
    }

    @Callback(doc = "function():number -- Returns the number of elements in the this.array.")
    def count(context: Context, arguments: Arguments): Array[AnyRef] = result(array.length)

    @Callback(doc = "function([includePatterns:boolean]):table -- Returns ALL the interface info in the this.array. Memory intensive.")
    def getAll(context: Context, arguments: Arguments): Array[AnyRef] = {
      result(array.map(convert(_, arguments.optBoolean(0, false)).toMap))
    }

    override def toString = "{InterfaceViewable Array}"
  }
}