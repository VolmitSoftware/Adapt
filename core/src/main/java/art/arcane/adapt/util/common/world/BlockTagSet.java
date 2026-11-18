package art.arcane.adapt.util.common.world;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;

/**
 * Members of one block tag, resolved once and kept until {@link #invalidate()} (call it when resources reload).
 * For per-move checks where a registry lookup per call is too slow.
 */
public final class BlockTagSet {
  private final Id tag;
  private volatile Set<Id> members;

  public BlockTagSet(Id tag) {
    this.tag = tag;
  }

  public boolean contains(Id block) {
    if (block == null) {
      return false;
    }
    Set<Id> resolved = members;
    if (resolved == null) {
      resolved = Set.copyOf(AdaptCore.platform().host().registries().tagValues(RegistryKind.BLOCK, tag));
      members = resolved;
    }
    return resolved.contains(block);
  }

  public void invalidate() {
    members = null;
  }
}
