package art.arcane.adapt.client.items.jei;

import art.arcane.adapt.client.items.ItemIndex;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.platform.value.Id;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds the server's Adapt items to JEI's list. JEI fixes subtype interpreters and recipe output identities when it
 * starts, before the list of a fresh connection arrives, so the interpreters cover Adapt's default base items and every
 * base item this client has seen Adapt items on, and tell stacks apart by their own data rather than by the list.
 * Items JEI's vanilla plugin already interprets keep JEI's interpreter. An item JEI cannot tell apart from its plain
 * base item is left out (until JEI restarts, for a base seen for the first time).
 */
@JeiPlugin
public final class AdaptJeiPlugin implements IModPlugin {
    private static final Identifier ID = ModConversions.identifier(Id.adapt("items"));
    private static final Set<Item> OWN_INTERPRETERS = Set.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION,
        Items.TIPPED_ARROW, Items.ENCHANTED_BOOK, Items.LIGHT, Items.PAINTING, Items.GOAT_HORN, Items.FIREWORK_ROCKET,
        Items.FIREWORK_STAR, Items.SUSPICIOUS_STEW, Items.OMINOUS_BOTTLE, Items.SHIELD, Items.DECORATED_POT);
    private static final ISubtypeInterpreter<ItemStack> IDENTITY = new ISubtypeInterpreter<>() {
        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return ItemIndex.identityKey(stack);
        }
    };
    private static final AtomicBoolean LISTENING = new AtomicBoolean();
    private static volatile IJeiRuntime runtime;
    private static List<ItemStack> shown = List.of();

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (Item base : ItemIndex.instance().knownBases()) {
            if (!OWN_INTERPRETERS.contains(base)) {
                registration.registerSubtypeInterpreter(base, IDENTITY);
            }
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
        shown = List.of();
        if (LISTENING.compareAndSet(false, true)) {
            ItemIndex.instance().listen(AdaptJeiPlugin::refresh);
        }
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        shown = List.of();
    }

    private static void refresh() {
        IJeiRuntime current = runtime;
        if (current == null) {
            return;
        }
        IIngredientManager ingredients = current.getIngredientManager();
        if (!shown.isEmpty()) {
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, shown);
        }
        IIngredientHelper<ItemStack> helper = ingredients.getIngredientHelper(VanillaTypes.ITEM_STACK);
        List<ItemStack> distinct = new ArrayList<>();
        Set<Object> uids = new HashSet<>();
        for (ItemStack stack : ItemIndex.instance().stacks()) {
            Object uid = helper.getUid(stack, UidContext.Ingredient);
            if (!uid.equals(helper.getUid(new ItemStack(stack.getItem()), UidContext.Ingredient)) && uids.add(uid)) {
                distinct.add(stack);
            }
        }
        if (!distinct.isEmpty()) {
            ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, distinct);
        }
        shown = List.copyOf(distinct);
    }
}
