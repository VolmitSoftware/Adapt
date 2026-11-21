package art.arcane.adapt.client.items;

import art.arcane.adapt.client.net.ItemSink;
import art.arcane.adapt.client.protocol.ItemDescriptor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Adapt items the server lists for recipe viewers, rebuilt as the exact stacks its recipes produce. Installed as
 * the network {@link ItemSink} when a recipe viewer is present, so only then does the client negotiate ITEMS. Every
 * {@code CustomItems} frame goes to {@link #items(List)} and {@link #reset()} runs when the session ends; a list
 * replaces the previous one when its terminating empty frame arrives. Viewer plugins read
 * {@link #stacks()} and {@link #identity(ItemStack)} from any thread and get {@link #listen(Runnable) change callbacks}
 * on the client thread. Nothing here touches client classes until a list arrives, so viewer plugins on a dedicated
 * server read an empty index.
 */
public final class ItemIndex implements ItemSink {
    private static final ItemIndex INSTANCE = new ItemIndex();
    /**
     * Base items of Adapt's own items with default settings. JEI fixes its subtypes before the first list of a session
     * arrives, so these let it tell Adapt items apart on a client's first connection; bases from other settings are
     * remembered from then on.
     */
    private static final List<String> DEFAULT_BASES = List.of("minecraft:snowball", "minecraft:stick", "minecraft:bundle",
        "minecraft:note_block", "minecraft:firework_star", "minecraft:clay_ball", "minecraft:brick", "minecraft:iron_hoe",
        "minecraft:tripwire_hook", "minecraft:redstone_torch", "minecraft:ender_eye", "minecraft:ender_pearl",
        "minecraft:shield", "minecraft:potion", "minecraft:lingering_potion", "minecraft:scrape_pottery_sherd",
        "minecraft:plenty_pottery_sherd");
    private static final List<String> VIEWER_TYPES = List.of("mezz.jei.api.IModPlugin", "dev.emi.emi.api.EmiPlugin",
        "me.shedaniel.rei.api.client.plugins.REIClientPlugin");

    private final ItemBatch batch = new ItemBatch();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private Set<String> rememberedBases;
    private volatile Set<Item> defaultItems;

    private ItemIndex() {
    }

    public static ItemIndex instance() {
        return INSTANCE;
    }

    /** True when JEI, EMI or REI is installed, checked without loading their classes' initializers. */
    public static boolean viewerPresent() {
        for (String type : VIEWER_TYPES) {
            try {
                Class.forName(type, false, ItemIndex.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError absent) {
                continue;
            }
        }
        return false;
    }

    /** One {@code CustomItems} frame, from any thread. */
    @Override
    public void items(List<ItemDescriptor> items) {
        Optional<List<ItemDescriptor>> complete = batch.accept(List.copyOf(items));
        complete.ifPresent((List<ItemDescriptor> list) -> ClientItems.execute(() -> commit(list)));
    }

    /** Forgets the list and any partial one, for a disconnect or a withdrawn capability; from any thread. */
    @Override
    public void reset() {
        batch.clear();
        if (snapshot != Snapshot.EMPTY) {
            ClientItems.execute(() -> replace(Snapshot.EMPTY));
        }
    }

    /** Runs {@code listener} on the client thread after every change of the list. */
    public void listen(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Fresh copies of the listed stacks in server order. */
    public List<ItemStack> stacks() {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : snapshot.stacks()) {
            copies.add(stack.copy());
        }
        return copies;
    }

    /** Base items of the current list. */
    public Set<Item> bases() {
        return snapshot.byItem().keySet();
    }

    /** Base items of the current list, of Adapt's default items and of every list seen before on this client. */
    public synchronized Set<Item> knownBases() {
        Set<Item> bases = new LinkedHashSet<>(bases());
        Set<String> ids = new LinkedHashSet<>(DEFAULT_BASES);
        ids.addAll(remembered());
        for (String id : ids) {
            ItemStacks.item(id).ifPresent(bases::add);
        }
        return bases;
    }

    /**
     * What tells a stack apart from its plain item without the list: its custom data, else all its components (its tag
     * on 1.20.1); null for a plain stack. Viewers that fix identities before the list arrives use this.
     */
    public static Object identityKey(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : ItemStacks.identityKey(stack);
    }

    /**
     * {@link #identityKey(ItemStack)} for stacks of Adapt's default base items and of the current list's base items;
     * null for every other item, so viewers that compare all items stay unchanged for them.
     */
    public Object adaptKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        return defaultBases().contains(item) || snapshot.byItem().containsKey(item) ? ItemStacks.identityKey(stack) : null;
    }

    /**
     * The Adapt id of a listed stack, matched on its custom data (or on all its components when the listed stack has no
     * custom data); null for anything else. Cheap for unrelated items.
     */
    public String identity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        List<Entry> entries = snapshot.byItem().get(stack.getItem());
        if (entries == null) {
            return null;
        }
        Object key = ItemStacks.identityKey(stack);
        if (key == null) {
            return null;
        }
        for (Entry entry : entries) {
            if (entry.key().equals(key)) {
                return entry.id();
            }
        }
        return null;
    }

    private Set<Item> defaultBases() {
        Set<Item> resolved = defaultItems;
        if (resolved == null) {
            Set<Item> items = new HashSet<>();
            for (String id : DEFAULT_BASES) {
                ItemStacks.item(id).ifPresent(items::add);
            }
            resolved = Set.copyOf(items);
            defaultItems = resolved;
        }
        return resolved;
    }

    private void commit(List<ItemDescriptor> descriptors) {
        List<ItemStack> stacks = new ArrayList<>();
        Map<Item, List<Entry>> byItem = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (ItemDescriptor descriptor : descriptors) {
            if (!ids.add(descriptor.id())) {
                continue;
            }
            ItemStack stack = ClientItems.decode(descriptor);
            Object key = stack.isEmpty() ? null : ItemStacks.identityKey(stack);
            if (key == null) {
                continue;
            }
            stacks.add(stack);
            byItem.computeIfAbsent(stack.getItem(), (Item ignored) -> new ArrayList<>()).add(new Entry(descriptor.id(), key));
        }
        remember(byItem.keySet());
        replace(new Snapshot(List.copyOf(stacks), Map.copyOf(byItem)));
    }

    private void replace(Snapshot next) {
        snapshot = next;
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private synchronized void remember(Set<Item> bases) {
        Set<String> known = remembered();
        boolean grew = false;
        for (Item base : bases) {
            grew |= known.add(ItemStacks.id(base));
        }
        if (grew) {
            new ItemBases(ClientItems.basesFile()).save(known);
        }
    }

    private Set<String> remembered() {
        if (rememberedBases == null) {
            rememberedBases = new ItemBases(ClientItems.basesFile()).load();
        }
        return rememberedBases;
    }

    private record Entry(String id, Object key) {
    }

    private record Snapshot(List<ItemStack> stacks, Map<Item, List<Entry>> byItem) {
        private static final Snapshot EMPTY = new Snapshot(List.of(), Map.of());
    }
}
