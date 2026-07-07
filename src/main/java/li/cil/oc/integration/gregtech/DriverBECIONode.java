package li.cil.oc.integration.gregtech;

import gregtech.api.enums.NaniteTier;
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
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import tectech.mechanics.boseEinsteinCondensate.CondensateList;
import tectech.thing.metaTileEntity.multi.bec.MTEBECIONode;

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

    public static final class Environment extends li.cil.oc.api.prefab.ManagedEnvironment implements NamedBlock {

        private final MTEBECIONode mte;

        public Environment(MTEBECIONode mte) {
            this.mte = mte;
            setNode(Network.newNode(this, Visibility.Network).withComponent("bec_io_node").create());
        }

        @Override
        public String preferredName() {
            return "bec_io_node";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Callback(direct = true, doc = "function():table -- Returns the condensate types and amounts required for the current recipe, or nil.")
        public Object[] getRequiredCondensate(Context context, Arguments args) {
            return new Object[] { condensateToMap(mte.getRequiredCondensate()) };
        }

        @Callback(direct = true, doc = "function():table -- Returns the condensate types and amounts consumed so far in the current recipe, or nil.")
        public Object[] getConsumedCondensate(Context context, Arguments args) {
            return new Object[] { condensateToMap(mte.getConsumedCondensate()) };
        }

        @Callback(direct = true, doc = "function():table -- Returns the nanite tier this node provides as {name, tier}, or nil.")
        public Object[] getProvidedTier(Context context, Arguments args) {
            return new Object[] { naniteTierToMap(mte.getProvidedTier()) };
        }

        @Callback(direct = true, doc = "function():table -- Returns the nanite tier required by the current recipe as {name, tier}, or nil.")
        public Object[] getRequiredTier(Context context, Arguments args) {
            return new Object[] { naniteTierToMap(mte.getRequiredTier()) };
        }

        @Callback(direct = true, doc = "function():number -- Returns the number of nanites available.")
        public Object[] getAvailableNanites(Context context, Arguments args) {
            return new Object[] { mte.getAvailableNanites() };
        }

        @Callback(direct = true, doc = "function():number -- Returns the number of slowdowns currently applied.")
        public Object[] getSlowdowns(Context context, Arguments args) {
            return new Object[] { mte.getSlowdowns() };
        }

        @Callback(direct = true, doc = "function():number -- Returns the number of parallel recipes currently in progress.")
        public Object[] getParallelRecipesInProgress(Context context, Arguments args) {
            return new Object[] { mte.getParallelRecipesInProgress() };
        }

        @Callback(direct = true, doc = "function():number -- Returns the minimum parallel recipe count.")
        public Object[] getMinParallel(Context context, Arguments args) {
            return new Object[] { mte.getMinParallel() };
        }

        @Callback(direct = true, doc = "function():number -- Returns the maximum parallel recipe count.")
        public Object[] getMaxParallel(Context context, Arguments args) {
            return new Object[] { mte.getMaxParallel() };
        }

        @Callback(direct = true, doc = "function():number -- Returns the manual slowdown value.")
        public Object[] getManualSlowdown(Context context, Arguments args) {
            return new Object[] { mte.getManualSlowdown() };
        }

        @Callback(direct = true, doc = "function():table -- Returns an array of recipe steps. Each step has 'nanite' (table), 'start' (number), 'end' (number), and 'index' (number) fields.")
        public Object[] getRecipeSteps(Context context, Arguments args) {
            List<MTEBECIONode.RecipeStep> steps = mte.getRecipeSteps();
            if (steps == null) return new Object[] { null };

            Object[] result = new Object[steps.size()];
            for (int i = 0; i < steps.size(); i++) {
                result[i] = recipeStepToMap(steps.get(i));
            }
            return new Object[] { result };
        }

        @Callback(direct = true, doc = "function():string -- Returns the machine state: 'idle', 'unpowered', 'assembler-offline', 'nanite-tier-too-low', 'paused-step', 'paused-immediate', 'crafting', or 'internal-error'.")
        public Object[] getState(Context context, Arguments args) {
            return new Object[] { mte.getState() };
        }

        @Callback(doc = "function(min:number) -- Sets the minimum parallel recipe count.")
        public Object[] setMinParallel(Context context, Arguments args) {
            mte.setMinParallel(args.checkInteger(0));
            return null;
        }

        @Callback(doc = "function(max:number) -- Sets the maximum parallel recipe count.")
        public Object[] setMaxParallel(Context context, Arguments args) {
            mte.setMaxParallel(args.checkInteger(0));
            return null;
        }

        @Callback(doc = "function(divisor:number) -- Sets the speed divisor.")
        public Object[] setSpeedDivisor(Context context, Arguments args) {
            mte.setSpeedDivisor(args.checkInteger(0));
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
