package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.server.component.{result, traits}
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.{FluidContainerUtils, FluidUtils, InventoryUtils}
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids.IFluidHandler

trait FluidContainerTransfer extends traits.WorldAware with traits.SideRestricted {
  // Return None on success, else Some("failure reason")
  def onTransferContents(): Option[String]

  @Callback(doc = """function(tankSide:number, inventorySide:number, inventorySlot:number [, count:number [, sourceTank:number [, outputSide:number[, outputSlot:number]]]]):boolean, number -- Transfer some fluid from the tank to the container. Returns operation result and filled amount""")
  def transferFluidFromTankToContainer(context: Context, args: Arguments): Array[AnyRef] = {
    val tankSide = checkSideForAction(args, 0)
    val tankPos = position.offset(tankSide)
    val inventorySide = checkSideForAction(args, 1)
    val checkInventorySlot: IInventory => Int = inventory => args.checkSlot(inventory, 2)
    val count = args.optFluidCount(3)
    val sourceTank = args.optInteger(4, -1)
    val outputSide = if (args.count > 5) checkSideForAction(args, 5) else inventorySide
    val checkOutputSlot: IInventory => Option[Int] = inventory => if (args.count > 6) Some(args.checkSlot(inventory, 6)) else None

    onTransferContents() match {
      case Some(reason) =>
        result(Unit, reason)
      case _ =>
        withInventory(inventorySide, inventory => {
          withInventory(outputSide, output => {
            withReplayableMove(
              FluidUtils.fluidHandlerAt(tankPos),
              FluidContainerUtils.fluidHandlerIn(inventory, checkInventorySlot(inventory)),
              (replayableTank, replayableContainer) => FluidUtils.transferBetweenFluidHandlers(replayableTank, tankSide, replayableContainer, ForgeDirection.UNKNOWN, count, sourceTank),
              (tank, container, tankReplay, containerReplay) => {
                containerReplay(container)
                val result = FluidContainerUtils.getContainerResult(container)
                if (syncResult(inventory, inventorySide, checkInventorySlot(inventory), output, outputSide, checkOutputSlot(output), result)) {
                  tankReplay(tank)
                  true
                } else {
                  false
                }
              }
            )
          })
        })
    }
  }

  @Callback(doc = """function(inventorySide:number, inventorySlot:number, tankSide:number [, count:number [, outputSide:number[, outputSlot:number]]]):boolean, number -- Transfer some fluid from the container to the tank. Returns operation result and filled amount""")
  def transferFluidFromContainerToTank(context: Context, args: Arguments): Array[AnyRef] = {
    val inventorySide = checkSideForAction(args, 0)
    val checkInventorySlot: IInventory => Int = inventory => args.checkSlot(inventory, 1)
    val tankSide = checkSideForAction(args, 2)
    val tankPos = position.offset(tankSide)
    val count = args.optFluidCount(3)
    val outputSide = if (args.count > 4) checkSideForAction(args, 4) else inventorySide
    val checkOutputSlot: IInventory => Option[Int] = inventory => if (args.count > 5) Some(args.checkSlot(inventory, 5)) else None

    onTransferContents() match {
      case Some(reason) =>
        result(Unit, reason)
      case _ =>
        withInventory(inventorySide, inventory => {
          withInventory(outputSide, output => {
            withReplayableMove(
              FluidContainerUtils.fluidHandlerIn(inventory, checkInventorySlot(inventory)),
              FluidUtils.fluidHandlerAt(tankPos),
              (replayableContainer, replayableTank) => FluidUtils.transferBetweenFluidHandlers(replayableContainer, ForgeDirection.UNKNOWN, replayableTank, tankSide, count),
              (container, tank, containerReplay, tankReplay) => {
                containerReplay(container)
                val result = FluidContainerUtils.getContainerResult(container)
                if (syncResult(inventory, inventorySide, checkInventorySlot(inventory), output, outputSide, checkOutputSlot(output), result)) {
                  tankReplay(tank)
                  true
                } else {
                  false
                }
              }
            )
          })
        })
    }
  }

