package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.InventoryUtils
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids.{FluidContainerRegistry, FluidStack, IFluidContainerItem}

trait WorldFluidContainerAnalytics extends WorldAware with SideRestricted with NetworkAware {

  @Callback(doc = """function(side:number, slot:number):number -- Get the capacity of the fluid container in the specified slot of the inventory on the specified side of the device.""")
  def getContainerCapacityInSlot(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => {
      val stack = inventory.getStackInSlot(args.checkSlot(inventory, 1))
      withFluidInfo(stack, (_, capacity) => result(capacity))
    })
  }
  else result(Unit, "not enabled in config")

  @Callback(doc = """function(side:number, slot:number):number -- Get the capacity the fluid container in the specified slot of the inventory on the specified side of the device.""")
  def getContainerLevelInSlot(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => {
      val stack = inventory.getStackInSlot(args.checkSlot(inventory, 1))
      withFluidInfo(stack, (fluid, _) => result(fluid.amount))
    })
  }
  else result(Unit, "not enabled in config")

  @Callback(doc = """function(side:number, slot:number):table -- Get a description of the fluid in the fluid container in the specified slot of the inventory on the specified side of the device.""")
  def getFluidInContainerInSlot(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val facing = checkSideForAction(args, 0)
    withInventory(facing, inventory => {
      val stack = inventory.getStackInSlot(args.checkSlot(inventory, 1))
      withFluidInfo(stack, (fluid, _) => result(fluid))
    })
  }
  else result(Unit, "not enabled in config")

  private def withFluidInfo(stack: ItemStack, f: (FluidStack, Int) => Array[AnyRef]) = {
    val fluidInfo = if (FluidContainerRegistry.isFilledContainer(stack)) {
      Option((FluidContainerRegistry.getFluidForFilledItem(stack), FluidContainerRegistry.getContainerCapacity(stack)))
    }
    else if (FluidContainerRegistry.isEmptyContainer(stack)) {
      Option((null, FluidContainerRegistry.getContainerCapacity(stack)))
    }
    else stack.getItem match {
      case from: IFluidContainerItem => Option((from.getFluid(stack), from.getCapacity(stack)))
      case _ => None
    }
    fluidInfo match {
      case Some((fluid, capacity)) => f(fluid, capacity)
      case _ => result(Unit, "item is not a fluid container")
    }
  }


  private def withInventory(side: ForgeDirection, f: IInventory => Array[AnyRef]) =
    InventoryUtils.inventoryAt(position.offset(side)) match {
      case Some(inventory) if inventory.isUseableByPlayer(fakePlayer) && mayInteract(position.offset(side), side.getOpposite) => f(inventory)
      case _ => result(Unit, "no inventory")
    }
}
