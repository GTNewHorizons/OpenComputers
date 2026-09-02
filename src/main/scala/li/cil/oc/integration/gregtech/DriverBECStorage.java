package li.cil.oc.integration.gregtech;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import it.unimi.dsi.fastutil.Pair;
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

import li.cil.oc.integration.ManagedTileEntityEnvironment;
import tectech.thing.metaTileEntity.multi.bec.MTEBECStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DriverBECStorage extends DriverSidedTileEntity {

  @Override
  public Class<?> getTileEntityClass() {
    return IGregTechTileEntity.class;
  }

  @Override
  public boolean worksWith(World world, int x, int y, int z, ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if (!(te instanceof IGregTechTileEntity)) return false;
    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
    return mte instanceof MTEBECStorage;
  }

  @Override
  public ManagedEnvironment createEnvironment(World world, int x, int y, int z, ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    MTEBECStorage mte = (MTEBECStorage) ((IGregTechTileEntity) te).getMetaTileEntity();
    return new Environment(mte);
  }

  public static final class Environment extends ManagedTileEntityEnvironment<MTEBECStorage> implements NamedBlock {

    public Environment(MTEBECStorage mte) {
      super(mte, "bec_storage");
    }

    @Override
    public String preferredName() {
      return "bec_storage";
    }

    @Override
    public int priority() {
      return 10;
    }

    @Callback(doc = "function():number -- Returns the field strength of this storage node.")
    public Object[] getFieldStrength(Context context, Arguments args) {
      return new Object[] { tileEntity.getFieldStrength() };
    }

    @Callback(doc = "function(strength:number) -- Sets the field strength of this storage node.")
    public Object[] setFieldStrength(Context context, Arguments args) {
      tileEntity.setFieldStrength(args.checkLong(0));
      return null;
    }

    @Callback(doc = "function():table -- Returns stored condensate as a table mapping fluid names to amounts.")
    public Object[] getStoredCondensate(Context context, Arguments args) {
      List<Pair<String, Long>> stored = tileEntity.getStoredCondensate();

      Map<String, Long> result = new HashMap<>();
      for (Pair<String, Long> pair : stored) {
        result.put(pair.left(), pair.right());
      }
      return new Object[] { result };
    }
  }
}
