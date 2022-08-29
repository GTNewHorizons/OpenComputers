package li.cil.oc.integration.cofh.energy;

import cofh.api.energy.IEnergyContainerItem;
import java.util.Map;
import li.cil.oc.api.driver.Converter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class ConverterEnergyContainerItem implements Converter {
    @Override
    public void convert(final Object value, final Map<Object, Object> output) {
        if (value instanceof ItemStack) {
            final ItemStack stack = (ItemStack) value;
            final Item item = stack.getItem();
            if (item instanceof IEnergyContainerItem) {
                final IEnergyContainerItem energyItem = (IEnergyContainerItem) item;
                output.put("energy", energyItem.getEnergyStored(stack));
                output.put("maxEnergy", energyItem.getMaxEnergyStored(stack));
            }
        }
    }
}
