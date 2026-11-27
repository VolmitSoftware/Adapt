package art.arcane.adapt.clientui;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

final class SlotLooks {
    private SlotLooks() {
    }

    static void describe(ItemStack stack, JsonObject entry) {
        Identifier model = stack.get(DataComponents.ITEM_MODEL);
        if (model != null) {
            entry.addProperty("model", model.toString());
        }
        Identifier tooltip = stack.get(DataComponents.TOOLTIP_STYLE);
        if (tooltip != null) {
            entry.addProperty("tooltipStyle", tooltip.toString());
        }
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (data != null) {
            entry.addProperty("customModelData", String.valueOf(data));
        }
    }
}
