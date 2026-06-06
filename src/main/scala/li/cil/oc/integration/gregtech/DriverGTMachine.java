package li.cil.oc.integration.gregtech;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.driver.SidedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.integration.ManagedTileEntityEnvironment;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public final class DriverGTMachine implements SidedBlock {

    @Override
    public boolean worksWith(final World world, final int x, final int y, final int z,
            final ForgeDirection side) {
        final TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof IGregTechTileEntity && ((IGregTechTileEntity) te).canAccessData();
    }

    @Override
    public ManagedEnvironment createEnvironment(final World world, final int x, final int y, final int z,
            final ForgeDirection side) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity) {
            return new Environment((IGregTechTileEntity) te);
        }
        return null;
    }

    public static final class Environment
            extends ManagedTileEntityEnvironment<IGregTechTileEntity>
            implements NamedBlock {

        public Environment(final IGregTechTileEntity tileEntity) {
            super(tileEntity, "gt_machine");
        }

        // ── NamedBlock ────────────────────────────────────────────────────
        @Override
        public String preferredName() {
            return "gt_machine";
        }

        @Override
        public int priority() {
            return 0;
        }

        // ── Callbacks ─────────────────────────────────────────────────────

        @Callback(doc = "function():string -- Returns the internal registry name of this GregTech machine "
                + "(e.g. \"multimachine.blackholecompressor\").")
        public Object[] getName(final Context context, final Arguments args) {
            if (tileEntity.canAccessData() && tileEntity.getMetaTileEntity() != null) {
                return new Object[] { tileEntity.getMetaTileEntity().getMetaName() };
            }
            return new Object[] { null };
        }

        @Callback(doc = "function(enabled:boolean):boolean -- Enables or disables machine work. "
                + "Returns the resulting isAllowedToWork state.")
        public Object[] setWorkAllowed(final Context context, final Arguments args) {
            final boolean enabled = args.checkBoolean(0);
            if (enabled) {
                tileEntity.enableWorking();
            } else {
                tileEntity.disableWorking();
            }
            return new Object[] { tileEntity.isAllowedToWork() };
        }

        @Callback(doc = "function():boolean -- Returns true if the machine is allowed to work.")
        public Object[] isWorkAllowed(final Context context, final Arguments args) {
            return new Object[] { tileEntity.isAllowedToWork() };
        }

        @Callback(doc = "function():number -- Returns the current recipe progress in ticks.")
        public Object[] getWorkProgress(final Context context, final Arguments args) {
            return new Object[] { tileEntity.getProgress() };
        }

        @Callback(doc = "function():number -- Returns the total recipe duration in ticks.")
        public Object[] getWorkMaxProgress(final Context context, final Arguments args) {
            return new Object[] { tileEntity.getMaxProgress() };
        }

        @Callback(doc = "function():boolean -- Returns true if the machine is currently active (running a recipe).")
        public Object[] isActive(final Context context, final Arguments args) {
            return new Object[] { tileEntity.isActive() };
        }
    }
}

