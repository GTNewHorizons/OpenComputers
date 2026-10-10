package li.cil.oc.integration.gregtech;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import li.cil.oc.integration.ManagedTileEntityEnvironment;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;

final public class DriverDTPF extends DriverSidedTileEntity {

  @Override
  public Class<?> getTileEntityClass() {
    return IGregTechTileEntity.class;
  }

  @Override
  public boolean worksWith(World world, int x, int y, int z, ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if (!(te instanceof IGregTechTileEntity)) return false;
    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
    return mte instanceof MTEPlasmaForge;
  }

  @Override
  public ManagedEnvironment createEnvironment(World world, int x, int y, int z, ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    MTEPlasmaForge mte = (MTEPlasmaForge) ((IGregTechTileEntity) te).getMetaTileEntity();
    return new Environment(mte);
  }

  public static final class Environment extends ManagedTileEntityEnvironment<MTEPlasmaForge> implements NamedBlock {

    public Environment(MTEPlasmaForge mte) {
      super(mte, "dtpf");
    }

    @Override
    public String preferredName() {
      return "dtpf";
    }

    @Override
    public int priority() {
      return 10;
    }

    @Callback(doc = "function():bool -- Returns whether this block has convergence enabled.")
    public Object[] isConvergenceEnabled(Context context, Arguments args) {
      return new Object[] { tileEntity.getConvergenceStatus() };
    }

    @Callback(doc = "function(enabled:bool) -- Sets whether this block has convergence enabled.")
    public Object[] setConvergenceEnabled(Context context, Arguments args) {
      tileEntity.setConvergenceStatus(args.checkBoolean(0));
      return null;
    }

    @Callback(doc = "function():number -- Returns the catalyst tier this block uses when convergence is enabled.")
    public Object[] getConvergenceCatalystType(Context context, Arguments args) {
      return new Object[] { tileEntity.getCatalystTypeForRecipesWithoutCatalyst() };
    }

    @Callback(doc = "function(tier:number) -- Sets catalyst tier used when convergence is enabled.")
    public Object[] setConvergenceCatalystType(Context context, Arguments args) {
      tileEntity.setCatalystTypeForRecipesWithoutCatalyst(args.checkInteger(0));
      return null;
    }
  }
}