  @Callback(doc = """function(sourceSide:number, sourceSlot:number, sinkSide:number, sinkSlot:number[, count:number [, sourceOutputSide:number[, sinkOutputSide:number[, sourceOutputSlot:number[, sinkOutputSlot:number]]]]]):boolean, number -- Transfer some fluid from a container to another container. Returns operation result and filled amount""")
  def transferFluidBetweenContainers(context: Context, args: Arguments): Array[AnyRef] = {
    val sourceSide = checkSideForAction(args, 0)
    val checkSourceSlot: IInventory => Int = inventory => args.checkSlot(inventory, 1)
    val sinkSide = checkSideForAction(args, 2)
    val checkSinkSlot: IInventory => Int = inventory => args.checkSlot(inventory, 3)
    val count = args.optFluidCount(4)
    val sourceOutputSide = if (args.count > 5) checkSideForAction(args, 5) else sourceSide
    val sinkOutputSide = if (args.count > 6) checkSideForAction(args, 6) else sinkSide
    val checkSourceOutputSlot: IInventory => Option[Int] = inventory => if (args.count > 7) Some(args.checkSlot(inventory, 7)) else None
    val checkSinkOutputSlot: IInventory => Option[Int] = inventory => if (args.count > 8) Some(args.checkSlot(inventory, 8)) else None

    onTransferContents() match {
      case Some(reason) =>
        result(Unit, reason)
      case _ =>
        withInventory(sourceSide, source => {
          withInventory(sinkSide, sink => {
            withInventory(sourceOutputSide, sourceOutput => {
              withInventory(sinkOutputSide, sinkOutput => {
                withMove(
                  FluidContainerUtils.fluidHandlerIn(source, checkSourceSlot(source)),
                  FluidContainerUtils.fluidHandlerIn(sink, checkSinkSlot(sink)),
                  (sourceContainer, sinkContainer) => FluidUtils.transferBetweenFluidHandlers(sourceContainer, ForgeDirection.UNKNOWN, sinkContainer, ForgeDirection.UNKNOWN, count),
                  (sourceContainer, sinkContainer) => {
                    val sourceResult = FluidContainerUtils.getContainerResult(sourceContainer)
                    val sinkResult = FluidContainerUtils.getContainerResult(sinkContainer)
                    if (
                      syncResult(source, sourceSide, checkSourceSlot(source), sourceOutput, sourceOutputSide, checkSourceOutputSlot(sourceOutput), sourceResult, simulate = true)
                        && syncResult(sink, sinkSide, checkSinkSlot(sink), sinkOutput, sinkOutputSide, checkSinkOutputSlot(sinkOutput), sinkResult, simulate = true)
                    ) {
                      syncResult(source, sourceSide, checkSourceSlot(source), sourceOutput, sourceOutputSide, checkSourceOutputSlot(sourceOutput), sourceResult)
                      syncResult(sink, sinkSide, checkSinkSlot(sink), sinkOutput, sinkOutputSide, checkSinkOutputSlot(sinkOutput), sinkResult)
                      true
                    } else {
                      false
                    }
                  }
                )
              })
            })
          })
        })
    }
  }

  private def syncResult(inventory: IInventory, inventorySide: ForgeDirection, inventorySlot: Int, output: IInventory, outputSide: ForgeDirection, outputSlot: Option[Int], result: ItemStack, simulate: Boolean = false) = {
    val stack = if (simulate) result.copy() else result
    def decrStackSizeIfInserted(inserted: Boolean): Boolean = {
      if (inserted && !simulate) {
        inventory.decrStackSize(inventorySlot, 1)
      }
      inserted
    }

    def replaceOr(f: () => Boolean) = {
      if (inventorySide == outputSide && outputSlot.getOrElse(inventorySlot) == inventorySlot && inventory.getStackInSlot(inventorySlot).stackSize == 1) {
        if (!simulate) inventory.setInventorySlotContents(inventorySlot, stack)
        true
      }
      else f()
    }

    outputSlot match {
      case None =>
        replaceOr(
          () => decrStackSizeIfInserted(InventoryUtils.insertIntoInventory(stack, output, Some(outputSide.getOpposite), simulate = simulate))
        )
      case Some(slot) =>
        replaceOr(
          () => decrStackSizeIfInserted(InventoryUtils.insertIntoInventorySlot(stack, output, Some(outputSide.getOpposite), slot, simulate = simulate))
        )
    }
  }

  private def withReplayableMove(handlerA: Option[IFluidHandler], handlerB: Option[IFluidHandler], moveFunc: (IFluidHandler, IFluidHandler) => Int, afterMovedFunc: (IFluidHandler, IFluidHandler, IFluidHandler => Unit, IFluidHandler => Unit) => Boolean) = {
    val moved = handlerA.fold(0)(a =>
      handlerB.fold(0)(b => {
        val replayableA = FluidContainerUtils.replayableFluidHandler(a)
        val replayableB = FluidContainerUtils.replayableFluidHandler(b)
        moveFunc(replayableA, replayableB) match {
          case 0 => 0
          case moved =>
            if (afterMovedFunc(a, b, h => FluidContainerUtils.replay(replayableA, h), h => FluidContainerUtils.replay(replayableB, h))) moved else 0
        }
      })
    )
    result(moved > 0, moved)
  }

  private def withMove(handlerA: Option[IFluidHandler], handlerB: Option[IFluidHandler], moveFunc: (IFluidHandler, IFluidHandler) => Int, afterMovedFunc: (IFluidHandler, IFluidHandler) => Boolean) = {
    val moved = handlerA.fold(0)(a =>
      handlerB.fold(0)(b => {
        moveFunc(a, b) match {
          case 0 => 0
          case moved =>
            if (afterMovedFunc(a, b)) moved else 0
        }
      })
    )
    result(moved > 0, moved)
  }

  private def withInventory(side: ForgeDirection, f: IInventory => Array[AnyRef]) =
    InventoryUtils.inventoryAt(position.offset(side)) match {
      case Some(inventory) if inventory.isUseableByPlayer(fakePlayer) && mayInteract(position.offset(side), side.getOpposite) => f(inventory)
      case _ => result(Unit, "no inventory")
    }
}
