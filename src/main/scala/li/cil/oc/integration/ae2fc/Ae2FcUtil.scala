package li.cil.oc.integration.ae2fc

import appeng.api.storage.data.IAEFluidStack
import com.glodblock.github.api.FluidCraftAPI

object Ae2FcUtil {
  def canSeeFluidInNetwork(fluid: IAEFluidStack) = fluid != null && fluid.getFluid != null && !FluidCraftAPI.instance().isBlacklistedInDisplay(fluid.getFluid.getClass)
}
