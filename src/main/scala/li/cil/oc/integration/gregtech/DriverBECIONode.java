package li.cil.oc.integration.gregtech;

import gregtech.api.enums.NaniteTier;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
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
import tectech.mechanics.boseEinsteinCondensate.CondensateList;
import tectech.thing.metaTileEntity.multi.bec.MTEBECIONode;
import tectech.thing.metaTileEntity.multi.bec.MTEBECIONode.RecipeStep;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DriverBECIONode extends DriverSidedTileEntity {

  @Override
  public Class<?> getTileEntityClass() {
    return IGregTechTileEntity.class;
  }

  @Override
  public boolean worksWith(World world, int x, int y, int z, ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    if (!(te instanceof IGregTechTileEntity)) return false;
    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
    return mte instanceof MTEBECIONode;
  }

  @Override
  public ManagedEnvironment createEnvironment(World world, int x, int y, int z, ForgeDirection side) {
    TileEntity te = world.getTileEntity(x, y, z);
    MTEBECIONode mte = (MTEBECIONode) ((IGregTechTileEntity) te).getMetaTileEntity();
    return new Environment(mte);
  }

  public static final class Environment extends ManagedTileEntityEnvironment<MTEBECIONode> implements NamedBlock {

    public Environment(MTEBECIONode mte) {
      super(mte, "bec_io_node");
    }

    @Override
    public String preferredName() {
      return "bec_io_node";
    }

    @Override
    public int priority() {
      return 10;
    }

    @Callback(doc = "function():table -- Returns the condensate types and amounts required for the current recipe, or nil.")
    public Object[] getRequiredCondensate(Context context, Arguments args) {
      return new Object[] { condensateToMap(tileEntity.getRequiredCondensate()) };
    }

    @Callback(doc = "function():table -- Returns the condensate types and amounts consumed so far in the current recipe, or nil.")
    public Object[] getConsumedCondensate(Context context, Arguments args) {
      return new Object[] { condensateToMap(tileEntity.getConsumedCondensate()) };
    }

    @Callback(doc = "function():table -- Returns the nanite tier this node provides as {name, tier}, or nil.")
    public Object[] getProvidedTier(Context context, Arguments args) {
      return new Object[] { naniteTierToMap(tileEntity.getProvidedTier()) };
    }

    @Callback(doc = "function():table -- Returns the nanite tier required by the current recipe as {name, tier}, or nil.")
    public Object[] getRequiredTier(Context context, Arguments args) {
      return new Object[] { naniteTierToMap(tileEntity.getRequiredTier()) };
    }

    @Callback(doc = "function():number -- Returns the number of nanites available.")
    public Object[] getAvailableNanites(Context context, Arguments args) {
      return new Object[] { tileEntity.getAvailableNanites() };
    }

    @Callback(doc = "function():number -- Returns the number of slowdowns currently applied.")
    public Object[] getSlowdowns(Context context, Arguments args) {
      return new Object[] { tileEntity.getSlowdowns() };
    }

    @Callback(doc = "function():number -- Returns the number of parallel recipes currently in progress.")
    public Object[] getParallelRecipesInProgress(Context context, Arguments args) {
      return new Object[] { tileEntity.getParallelRecipesInProgress() };
    }

    @Callback(doc = "function():number -- Returns the minimum parallel recipe count.")
    public Object[] getMinParallel(Context context, Arguments args) {
      return new Object[] { tileEntity.getMinParallel() };
    }

    @Callback(doc = "function():number -- Returns the maximum parallel recipe count.")
    public Object[] getMaxParallel(Context context, Arguments args) {
      return new Object[] { tileEntity.getMaxParallel() };
    }

    @Callback(doc = "function():number -- Returns the manual slowdown value.")
    public Object[] getManualSlowdown(Context context, Arguments args) {
      return new Object[] { tileEntity.getManualSlowdown() };
    }

    @Callback(doc = "function():table -- Returns an array of recipe steps. Each step has 'nanite' (table), 'start' (number), 'end' (number), and 'index' (number) fields.")
    public Object[] getRecipeSteps(Context context, Arguments args) {
      List<RecipeStep> steps = tileEntity.getRecipeSteps();
      if (steps == null) return new Object[] { null };

      Object[] result = new Object[steps.size()];
      for (int i = 0; i < steps.size(); i++) {
        result[i] = recipeStepToMap(steps.get(i));
      }
      return new Object[] { result };
    }

    @Callback(doc = "function():string -- Returns the machine state: 'idle', 'unpowered', 'assembler-offline', 'nanite-tier-too-low', 'paused-step', 'paused-immediate', 'crafting', or 'internal-error'.")
    public Object[] getState(Context context, Arguments args) {
      return new Object[] { tileEntity.getState() };
    }

    @Callback(doc = "function(min:number) -- Sets the minimum parallel recipe count.")
    public Object[] setMinParallel(Context context, Arguments args) {
      tileEntity.setMinParallel(args.checkInteger(0));
      return null;
    }

    @Callback(doc = "function(max:number) -- Sets the maximum parallel recipe count.")
    public Object[] setMaxParallel(Context context, Arguments args) {
      tileEntity.setMaxParallel(args.checkInteger(0));
      return null;
    }

    @Callback(doc = "function(divisor:number) -- Sets the speed divisor.")
    public Object[] setSpeedDivisor(Context context, Arguments args) {
      tileEntity.setSpeedDivisor(args.checkInteger(0));
      return null;
    }

    private static Map<String, Long> condensateToMap(CondensateList condensate) {
      if (condensate == null) return null;

      Map<String, Long> result = new HashMap<>();
      for (Object2LongMap.Entry<Fluid> entry : condensate.object2LongEntrySet()) {
        result.put(FluidRegistry.getFluidName(entry.getKey()), entry.getLongValue());
      }
      return result;
    }

    private static Map<String, Object> naniteTierToMap(NaniteTier tier) {
      if (tier == null) return null;

      Map<String, Object> result = new HashMap<>();
      result.put("name", tier.name());
      result.put("tier", tier.getTier());
      return result;
    }

    private static Map<String, Object> recipeStepToMap(MTEBECIONode.RecipeStep step) {
      Map<String, Object> result = new HashMap<>();
      result.put("nanite", naniteTierToMap(step.nanite()));
      result.put("start", step.start());
      result.put("end", step.end());
      result.put("index", step.index());
      return result;
    }
  }
}
