package li.cil.oc.util;

import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class AE2Bridge {
    /**
     * This Java helper performs the safe cast to allow Scala to call this method.
     */
    @SuppressWarnings("unchecked")
    public static void getModernListOfItem(final CraftingCPUCluster cpu, final IItemList<?> list, final CraftingItemList whichList){
        cpu.getModernListOfItem((IItemList<IAEStack<?>>) list, whichList);
    }
}
