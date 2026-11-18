package art.arcane.adapt.mod.platform.data;

import net.minecraft.nbt.CompoundTag;

/** Implemented on every entity by the platform mixins. */
public interface AdaptEntityData {
    String KEY = "AdaptData";

    /** Stored compound, or null when nothing was ever stored. */
    CompoundTag adapt$data();

    CompoundTag adapt$dataOrCreate();

    void adapt$setData(CompoundTag tag);

    boolean adapt$isTransient();

    void adapt$setTransient(boolean value);
}
