package li.cil.oc.integration.thaumcraft;

import com.google.common.base.Preconditions;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import li.cil.oc.integration.ManagedTileEntityEnvironment;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import thaumcraft.common.tiles.TileInfusionMatrix;

public class DriverInfusionMatrix extends DriverSidedTileEntity {
    @Override
    public ManagedEnvironment createEnvironment(
            final World world, final int x, final int y, final int z, final ForgeDirection side) {
        return new Environment((TileInfusionMatrix) world.getTileEntity(x, y, z));
    }

    @Override
    public Class<?> getTileEntityClass() {
        return TileInfusionMatrix.class;
    }

    public static final class Environment extends ManagedTileEntityEnvironment<TileInfusionMatrix> implements NamedBlock {
        public Environment(final TileInfusionMatrix tileEntity) {
            super(tileEntity, "infusion_matrix");
        }

        @Override
        public String preferredName() {
            return "infusion_matrix";
        }

        @Override
        public int priority() {
            return 1;
        }

        @Callback(doc = "function():bool -- Return whether the matrix is active")
        public Object[] isActive(final Context context, final Arguments args) {
            return new Object[]{tileEntity.active};
        }

        @Callback(doc = "function():bool -- Return whether the matrix is crafting")
        public Object[] isCrafting(final Context context, final Arguments args) {
            return new Object[]{tileEntity.crafting};
        }

        @Callback(doc = "function():number -- Get the instability.")
        public Object[] getInstability(final Context context, final Arguments args) {
            return new Object[]{tileEntity.instability};
        }

        @Callback(doc = "function():number -- Get the symmetry.")
        public Object[] getSymmetry(final Context context, final Arguments args) {
            return new Object[]{tileEntity.symmetry};
        }
    }
}
