package li.cil.oc.integration.matter_manipulator

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode._
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState._
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.{ItemMatterManipulator, Location, MMState, Transform}
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils
import li.cil.oc.api.Network
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{EnvironmentHost, Visibility}
import li.cil.oc.api.prefab.{DriverItem, ManagedEnvironment}
import li.cil.oc.common.Slot
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection
import org.joml.Vector3i

class DriverMatterManipulator extends DriverItem {
  override def worksWith(stack: ItemStack): Boolean = stack.getItem.isInstanceOf[ItemMatterManipulator]

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment = new Environment(stack, host)

  override def slot(stack: ItemStack): String = Slot.Tool

  class Environment(val stack: ItemStack, val host: EnvironmentHost) extends ManagedEnvironment {
    val item: ItemMatterManipulator = stack.getItem.asInstanceOf[ItemMatterManipulator]
    setNode(Network.newNode(this, Visibility.Network).withComponent("matter_manipulator").create)
    val agent: Agent = host.asInstanceOf[Agent]
    val state: MMState = ItemMatterManipulator.getState(stack)

    @Callback(doc = "function():number -- Get the charge.")
    def getCharge(context: Context, arguments: Arguments) = result(item.getCharge(stack))

    @Callback(doc = "function(mode: [COPYING, EXCHANGING, GEOMETRY, MOVING, CABLES]: string):boolean -- Set the place mode.")
    def setPlaceMode(ctx: Context, args: Arguments): Array[AnyRef] = {
      val new_mode = PlaceMode.valueOf(args.checkString(0))
      val requiredBit = new_mode match {
        case COPYING => ItemMatterManipulator.ALLOW_COPYING
        case EXCHANGING => ItemMatterManipulator.ALLOW_EXCHANGING
        case GEOMETRY => 0
        case MOVING => ItemMatterManipulator.ALLOW_MOVING
        case CABLES => ItemMatterManipulator.ALLOW_CABLES
      }
      if (state.hasCap(requiredBit)) {
        state.config.placeMode = new_mode
        ItemMatterManipulator.setState(stack, state)
        result(true)
      }
      else {
        result(false)
      }
    }

