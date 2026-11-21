package art.arcane.adapt.client.items.emi;

import art.arcane.adapt.client.items.ItemIndex;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds the server's Adapt items to EMI's index and keeps them apart from their base items. EMI builds its index and
 * comparisons in a reload, so a changed list asks EMI to reload once it has loaded for this world; before that, EMI's
 * own first reload picks the list up. The reload call is EMI's runtime class, not its API.
 */
@EmiEntrypoint
public final class AdaptEmiPlugin implements EmiPlugin {
    private static final AtomicBoolean LISTENING = new AtomicBoolean();

    @Override
    public void register(EmiRegistry registry) {
        if (LISTENING.compareAndSet(false, true)) {
            ItemIndex.instance().listen(AdaptEmiPlugin::reload);
        }
        ItemIndex index = ItemIndex.instance();
        for (Item base : index.bases()) {
            registry.setDefaultComparison(base, (Comparison previous) -> Comparison.of(
                (EmiStack first, EmiStack second) -> previous.compare(first, second) && Objects.equals(identity(first), identity(second)),
                (EmiStack stack) -> 31 * previous.getHash(stack) + Objects.hashCode(identity(stack))));
        }
        for (ItemStack stack : index.stacks()) {
            registry.addEmiStack(EmiStack.of(stack));
        }
    }

    private static String identity(EmiStack stack) {
        return ItemIndex.instance().identity(stack.getItemStack());
    }

    private static void reload() {
        if (EmiReloadManager.getStatus() != 0) {
            EmiReloadManager.reload();
        }
    }
}
