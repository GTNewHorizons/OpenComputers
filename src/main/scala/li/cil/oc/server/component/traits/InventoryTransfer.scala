package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component._
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.InventoryUtils

trait InventoryTransfer extends traits.WorldAware with traits.SideRestricted {
  // Return None on success, else Some("failure reason")
  def onTransferContents(): Option[String]

  def fluidTransferRate(): Int;

  @Callback(doc = """function(sourceSide:number, sinkSide:number[, count:number[, sourceSlot:number[, sinkSlot:number]]]):number -- Transfer some items between two inventories.""")
  def transferItem(context: Context, args: Arguments): Array[AnyRef] = {
    val sourceSide = checkSideForAction(args, 0)
    val sourcePos = position.offset(sourceSide)
    val sinkSide = checkSideForAction(args, 1)
    val sinkPos = position.offset(sinkSide)
    val count = args.optItemCount(2)

    onTransferContents() match {
      case Some(reason) =>
        result(Unit, reason)
      case _ =>
        val extractor = if (args.count > 3) {
          val sourceSlot = args.checkSlot(InventoryUtils.inventoryAt(sourcePos).getOrElse(throw new IllegalArgumentException("no inventory")), 3)
          val sinkSlot = args.optSlot(InventoryUtils.inventoryAt(sinkPos).getOrElse(throw new IllegalArgumentException("no inventory")), 4, -1)

          InventoryUtils.getTransferBetweenInventoriesSlotsAt(sourcePos, sourceSide.getOpposite, sourceSlot, sinkPos, Option(sinkSide.getOpposite), if (sinkSlot < 0) None else Option(sinkSlot), count)
        }
        else
          InventoryUtils.getTransferBetweenInventoriesAt(sourcePos, sourceSide.getOpposite, sinkPos, Option(sinkSide.getOpposite), count)

        Option(extractor) match {
          case Some(ex) => result(ex())
          case _ => result(Unit, "no inventory")
        }
    }
  }

  @Callback(doc = """function(sourceSide:number, sinkSide:number[, count:number [, sourceTank:number]]):boolean, number -- Transfer some fluid between two tanks. Returns operation result and filled amount""")
  def transferFluid(context: Context, args: Arguments): Array[AnyRef] = {
    val sourceSide = checkSideForAction(args, 0)
    val sourcePos = position.offset(sourceSide)
    val sinkSide = checkSideForAction(args, 1)
    val sinkPos = position.offset(sinkSide)
    val count = args.optFluidCount(2)
    val sourceTank = args.optInteger(3, -1)

    onTransferContents() match {
      case Some(reason) =>
        result(Unit, reason)
      case _ =>
        val fluidTransferRate = this.fluidTransferRate()
        if (fluidTransferRate == 0) {
          return result(Unit, "device has fluid transfer rate of 0")
        }
        val moved = FluidUtils.transferBetweenFluidHandlersAt(sourcePos, sourceSide.getOpposite, sinkPos, sinkSide.getOpposite, count, sourceTank)
        val delay = moved.toDouble / fluidTransferRate.toDouble - 0.05
        if (delay > 0) context.pause(delay)
        result(moved > 0, moved)
    }
  }

  @Callback(doc = """function():number -- Returns the fluid transfer rate in liters per second.""")
  def getFluidTransferRate(context: Context, args: Arguments): Array[AnyRef] = {
    result(fluidTransferRate())
  }
}
