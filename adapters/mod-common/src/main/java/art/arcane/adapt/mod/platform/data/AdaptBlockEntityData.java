package art.arcane.adapt.mod.platform.data;

import net.minecraft.nbt.CompoundTag;

/** Implemented on every block entity by the platform mixins; saved with the block entity under {@link #KEY}. */
public interface AdaptBlockEntityData {
    String KEY = "AdaptData";

    /** Stored compound, or null when nothing was ever stored. */
    CompoundTag adapt$data();

    CompoundTag adapt$dataOrCreate();

    void adapt$setData(CompoundTag tag);
}
