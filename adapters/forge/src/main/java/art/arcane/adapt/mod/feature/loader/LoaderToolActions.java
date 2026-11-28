package art.arcane.adapt.mod.feature.loader;

import art.arcane.adapt.platform.item.ToolKind;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Forge's digging tool actions, which modded tools such as paxels answer for each kind they dig as. */
public final class LoaderToolActions {
    private static final Map<ToolKind, ToolAction> DIG = Map.of(ToolKind.PICKAXE, ToolActions.PICKAXE_DIG,
            ToolKind.AXE, ToolActions.AXE_DIG, ToolKind.SHOVEL, ToolActions.SHOVEL_DIG, ToolKind.HOE, ToolActions.HOE_DIG,
            ToolKind.SWORD, ToolActions.SWORD_DIG);

    private LoaderToolActions() {
    }

    public static Set<ToolKind> kinds(ItemStack stack) {
        if (stack.isEmpty()) {
            return Set.of();
        }
        Set<ToolKind> out = EnumSet.noneOf(ToolKind.class);
        for (Map.Entry<ToolKind, ToolAction> entry : DIG.entrySet()) {
            if (stack.canPerformAction(entry.getValue())) {
                out.add(entry.getKey());
            }
        }
        return out.isEmpty() ? Set.of() : Set.copyOf(out);
    }
}
