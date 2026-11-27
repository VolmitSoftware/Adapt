package art.arcane.adapt.api.world;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Members of one block tag, resolved on first use and kept, for per-move checks where a registry lookup per call is
 * too slow. Build it at any time; the first {@link #contains} needs a running core.
 */
@ApiStatus.Experimental
public final class BlockTagSet {
  private final Id tag;
  private volatile @Nullable Set<Id> members;

  /** A set over the block tag {@code tag}, for example {@code minecraft:fences}. */
  public BlockTagSet(Id tag) {
    this.tag = tag;
  }

  /** True when {@code block} is in the tag. */
  public boolean contains(@Nullable Id block) {
    if (block == null) {
      return false;
    }
    Set<Id> resolved = members;
    if (resolved == null) {
      resolved = Set.copyOf(AdaptApi.platform().host().registries().tagValues(RegistryKind.BLOCK, tag));
      members = resolved;
    }
    return resolved.contains(block);
  }

  /** Forgets the members, so the next {@link #contains} reads the tag again; call it when resources reload. */
  public void invalidate() {
    members = null;
  }
}
