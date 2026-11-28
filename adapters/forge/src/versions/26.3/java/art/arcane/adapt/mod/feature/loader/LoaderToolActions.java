package art.arcane.adapt.mod.feature.loader;

import art.arcane.adapt.platform.item.ToolKind;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Forge 26.3 dropped the digging tool actions; tools there are read from their tags and mining rules. */
public final class LoaderToolActions {
    private LoaderToolActions() {
    }

    public static Set<ToolKind> kinds(ItemStack stack) {
        return Set.of();
    }
}
