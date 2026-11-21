package art.arcane.adapt.client.items.rei;

import art.arcane.adapt.client.items.ItemIndex;
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps Adapt items apart from their base items in REI, exact and fuzzy, through one global comparator that adds a
 * stack's own data to the hash of Adapt's base items and leaves every other item alone. The hash depends on the stack
 * only, so recipe outputs REI indexed before the list arrived still match. REI takes comparators from common plugins,
 * which also load on a server running REI; the comparator reads no client state.
 */
public class AdaptReiComparators implements REICommonPlugin {
    static final EntryComparator<ItemStack> IDENTITY = (context, stack) -> {
        Object key = ItemIndex.instance().adaptKey(stack);
        return key == null ? 0L : key.hashCode();
    };

    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        registry.registerGlobal(IDENTITY);
    }
}
