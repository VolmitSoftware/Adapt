package art.arcane.adapt.client.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One Adapt custom item for recipe viewers. {@code itemModelId} may be null; {@code components} is the platform's
 * encoded component or NBT data, opaque to core.
 */
public record ItemDescriptor(String id, String baseItemId, String name, List<String> lore, String itemModelId,
                             int customModelData, byte[] components) {
  public ItemDescriptor {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(baseItemId, "baseItemId");
    Objects.requireNonNull(name, "name");
    lore = List.copyOf(lore);
    components = components.clone();
  }

  @Override
  public byte[] components() {
    return components.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ItemDescriptor that
        && customModelData == that.customModelData
        && id.equals(that.id)
        && baseItemId.equals(that.baseItemId)
        && name.equals(that.name)
        && lore.equals(that.lore)
        && Objects.equals(itemModelId, that.itemModelId)
        && Arrays.equals(components, that.components);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, baseItemId, name, lore, itemModelId, customModelData, Arrays.hashCode(components));
  }

  @Override
  public String toString() {
    return "ItemDescriptor[id=" + id + ", baseItemId=" + baseItemId + ", components=" + components.length + " bytes]";
  }
}
