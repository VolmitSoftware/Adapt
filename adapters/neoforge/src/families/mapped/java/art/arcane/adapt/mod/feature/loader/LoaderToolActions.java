package art.arcane.adapt.mod.feature.loader;

import art.arcane.adapt.platform.item.ToolKind;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** NeoForge's digging item abilities, which modded tools such as paxels answer for each kind they dig as. */
public final class LoaderToolActions {
    private static final Map<ToolKind, ItemAbility> DIG = Map.of(ToolKind.PICKAXE, ItemAbilities.PICKAXE_DIG,
            ToolKind.AXE, ItemAbilities.AXE_DIG, ToolKind.SHOVEL, ItemAbilities.SHOVEL_DIG, ToolKind.HOE,
            ItemAbilities.HOE_DIG, ToolKind.SWORD, ItemAbilities.SWORD_DIG);

    private LoaderToolActions() {
    }

    public static Set<ToolKind> kinds(ItemStack stack) {
        if (stack.isEmpty()) {
            return Set.of();
        }
        Set<ToolKind> out = EnumSet.noneOf(ToolKind.class);
        for (Map.Entry<ToolKind, ItemAbility> entry : DIG.entrySet()) {
            if (stack.canPerformAction(entry.getValue())) {
                out.add(entry.getKey());
            }
        }
        return out.isEmpty() ? Set.of() : Set.copyOf(out);
    }
}
