package li.cil.oc.integration.ae2fc;

import appeng.api.AEApi;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.items.AEBaseCell;
import appeng.me.storage.FluidCellInventoryHandler;
import appeng.util.IterationCounter;
import li.cil.oc.api.driver.Converter;
import net.minecraft.item.ItemStack;

import java.util.Map;

public final class ConverterFluidCellInventory implements Converter {
    @Override
    public void convert(final Object value, final Map<Object, Object> output) {
        if (value instanceof ICellInventory) {
            final ICellInventory cell = (ICellInventory) value;
            if (cell.getChannel() != StorageChannel.FLUIDS) {
                return;
            }
            output.put("storedFluidTypes", cell.getStoredItemTypes());
            output.put("storedFluidCount", cell.getStoredItemCount());
            output.put("remainingFluidCount", cell.getRemainingItemCount());
            output.put("remainingFluidTypes", cell.getRemainingItemTypes());

            output.put("totalFluidTypes", cell.getTotalItemTypes());
            output.put(
                    "availableFluids",
                    cell.getAvailableItems(AEApi.instance().storage().createFluidList(), IterationCounter.fetchNewId()));

            output.put("totalBytes", cell.getTotalBytes());
            output.put("freeBytes", cell.getFreeBytes());
            output.put("usedBytes", cell.getUsedBytes());
            output.put("unusedFluidCount", cell.getUnusedItemCount());
            output.put("canHoldNewFluid", cell.canHoldNewItem());

            output.put("name", cell.getItemStack().getDisplayName());
        } else if (value instanceof FluidCellInventoryHandler) {
            convert(((FluidCellInventoryHandler) value).getCellInv(), output);
        } else if ((value instanceof ItemStack) && (((ItemStack) value).getItem() instanceof AEBaseCell)) {
            IMEInventoryHandler<?> inventory = AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory((ItemStack) value, null, StorageChannel.FLUIDS);
            if (inventory instanceof FluidCellInventoryHandler)
                convert(((FluidCellInventoryHandler) inventory).getCellInv(), output);
        }
    }
}
