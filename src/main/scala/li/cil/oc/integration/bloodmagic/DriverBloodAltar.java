package li.cil.oc.integration.bloodmagic;

import WayofTime.alchemicalWizardry.api.tile.IBloodAltar;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import li.cil.oc.integration.ManagedTileEntityEnvironment;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import WayofTime.alchemicalWizardry.api.soulNetwork.SoulNetworkHandler;
import WayofTime.alchemicalWizardry.api.items.interfaces.IBloodOrb;

public class DriverBloodAltar extends DriverSidedTileEntity {
    @Override
    public Class<?> getTileEntityClass() {
        return IBloodAltar.class;
    }

    @Override
    public ManagedEnvironment createEnvironment(
            final World world, final int x, final int y, final int z, final ForgeDirection side) {
        return new Environment((IBloodAltar) world.getTileEntity(x, y, z));
    }

    public static final class Environment extends ManagedTileEntityEnvironment<IBloodAltar> implements NamedBlock {
        public Environment(final IBloodAltar tileEntity) {
            super(tileEntity, "blood_altar");
        }

        @Override
        public String preferredName() {
            return "blood_altar";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Callback(doc = "function():number -- Get the capacity.")
        public Object[] getCapacity(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getCapacity() };
        }

        @Callback(doc = "function():number -- Get the amount of blood currently contained by this altar.")
        public Object[] getCurrentBlood(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getCurrentBlood() };
        }

        @Callback(doc = "function():number -- Get the current tier.")
        public Object[] getTier(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getTier() };
        }

        @Callback(doc = "function():number -- Get the progress.")
        public Object[] getProgress(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getProgress() };
        }

        @Callback(doc = "function():number -- Get the sacrifice multiplier.")
        public Object[] getSacrificeMultiplier(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getSacrificeMultiplier() };
        }

        @Callback(doc = "function():number -- Get the self sacrifice multiplier.")
        public Object[] getSelfSacrificeMultiplier(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getSelfSacrificeMultiplier() };
        }

        @Callback(doc = "function():number -- Get the orb multiplier.")
        public Object[] getOrbMultiplier(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getOrbMultiplier() };
        }

        @Callback(doc = "function():number -- Get the dislocation multiplier.")
        public Object[] getDislocationMultiplier(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getDislocationMultiplier() };
        }

        @Callback(doc = "function():number -- Get IO buffer capacity.")
        public Object[] getBufferCapacity(final Context context, final Arguments arguments) {
            return new Object[] { tileEntity.getBufferCapacity() };
        }

        @Callback(doc = "function():string -- Returns the owner name of the blood orb.")
        public Object[] getOwnerName(final Context context, final Arguments args) {
            ItemStack orb;
            try {
                orb = getBloodOrb();
            } catch (Exception e) {
                return new Object[] { null, e.getMessage() };
            }
            ;
            String owner = SoulNetworkHandler.getOwnerName(orb);
            return new Object[] { owner };
        }

        @Callback(doc = "function():number -- Returns the current LP in the owner's soul network.")
        public Object[] getLP(final Context context, final Arguments args) {
            ItemStack orb;
            try {
                orb = getBloodOrb();
            } catch (Exception e) {
                return new Object[] { null, e.getMessage() };
            }
            ;
            String owner = SoulNetworkHandler.getOwnerName(orb);
            if (owner == null || owner.isEmpty()) {
                return new Object[] { 0, "Orb has no bound owner" };
            }
            int currentLP = SoulNetworkHandler.getCurrentEssence(owner);
            return new Object[] { currentLP };
        }

        @Callback(doc = "function():number -- Returns the maximum LP capacity for this orb's tier.")
        public Object[] getMaxLP(final Context context, final Arguments args) {
            ItemStack orb;
            try {
                orb = getBloodOrb();
            } catch (Exception e) {
                return new Object[] { null, e.getMessage() };
            }
            ;
            int maxLP = ((IBloodOrb) orb.getItem()).getMaxEssence();
            return new Object[] { maxLP };
        }

        @Callback(doc = "function():number -- Returns the orb's tier level.")
        public Object[] getOrbLevel(final Context context, final Arguments args) {
            ItemStack orb;
            try {
                orb = getBloodOrb();
            } catch (Exception e) {
                return new Object[] { null, e.getMessage() };
            }
            ;
            int level = ((IBloodOrb) orb.getItem()).getOrbLevel();
            return new Object[] { level };
        }

        private ItemStack getBloodOrb() throws Exception {
            if (!(tileEntity instanceof IInventory)) {
                throw new Exception("Blood Altar does not contain a blood orb");
            }
            IInventory inventory = (IInventory) tileEntity;
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                ItemStack stackInSlot = inventory.getStackInSlot(slot);
                if (stackInSlot != null && stackInSlot.getItem() instanceof IBloodOrb) {
                    return stackInSlot;
                }
            }
            throw new Exception("Blood Altar does not contain a blood orb");
        }
    }
}
