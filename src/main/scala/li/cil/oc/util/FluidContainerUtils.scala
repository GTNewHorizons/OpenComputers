package li.cil.oc.util

import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids._

object FluidContainerUtils {

  /**
   * Retrieves an actual fluid handler implementation for a fluid container item.
   */
  def fluidHandlerIn(inventory: IInventory, slot: Int): Option[IFluidHandler] = {
    inventory.getStackInSlot(slot) match {
      case stack: ItemStack if stack != null =>
        val oneSizedStack = stack.copy()
        oneSizedStack.stackSize = 1
        oneSizedStack match {
          case _ if FluidContainerRegistry.isFilledContainer(oneSizedStack) => Option(new FilledContainerWrapper(oneSizedStack))
          case _ if FluidContainerRegistry.isEmptyContainer(oneSizedStack) => Option(new EmptyContainerWrapper(oneSizedStack))
          case _ =>
            stack.getItem match {
              case _: IFluidContainerItem => Option(new FluidContainerItemWrapper(oneSizedStack))
              case _ => None
            }
        }
      case _ => None
    }
  }
  
  /**
   * A fluid handler implementation that records fluid operations and can replay them.
   */
  def replayableFluidHandler(handler: IFluidHandler, simulate: Boolean = true): IFluidHandler = {
    new ReplayableFluidHandler(handler, simulate)
  }

  def getContainerResult(container: IFluidHandler): ItemStack = {
    container match {
      case w: ContainerWrapper => w.getResult
      case _ => null
    }
  }


  def replay(replayable: IFluidHandler, handler: IFluidHandler): Unit = {
    replayable match {
      case r: ReplayableFluidHandler => r.replay(handler)
      case _ => ()
    }
  }

  private trait ContainerWrapper extends IFluidHandler {
    def getResult: ItemStack
  }

  private class FilledContainerWrapper(val stack: ItemStack) extends ContainerWrapper {
    private val fluid = FluidContainerRegistry.getFluidForFilledItem(stack)
    private val capacity = FluidContainerRegistry.getContainerCapacity(stack)
    private var result = null: ItemStack
    private var dirty = false

    override def fill(from: ForgeDirection, resource: FluidStack, doFill: Boolean): Int = 0

    override def drain(from: ForgeDirection, resource: FluidStack, doDrain: Boolean): FluidStack = {
      if (dirty) return null
      if (resource == null || !resource.isFluidEqual(fluid)) null
      else drain(from, resource.amount, doDrain)
    }

    override def drain(from: ForgeDirection, maxDrain: Int, doDrain: Boolean): FluidStack = {
      if (dirty) return null
      if (maxDrain < capacity) null
      else {
        if (doDrain) {
          result = FluidContainerRegistry.drainFluidContainer(stack)
          dirty = true
        }
        fluid
      }
    }

    override def canFill(from: ForgeDirection, fluid: Fluid): Boolean = false

    override def canDrain(from: ForgeDirection, fluid: Fluid): Boolean = {
      if (dirty) return false
      fluid != null && this.fluid.getFluid == fluid
    }

    override def getTankInfo(from: ForgeDirection): Array[FluidTankInfo] = {
      Array(new FluidTankInfo(fluid, capacity))
    }

    override def getResult: ItemStack = result
  }

  private class EmptyContainerWrapper(val stack: ItemStack) extends ContainerWrapper {
    private var result = null: ItemStack
    private var dirty = false

    override def fill(from: ForgeDirection, resource: FluidStack, doFill: Boolean): Int = {
      if (dirty) return 0
      val filledContainer = FluidContainerRegistry.fillFluidContainer(resource, stack)
      if (filledContainer == null) 0
      else {
        if (doFill) {
          result = filledContainer
          dirty = true
        }
        FluidContainerRegistry.getFluidForFilledItem(filledContainer).amount
      }
    }

    override def drain(from: ForgeDirection, resource: FluidStack, doDrain: Boolean): FluidStack = null

