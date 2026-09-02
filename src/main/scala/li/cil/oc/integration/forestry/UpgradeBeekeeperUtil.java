package li.cil.oc.integration.forestry;

import cpw.mods.fml.common.Loader;
import forestry.api.apiculture.IBeeHousing;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.basic.MTEIndustrialApiary;
import li.cil.oc.util.BlockPosition;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Wrapper class for UpgradeBeekeeper item. Scala doesn't seem to like GT machines,
 * so we wrap most functionality in a static java class
 * */
public final class UpgradeBeekeeperUtil {

    private static final boolean GT5U_NH_LOADED = Loader.isModLoaded("gregtech_nh");
    private UpgradeBeekeeperUtil() {}

    /** Returns an IBeeHousing TileEntity at position pos. Can be an Industrial Apiary */
    public static IBeeHousing getBeeHousingAt(BlockPosition pos) {
        if (pos.world().isEmpty())
            return null;

        World world = pos.world().get();
        TileEntity te = world.getTileEntity(pos.x(), pos.y(), pos.z());
        if (te == null)
            return null;
        if (te instanceof IBeeHousing)
            return (IBeeHousing)te;

        if (!GT5U_NH_LOADED)
            return null;

        // Scala doesn't compile if these checks are of the form (a instanceof B b)
        if (!(te instanceof BaseMetaTileEntity))
            return null;
        BaseMetaTileEntity mte = (BaseMetaTileEntity)te;
        if (!(mte.getMetaTileEntity() instanceof MTEIndustrialApiary))
            return null;
        return (IBeeHousing)mte.getMetaTileEntity();
    }

    /** Returns a Tile Entity for Industrial Apiaries at position pos, or null if none exist */
    public static MTEIndustrialApiary getGTIApiaryAt(BlockPosition pos) {
        if (!GT5U_NH_LOADED || pos.world().isEmpty())
            return null;

        World world = pos.world().get();
        TileEntity te = world.getTileEntity(pos.x(), pos.y(), pos.z());
        if (te == null)
            return null;
        if (!(te instanceof BaseMetaTileEntity))
            return null;
        BaseMetaTileEntity mte = (BaseMetaTileEntity)te;
        if (!(mte.getMetaTileEntity() instanceof MTEIndustrialApiary))
            return null;
        return (MTEIndustrialApiary)mte.getMetaTileEntity();
    }

    public static boolean swapQueen(BlockPosition pos, IInventory hostInv, int slot) {
        IBeeHousing housing = getBeeHousingAt(pos);
        if (housing == null)
            return false;

        ItemStack newQueen = hostInv.getStackInSlot(slot);
        ItemStack oldQueen = housing.getBeeInventory().getQueen();
        housing.getBeeInventory().setQueen(newQueen);
        hostInv.setInventorySlotContents(slot, oldQueen);
        return true;
    }

    public static boolean swapDrone(BlockPosition pos, IInventory hostInv, int slot) {
        IBeeHousing housing = getBeeHousingAt(pos);
        if (housing == null)
            return false;

        ItemStack newDrone = hostInv.getStackInSlot(slot);
        ItemStack oldDrone = housing.getBeeInventory().getDrone();
        housing.getBeeInventory().setDrone(newDrone);
        hostInv.setInventorySlotContents(slot, oldDrone);
        return true;
    }

    public static int getMaxIndustrialUpgradeCount() {
        if (!GT5U_NH_LOADED)
            return 0;
        return MTEIndustrialApiary.getMaxUpgradeCount();
    }

    public static int addIndustrialUpgrade(BlockPosition pos, IInventory hostInv, int slot, int amount) {
        MTEIndustrialApiary iapiary = getGTIApiaryAt(pos);
        if (iapiary == null || amount <= 0)
            return 0;

        ItemStack stackToInstall = hostInv.getStackInSlot(slot);
        if (stackToInstall == null)
            return 0;
        amount = Math.min(amount, stackToInstall.stackSize);

        ItemStack stackToTryPush = stackToInstall.splitStack(amount);
        iapiary.addUpgrade(stackToTryPush);
        int itemsPushed = amount - stackToTryPush.stackSize;
        // Any Upgrades that weren't pushed go back into host inventory
        stackToInstall.stackSize += stackToTryPush.stackSize;

        return itemsPushed;
    }

    public static ItemStack getIndustrialUpgrade(BlockPosition pos, int index){
        if (index < 1 || index > getMaxIndustrialUpgradeCount())
            return null;
        MTEIndustrialApiary iapiary = getGTIApiaryAt(pos);
        if (iapiary == null)
            return null;
        return iapiary.getUpgrade(index - 1);
    }

    public static int removeIndustrialUpgrade(BlockPosition pos, IInventory hostInv, int slot, int index, int amount) {
        if (index < 1 || index > getMaxIndustrialUpgradeCount() || amount <= 0)
            return 0;
        MTEIndustrialApiary iapiary = getGTIApiaryAt(pos);
        if (iapiary == null)
            return 0;

        ItemStack stack = iapiary.getUpgrade(index - 1);
        if (stack == null)
            return 0;
        stack = stack.copy();

        amount = Math.min(amount, stack.stackSize);
        int moved = insertIntoHostInv(hostInv, slot, stack.splitStack(amount));

        iapiary.removeUpgrade(index - 1, moved);
        return moved;
    }

    private static int insertIntoHostInv(IInventory hostInv, int slot, ItemStack stack) {
        if (stack == null)
            return 0;

        final int initialStackSize = stack.stackSize;

        // Try putting in selected slot first
        insertIntoSlot(hostInv, slot, stack);

        if (stack.stackSize <= 0)
            return initialStackSize;

        // Find any stacks to merge with
        for (int i = 0; i < hostInv.getSizeInventory(); i++) {
            ItemStack stackInSlot = hostInv.getStackInSlot(i);
            if (!GTUtility.areStacksEqual(stack, stackInSlot))
                continue;
            insertIntoSlot(hostInv, i, stack);

            if (stack.stackSize <= 0)
                return initialStackSize;
        }

        // Try pushing any remaining items
        for (int i = 0; i < hostInv.getSizeInventory(); i++) {
            insertIntoSlot(hostInv, i, stack);

            if (stack.stackSize <= 0)
                return initialStackSize;
        }

        return initialStackSize - stack.stackSize;
    }

    private static void insertIntoSlot(IInventory inv, int slot, ItemStack stack) {
        ItemStack stackInSlot = inv.getStackInSlot(slot);
        int maxStackSize = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
        if (stackInSlot == null) {
            inv.setInventorySlotContents(slot, stack.splitStack(Math.min(maxStackSize, stack.stackSize)));
        } else if (GTUtility.areStacksEqual(stack, stackInSlot)) {
            int toMove = Math.min(stack.stackSize, Math.max(0, maxStackSize - stackInSlot.stackSize));
            if (toMove > 0) {
                stackInSlot.stackSize += toMove;
                stack.stackSize -= toMove;
            }
        }
    }

}
