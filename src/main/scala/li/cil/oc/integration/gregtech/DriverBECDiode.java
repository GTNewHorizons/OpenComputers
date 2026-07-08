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

    @Callback(doc = "function():string -- Returns the name of the fluid used as condensate filter, or nil if no filter is set.")
    public Object[] getCondensateFilter(Context context, Arguments args) {
      Fluid filter = tileEntity.getCondensateFilter();
      return new Object[] { filter == null ? null : FluidRegistry.getFluidName(filter) };
    }

    @Callback(doc = "function(fluidName:string) -- Sets the condensate filter to the given fluid name.")
    public Object[] setCondensateFilter(Context context, Arguments args) {
      String name = args.optString(0, null);
      if (name == null) {
        tileEntity.setCondensateFilter(null);
        return null;
      }
      Fluid fluid = FluidRegistry.getFluid(name);
      if (fluid == null) {
        throw new IllegalArgumentException("Unknown fluid: " + name);
      }
      tileEntity.setCondensateFilter(fluid);
      return null;
    }
  }
}
