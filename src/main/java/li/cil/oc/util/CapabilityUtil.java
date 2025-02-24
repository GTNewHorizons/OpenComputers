package li.cil.oc.util;

import com.gtnewhorizon.gtnhlib.capability.Capabilities;
import cpw.mods.fml.common.Loader;
import net.minecraft.tileentity.TileEntity;

public final class CapabilityUtil {

    private static final boolean isGTNHLibLoaded = Loader.isModLoaded("gtnhlib");

    public static <T> boolean hasCapability(TileEntity tileEntity, Class<T> capability) {
        if (isGTNHLibLoaded) {
            return Capabilities.getCapability(tileEntity, capability) != null;
        } else {
            return tileEntity != null && capability.isAssignableFrom(tileEntity.getClass());
        }
    }

    public static <T> T getCapability(TileEntity tileEntity, Class<T> capability) {
        if (isGTNHLibLoaded) {
            return Capabilities.getCapability(tileEntity, capability);
        } else {
            if (tileEntity != null && capability.isAssignableFrom(tileEntity.getClass())) {
                return capability.cast(tileEntity);
            } else {
                return null;
            }
        }
    }
}
