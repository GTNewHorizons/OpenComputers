package li.cil.oc.integration.appeng

import appeng.api.parts.IPatternTerminal.PatternEncodeListener
import appeng.api.parts.{IPartHost, IPatternTerminal}
import appeng.api.storage.StorageName
import appeng.parts.reporting.PartPatternTerminal
import li.cil.oc.api.driver
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{ManagedEnvironment, Node}
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.internal.PartTerminalPatternEnvironment
import li.cil.oc.server.driver.Registry
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.mutable
import scala.reflect.ClassTag

object DriverPartPatternTerminal extends driver.SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean =
    world.getTileEntity(x, y, z) match {
      case container: IPartHost => ForgeDirection.VALID_DIRECTIONS.map(container.getPart).exists(_.isInstanceOf[PartPatternTerminal])
      case _ => false
    }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment = {
    val host = world.getTileEntity(x, y, z).asInstanceOf[IPartHost]
    new Environment(host)
  }

  final class Environment(val host: IPartHost)(implicit val tag: ClassTag[PartPatternTerminal]) extends ManagedTileEntityEnvironment[IPartHost](host, "me_pattern_terminal") with NamedBlock with PartTerminalPatternEnvironment[PartPatternTerminal] {
    override def preferredName = "me_pattern_terminal"

    override def priority = 0

    private val listeners = mutable.ArrayBuffer[(PartPatternTerminal, PatternEncodeListener)]()
    private def bindListeners(): Unit = {
      unbindListeners()
      val location = Registry.convert(result(host.getLocation))
      ForgeDirection.VALID_DIRECTIONS.foreach { side =>
        host.getPart(side) match {
          case part: PartPatternTerminal =>
            val listener = new PatternEncodeListener {
              override def onEncoded(terminal: IPatternTerminal, pattern: ItemStack): Unit = {
                val args: Array[AnyRef] = Array.concat(result("me_pattern_encoded"), location, result(side.ordinal))
                node.sendToReachable("computer.signal", args: _*)
              }
            }
            part.addPatternEncodeListeners(listener)
            listeners += ((part, listener))
          case _ =>
        }
      }
    }
    private def unbindListeners(): Unit = {
      listeners.foreach { case (part, listener) =>
        part.removePatternEncodeListeners(listener)
      }
      listeners.clear()
    }
    override def onConnect(node: Node): Unit = {
      super.onConnect(node)
      if (node == this.node) {
        bindListeners()
      }
    }

    override def onDisconnect(node: Node): Unit = {
      super.onDisconnect(node)
      if (node == this.node) {
        unbindListeners()
      }
    }

    @Callback(doc = "function(side:number):boolean -- Return crafting mode.")
    def isCraftingMode(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      result(part.isCraftingRecipe)
    }

    @Callback(doc = "function(side:number, mode:boolean) -- Set crafting mode.")
    def setCraftingMode(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      part.setCraftingRecipe(args.checkBoolean(1))
      result(null)
    }

    @Callback(doc = "function(side:number):boolean -- Return substitution state.")
    def isSubstitution(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      result(part.isSubstitution)
    }

    @Callback(doc = "function(side:number, mode:boolean) -- Set if substitution.")
    def setSubstitution(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      part.setSubstitution(args.checkBoolean(1))
      result(null)
    }

    @Callback(doc = "function(side:number):boolean -- Return canBeSubstitution state.")
    def isCanBeSubstitution(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      result(part.canBeSubstitution)
    }

    @Callback(doc = "function(side:number, mode:boolean) -- Set if canBeSubstitution.")
    def setCanBeSubstitution(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      part.setCanBeSubstitution(args.checkBoolean(1))
      result(null)
    }

    @Callback(doc = "function(side:number, index:number[, detail:table, type:string]):boolean -- Set the pattern input.")
    def setInterfacePatternInput(context: Context, args: Arguments): Array[AnyRef] =
      setPatternSlot(context, args, "in")

    @Callback(doc = "function(side:number, index:number[, detail:table, type:string]):boolean -- Set the pattern input.")
    def setInterfacePatternOutput(context: Context, args: Arguments): Array[AnyRef] =
      setPatternSlot(context, args, "out")

    @Callback(doc = "function(side:number, index:number):boolean -- Get the terminal pattern input stack.")
    def getTerminalPatternInput(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      val index = args.checkInteger(1)
      val inv = part.getAEInventoryByName(StorageName.CRAFTING_INPUT)
      result(inv.getAEStackInSlot(index))
    }

    @Callback(doc = "function(side:number, index:number[, detail:table, type:string]):boolean -- Set the terminal pattern input stack.")
    def setTerminalPatternInput(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      val index = args.checkInteger(1) - 1
      val stack = getAEStack(context, args, 0)
      val inv = part.getAEInventoryByName(StorageName.CRAFTING_INPUT)
      inv.putAEStackInSlot(index, stack)
      result(null)
    }

    @Callback(doc = "function(side:number, index:number):boolean -- Get the terminal pattern output stack.")
    def getTerminalPatternOutput(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      val index = args.checkInteger(1)
      val inv = part.getAEInventoryByName(StorageName.CRAFTING_OUTPUT)
      result(inv.getAEStackInSlot(index))
    }

    @Callback(doc = "function(side:number):table -- Get the pattern in the terminal.")
    def getTerminalPattern(context: Context, args: Arguments): Array[AnyRef] = {
      val inv = getPatternInventory(context, args)
      val stack = inv.getStackInSlot(1)
      result(stack)
    }

    @Callback(doc = "function(side:number, index:number[, detail:table, type:string]):boolean -- Set the terminal pattern input stack.")
    def setTerminalPatternOutput(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      val index = args.checkInteger(1) - 1
      val stack = getAEStack(context, args, 0)
      val inv = part.getAEInventoryByName(StorageName.CRAFTING_OUTPUT)
      inv.putAEStackInSlot(index, stack)
      result(null)
    }

    @Callback(doc = "function(side:number):boolean -- Encode pattern.")
    def encode(context: Context, args: Arguments): Array[AnyRef] = {
      val part = getPart(args.checkSideAny(0))
      result(part.encode(part.getCustomName, host.getTile.getWorldObj))
    }
  }
}
