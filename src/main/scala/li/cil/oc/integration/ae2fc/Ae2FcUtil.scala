package li.cil.oc.integration.ae2fc

import appeng.api.storage.data.IAEFluidStack
import com.glodblock.github.api.FluidCraftAPI
import com.glodblock.github.loader.ItemAndBlockHolder
import net.minecraft.block.Block
import net.minecraft.item.ItemStack

object Ae2FcUtil {
  def canSeeFluidInNetwork(fluid: IAEFluidStack) = fluid != null && fluid.getFluid != null && !FluidCraftAPI.instance().isBlacklistedInDisplay(fluid.getFluid.getClass)

  def isFluidExportBus(stack: ItemStack): Boolean =
    stack != null && stack.getItem == ItemAndBlockHolder.FLUID_EXPORT_BUS

  def isFluidImportBus(stack: ItemStack): Boolean =
    stack != null && stack.getItem == ItemAndBlockHolder.FLUID_IMPORT_BUS

  def isFluidInterface(stack: ItemStack): Boolean =
    stack != null && Block.getBlockFromItem(stack.getItem) == ItemAndBlockHolder.INTERFACE

  def isPartFluidInterface(stack: ItemStack): Boolean =
    stack != null && stack.getItem == ItemAndBlockHolder.FLUID_INTERFACE

  def isFluidStorageBus(stack: ItemStack): Boolean =
    stack != null && stack.getItem == ItemAndBlockHolder.FLUID_STORAGE_BUS
}