    @Callback(doc = "function(mode: [NONE, REPLACEABLE, ALL]: string) -- Set the remove mode.")
    def setRemoveMode(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.removeMode = BlockRemoveMode.valueOf(args.checkString(0))
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(mode: [CORNERS, EDGES, FACES, VOLUMES, ALL]: string) -- Set the block select mode.")
    def setBlockSelectMode(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.blockSelectMode = BlockSelectMode.valueOf(args.checkString(0))
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(action: [MOVING_COORDS, MARK_COPY_A, MARK_COPY_B, MARK_CUT_A, MARK_CUT_B, MARK_PASTE, GEOM_SELECTING_BLOCK, EXCH_SET_TARGET, EXCH_ADD_REPLACE, EXCH_SET_REPLACE, PICK_CABLE, MARK_ARRAY]: string) -- Set the pending action.")
    def setPendingAction(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.action = PendingAction.valueOf(args.checkString(0))
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(shape: [LINE, CUBE, SPHERE, CYLINDER]: string) -- Set the Shape.")
    def setShape(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.shape = Shape.valueOf(args.checkString(0))
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- Clear blocks.")
    def clearBlocks(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.corners = null
      state.config.edges = null
      state.config.faces = null
      state.config.volumes = null
      state.config.action = null
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(x:number, y:number, z:number) -- Set the coordA.")
    def setCoordA(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.coordA = new Location(host.world(), args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(x:number, y:number, z:number) -- Set the coordB.")
    def setCoordB(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.coordB = new Location(host.world(), args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(x:number, y:number, z:number) -- Set the coordC.")
    def setCoordC(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.coordC = new Location(host.world(), args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- Move here.")
    def moveHere(ctx: Context, args: Arguments): Array[AnyRef] = {
      if (state.config.shape.requiresC) if (Location.areCompatible(state.config.coordA, state.config.coordB, state.config.coordC)) {
        val offsetB = state.config.coordB.toVec.sub(state.config.coordA.toVec)
        val offsetC = state.config.coordC.toVec.sub(state.config.coordA.toVec)
        val newA = MMUtils.getLookingAtLocation(agent.player())
        val newB = new Vector3i(newA).add(offsetB)
        val newC = new Vector3i(newA).add(offsetC)
        state.config.coordA = new Location(host.world(), newA)
        state.config.coordB = new Location(host.world(), newB)
        state.config.coordC = new Location(host.world(), newC)
      }
      else if (Location.areCompatible(state.config.coordA, state.config.coordB)) {
        val offsetB = state.config.coordB.toVec.sub(state.config.coordA.toVec)
        val newA = MMUtils.getLookingAtLocation(agent.player())
        val newB = new Vector3i(newA).add(offsetB)
        state.config.coordA = new Location(host.world(), newA)
        state.config.coordB = new Location(host.world(), newB)
      }
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- Clear the coords.")
    def clearCoords(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.action = null
      state.config.coordA = null
      state.config.coordB = null
      state.config.coordC = null
      state.config.coordAOffset = null
      state.config.coordBOffset = null
      state.config.coordCOffset = null
      ItemMatterManipulator.setState(stack, state)
      result(true)
    }

    @Callback(doc = "function() -- Clear the transform.")
    def clearTransform(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.transform = new Transform
      state.config.arraySpan = null
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- clear whitelist.")
    def resetTransform(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.transform = new Transform
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- mark and copy.")
    def markCopy(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.action = PendingAction.MARK_COPY_A
      state.config.coordA = null
      state.config.coordB = null
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- mark and cut.")
    def markCut(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.action = PendingAction.MARK_CUT_A
      state.config.coordA = null
      state.config.coordB = null
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- mark and paste.")
    def markPaste(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.action = PendingAction.MARK_PASTE
      state.config.coordC = null
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- clear whitelist.")
    def clearWhiteList(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.replaceWhitelist = null
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(x: number, y: number, z: number) -- set array span.")
    def setArraySpan(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.arraySpan = new Vector3i(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function() -- reset array span.")
    def resetArraySpan(ctx: Context, args: Arguments): Array[AnyRef] = {
      state.config.arraySpan = null
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(flip: ['FLIP_X', 'FLIP_Y', 'FLIP_Z']: string) -- toggle the transform flip.")
    def toggleTransformFlip(ctx: Context, args: Arguments): Array[AnyRef] = {
      var transform = state.config.transform
      if (transform == null) {
        transform = new Transform
        state.config.transform = transform
      }
      args.checkString(0) match {
        case "FLIP_X" => transform.flipX ^= true
        case "FLIP_Y" => transform.flipY ^= true
        case "FLIP_Z" => transform.flipZ ^= true
      }
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function(side: number, positive: boolean) -- rotate the transform.")
    def rotateTransform(ctx: Context, args: Arguments): Array[AnyRef] = {
      if (state.config.transform == null) state.config.transform = new Transform
      val dir = args.checkInteger(0)
      val positive = args.checkBoolean(1)

      if (dir < 0 || dir >= ForgeDirection.VALID_DIRECTIONS.length) return null
      val amount = if (positive) 1 else -1

      var transform = state.config.transform
      if (transform == null) {
        transform = new Transform
        state.config.transform = transform
      }

      transform.rotate(ForgeDirection.VALID_DIRECTIONS(dir), amount)
      ItemMatterManipulator.setState(stack, state)
      null
    }

    @Callback(doc = "function():string -- Get the remove mode.")
    def getRemoveMode(ctx: Context, args: Arguments) = result(state.config.removeMode.toString)

    @Callback(doc = "function():string -- Get the place mode.")
    def getPlaceMode(ctx: Context, args: Arguments) = result(state.config.placeMode.toString)

    @Callback(doc = "function():string -- Get the block select mode.")
    def getBlockSelectMode(ctx: Context, args: Arguments) = result(state.config.blockSelectMode.toString)

    @Callback(doc = "function():string -- Get the pending action.")
    def getPendingAction(ctx: Context, args: Arguments) = result(Option(state.config.action).map(_.toString).orNull)

    @Callback(doc = "function():string -- Get the current shape.")
    def getShape(ctx: Context, args: Arguments) = result(state.config.shape.toString)

    @Callback(doc = "function():table or nil -- Get coordinate A.")
    def getCoordA(ctx: Context, args: Arguments) = result(state.config.coordA)

    @Callback(doc = "function():table or nil -- Get coordinate B.")
    def getCoordB(ctx: Context, args: Arguments) = result(state.config.coordB)

    @Callback(doc = "function():table or nil -- Get coordinate C.")
    def getCoordC(ctx: Context, args: Arguments) = result(state.config.coordC)

    @Callback(doc = "function():string -- Get the replace whitelist.")
    def getReplaceWhitelist(ctx: Context, args: Arguments) = result(state.config.replaceWhitelist.toString)
  }
}
