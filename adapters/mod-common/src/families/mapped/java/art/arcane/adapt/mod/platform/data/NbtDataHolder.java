package art.arcane.adapt.mod.platform.data;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Data holder over a compound tag whose keys are {@code namespace:path} strings, the Bukkit
 * persistent data layout. The store decides where the compound lives and when changes persist.
 */
public final class NbtDataHolder implements DataHolder {
    private final Store store;

    public NbtDataHolder(Store store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public <T> T get(DataKey<T> key) {
        CompoundTag tag = store.read();
        if (tag == null) {
            return null;
        }
        Tag stored = tag.get(key.id().toString());
        if (stored == null) {
            return null;
        }
        DataValue value = NbtValues.fromTag(stored);
        if (value == null || value.type() != key.type()) {
            return null;
        }
        return key.codec().decode(value);
    }

    @Override
    public boolean has(DataKey<?> key) {
        CompoundTag tag = store.read();
        if (tag == null) {
            return false;
        }
        Tag stored = tag.get(key.id().toString());
        DataValue value = stored == null ? null : NbtValues.fromTag(stored);
        return value != null && value.type() == key.type();
    }

    @Override
    public boolean hasAny(Id id) {
        CompoundTag tag = store.read();
        return tag != null && tag.contains(id.toString());
    }

    @Override
    public <T> void set(DataKey<T> key, T value) {
        if (value == null) {
            remove(key.id());
            return;
        }
        Tag encoded = NbtValues.toTag(key.codec().encode(value));
        store.write((CompoundTag tag) -> tag.put(key.id().toString(), encoded));
    }

    @Override
    public void remove(Id id) {
        CompoundTag tag = store.read();
        if (tag == null || !tag.contains(id.toString())) {
            return;
        }
        store.write((CompoundTag writable) -> writable.remove(id.toString()));
    }

    @Override
    public Set<Id> keys() {
        CompoundTag tag = store.read();
        if (tag == null || tag.isEmpty()) {
            return Set.of();
        }
        Set<Id> keys = new LinkedHashSet<>();
        for (String name : tag.getAllKeys()) {
            Id id = Id.tryParse(name);
            if (id != null) {
                keys.add(id);
            }
        }
        return Set.copyOf(keys);
    }

    @Override
    public boolean isEmpty() {
        CompoundTag tag = store.read();
        return tag == null || tag.isEmpty();
    }

    /** Where the compound lives. */
    public interface Store {
        /** Current compound for reading, or null when nothing is stored. Callers do not mutate it. */
        CompoundTag read();

        /** Applies a change to the writable compound, creating it if needed, and persists it. */
        void write(Edit edit);
    }

    @FunctionalInterface
    public interface Edit {
        void apply(CompoundTag tag);
    }
}
