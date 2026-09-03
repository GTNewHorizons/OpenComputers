package li.cil.oc.integration.gregtech;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.DriverSidedTileEntity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import li.cil.oc.integration.ManagedTileEntityEnvironment;
import tectech.thing.metaTileEntity.multi.bec.MTEBECDiode;

public final class DriverBECDiode extends DriverSidedTileEntity {

  @Override
  public Class<?> getTileEntityClass() {
    return IGregTechTileEntity.class;
  }

  @Override
  public boolean worksWith(World world, int x, int y, int z, ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if (!(te instanceof IGregTechTileEntity)) return false;
    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
    return mte instanceof MTEBECDiode;
  }

  @Override
  public ManagedEnvironment createEnvironment(World world, int x, int y, int z, ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    MTEBECDiode mte = (MTEBECDiode) ((IGregTechTileEntity) te).getMetaTileEntity();
    return new Environment(mte);
  }

  public static final class Environment extends ManagedTileEntityEnvironment<MTEBECDiode> implements NamedBlock {

    public Environment(MTEBECDiode mte) {
      super(mte, "bec_diode");
    }

    @Override
    public String preferredName() {
      return "bec_diode";
    }

    @Override
    public int priority() {
      return 10;
    }

    @Callback(doc = "function(slot:number):string -- Returns the name of the fluid used as condensate filter in the given slot, or nil if no filter is set.")
    public Object[] getCondensateFilterAt(Context context, Arguments args) {
      int slot = getFilterSlot(args.checkInteger(0));
      Fluid filter = tileEntity.getCondensateFilterAt(slot);
      return new Object[] { filter == null ? null : FluidRegistry.getFluidName(filter) };
    }

    @Callback(doc = "function(slot:number[, fluidName:string]) -- Sets the condensate filter in the given slot to the given fluid name, or clears it if nil.")
    public Object[] setCondensateFilterAt(Context context, Arguments args) {
      int slot = getFilterSlot(args.checkInteger(0));
      String name = args.optString(1, null);
      if (name == null) {
        tileEntity.setCondensateFilterAt(slot, null);
        return null;
      }
      Fluid fluid = FluidRegistry.getFluid(name);
      if (fluid == null) {
        throw new IllegalArgumentException("Unknown fluid: " + name);
      }
      tileEntity.setCondensateFilterAt(slot, fluid);
      return null;
    }

    private int getFilterSlot(int luaSlot) {
      int filterCount = tileEntity.getCondensateFilterCount();
      if (luaSlot < 1 || luaSlot > filterCount) {
        throw new IllegalArgumentException("Filter slot must be between 1 and " + filterCount);
      }
      return luaSlot - 1;
    }
  }
}