    override def drain(from: ForgeDirection, maxDrain: Int, doDrain: Boolean): FluidStack = null

    override def canFill(from: ForgeDirection, fluid: Fluid): Boolean = {
      if (dirty) return false
      FluidContainerRegistry.fillFluidContainer(new FluidStack(fluid, Int.MaxValue), stack) != null
    }

    override def canDrain(from: ForgeDirection, fluid: Fluid): Boolean = false

    override def getTankInfo(from: ForgeDirection): Array[FluidTankInfo] = {
      Array(new FluidTankInfo(null, Int.MaxValue))
    }

    override def getResult: ItemStack = result
  }

  private class FluidContainerItemWrapper(val stack: ItemStack) extends ContainerWrapper {
    private val fluidContainerItem = stack.getItem.asInstanceOf[IFluidContainerItem]

    override def fill(from: ForgeDirection, resource: FluidStack, doFill: Boolean): Int = {
      fluidContainerItem.fill(stack, resource, doFill)
    }

    override def drain(from: ForgeDirection, resource: FluidStack, doDrain: Boolean): FluidStack = {
      if (fluidContainerItem.getFluid(stack) == null || !fluidContainerItem.getFluid(stack).isFluidEqual(resource)) null
      else fluidContainerItem.drain(stack, resource.amount, doDrain)
    }

    override def drain(from: ForgeDirection, maxDrain: Int, doDrain: Boolean): FluidStack = {
      fluidContainerItem.drain(stack, maxDrain, doDrain)
    }

    override def canFill(from: ForgeDirection, fluid: Fluid): Boolean = {
      if (fluidContainerItem.getFluid(stack) == null) true
      else if (fluidContainerItem.getFluid(stack).getFluid == fluid && fluidContainerItem.getFluid(stack).amount < fluidContainerItem.getCapacity(stack)) true
      else false
    }

    override def canDrain(from: ForgeDirection, fluid: Fluid): Boolean = {
      if (fluidContainerItem.getFluid(stack) == null) false
      else if (fluidContainerItem.getFluid(stack).getFluid == fluid && 0 < fluidContainerItem.getFluid(stack).amount) true
      else false
    }

    override def getTankInfo(from: ForgeDirection): Array[FluidTankInfo] = {
      Array(new FluidTankInfo(fluidContainerItem.getFluid(stack), fluidContainerItem.getCapacity(stack)))
    }

    override def getResult: ItemStack = stack
  }

  private class ReplayableFluidHandler(val handler: IFluidHandler, val simulate: Boolean = true) extends IFluidHandler {
    var actions: List[IFluidHandler => Unit] = List.empty

    def replay(handler: IFluidHandler): Unit = {
      actions.foreach(action => action(handler))
    }

    override def fill(from: ForgeDirection, resource: FluidStack, doFill: Boolean): Int = {
      actions.+:=((h: IFluidHandler) => {
        h.fill(from, resource, doFill)
        ()
      })
      handler.fill(from, resource, doFill && !simulate)

    }

    override def drain(from: ForgeDirection, resource: FluidStack, doDrain: Boolean): FluidStack = {
      actions.+:=((h: IFluidHandler) => {
        h.drain(from, resource, doDrain)
        ()
      })
      handler.drain(from, resource, doDrain && !simulate)
    }

    override def drain(from: ForgeDirection, maxDrain: Int, doDrain: Boolean): FluidStack = {
      actions.+:=((h: IFluidHandler) => {
        h.drain(from, maxDrain, doDrain)
        ()
      })
      handler.drain(from, maxDrain, doDrain && !simulate)
    }

    override def canFill(from: ForgeDirection, fluid: Fluid): Boolean = {
      handler.canFill(from, fluid)
    }

    override def canDrain(from: ForgeDirection, fluid: Fluid): Boolean = {
      handler.canDrain(from, fluid)
    }

    override def getTankInfo(from: ForgeDirection): Array[FluidTankInfo] = {
      handler.getTankInfo(from)
    }
  }
}