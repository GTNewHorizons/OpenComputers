package li.cil.oc.integration.ae2fc;

import appeng.api.AEApi;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.util.IterationCounter;
import com.glodblock.github.common.storage.IFluidCellInventory;
import com.glodblock.github.common.storage.IFluidCellInventoryHandler;
import com.glodblock.github.common.storage.IStorageFluidCell;
import li.cil.oc.api.driver.Converter;
import net.minecraft.item.ItemStack;

import java.util.Map;

public final class ConverterFluidCellInventory implements Converter {
    @Override
    public void convert(final Object value, final Map<Object, Object> output) {
        if (value instanceof IFluidCellInventory) {
            final IFluidCellInventory cell = (IFluidCellInventory) value;
            output.put("storedFluidTypes", cell.getStoredFluidTypes());
            output.put("storedFluidCount", cell.getStoredFluidCount());
            output.put("remainingFluidCount", cell.getRemainingFluidCount());
            output.put("remainingFluidTypes", cell.getRemainingFluidTypes());

            output.put("totalFluidTypes", cell.getTotalFluidTypes());
            output.put(
                    "availableFluids",
                    cell.getAvailableItems(AEApi.instance().storage().createFluidList(), IterationCounter.fetchNewId()));

            output.put("totalBytes", cell.getTotalBytes());
            output.put("freeBytes", cell.getFreeBytes());
            output.put("usedBytes", cell.getUsedBytes());
            output.put("unusedFluidCount", cell.getUnusedFluidCount());
            output.put("canHoldNewFluid", cell.canHoldNewFluid());

            output.put("name", cell.getItemStack().getDisplayName());
        } else if (value instanceof IFluidCellInventoryHandler) {
            convert(((IFluidCellInventoryHandler) value).getCellInv(), output);
        } else if ((value instanceof ItemStack) && (((ItemStack) value).getItem() instanceof IStorageFluidCell)) {
            IMEInventoryHandler<?> inventory = AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory((ItemStack) value, null, StorageChannel.FLUIDS);
            if (inventory instanceof IFluidCellInventoryHandler)
                convert(((IFluidCellInventoryHandler) inventory).getCellInv(), output);
        }
    }
}
