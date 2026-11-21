package art.arcane.adapt.client.items.rei;

import art.arcane.adapt.client.items.ItemIndex;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds the server's Adapt items to REI's entry list, at reload and again whenever the list changes, and keeps them
 * apart from their base items, exact and fuzzy, through one global comparator that adds a stack's own data to the hash
 * of Adapt's base items and leaves every other item alone. The hash depends on the stack only, so recipe outputs REI
 * indexed before the list arrived still match. Forge and NeoForge load a subclass annotated for their plugin scan.
 */
public class AdaptReiPlugin implements REIClientPlugin {
    private static final EntryComparator<ItemStack> IDENTITY = (context, stack) -> {
        Object key = ItemIndex.instance().adaptKey(stack);
        return key == null ? 0L : key.hashCode();
    };
    private static final AtomicBoolean LISTENING = new AtomicBoolean();
    private static final Set<EntryStack<?>> ADDED = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        registry.registerGlobal(IDENTITY);
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        if (LISTENING.compareAndSet(false, true)) {
            ItemIndex.instance().listen(AdaptReiPlugin::refresh);
        }
        synchronized (ADDED) {
            ADDED.clear();
            List<EntryStack<ItemStack>> entries = entries();
            ADDED.addAll(entries);
            registry.addEntries(entries);
        }
    }

    private static List<EntryStack<ItemStack>> entries() {
        List<EntryStack<ItemStack>> entries = new ArrayList<>();
        for (ItemStack stack : ItemIndex.instance().stacks()) {
            entries.add(EntryStacks.of(stack));
        }
        return entries;
    }

    private static void refresh() {
        if (PluginManager.areAnyReloading()) {
            return;
        }
        EntryRegistry registry = EntryRegistry.getInstance();
        synchronized (ADDED) {
            if (!ADDED.isEmpty()) {
                registry.removeEntryIf((EntryStack<?> entry) -> ADDED.contains(entry));
                ADDED.clear();
            }
            List<EntryStack<ItemStack>> entries = entries();
            ADDED.addAll(entries);
            registry.addEntries(entries);
        }
        registry.refilter();
    }
}
