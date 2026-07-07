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

    public static final class Environment extends li.cil.oc.api.prefab.ManagedEnvironment implements NamedBlock {

        private final MTEBECStorage mte;

        public Environment(MTEBECStorage mte) {
            this.mte = mte;
            setNode(Network.newNode(this, Visibility.Network).withComponent("bec_storage").create());
        }

        @Override
        public String preferredName() {
            return "bec_storage";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Callback(doc = "function():number -- Returns the field strength of this storage node. Precision loss above 2^53; use getFieldStrengthString for exact value.")
        public Object[] getFieldStrength(Context context, Arguments args) {
            return new Object[] { mte.getFieldStrength() };
        }

        @Callback(doc = "function():string -- Returns the field strength of this storage node as a string for exact representation of large values.")
        public Object[] getFieldStrengthString(Context context, Arguments args) {
            return new Object[] { Long.toUnsignedString(mte.getFieldStrength()) };
        }

        @Callback(doc = "function(strength:number) -- Sets the field strength of this storage node. Precision loss above 2^53; use setFieldStrengthString for exact values.")
        public Object[] setFieldStrength(Context context, Arguments args) {
            mte.setFieldStrength(args.checkLong(0));
            return null;
        }

        @Callback(doc = "function(strength:string) -- Sets the field strength of this storage node from a string for exact representation of large values.")
        public Object[] setFieldStrengthString(Context context, Arguments args) {
            mte.setFieldStrength(Long.parseUnsignedLong(args.checkString(0)));
            return null;
        }

        @Callback(doc = "function():table -- Returns stored condensate as a table mapping fluid names to amounts. Amounts are doubles; precision loss above 2^53.")
        public Object[] getStoredCondensate(Context context, Arguments args) {
            List<Pair<String, Long>> stored = mte.getStoredCondensate();

            Map<String, Long> result = new HashMap<>();
            for (Pair<String, Long> pair : stored) {
                result.put(pair.left(), pair.right());
            }
            return new Object[] { result };
        }
    }
}
