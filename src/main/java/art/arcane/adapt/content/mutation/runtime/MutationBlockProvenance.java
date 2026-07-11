package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.xp.XpProvenance;
import com.jeff_media.customblockdata.CustomBlockData;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

final class MutationBlockProvenance {
  private final NamespacedKey temporaryOwnerKey;
  private final NamespacedKey temporaryOriginalKey;
  private final NamespacedKey temporaryExpiresKey;

  MutationBlockProvenance() {
    temporaryOwnerKey = new NamespacedKey(Adapt.instance, "mutation-temporary-owner");
    temporaryOriginalKey = new NamespacedKey(Adapt.instance, "mutation-temporary-original");
    temporaryExpiresKey = new NamespacedKey(Adapt.instance, "mutation-temporary-expires");
  }

  boolean isPlayerPlaced(Block block) {
    return block != null && XpProvenance.hasPermanentPlayerModification(block);
  }

  void markTemporary(Block block, String ownerId, String originalData, long expiresAt) {
    if (block == null || ownerId == null || originalData == null) {
      return;
    }
    CustomBlockData data = new CustomBlockData(block, Adapt.instance);
    data.set(temporaryOwnerKey, PersistentDataType.STRING, ownerId);
    data.set(temporaryOriginalKey, PersistentDataType.STRING, originalData);
    data.set(temporaryExpiresKey, PersistentDataType.LONG, expiresAt);
  }

  boolean isTemporary(Block block) {
    if (block == null || !CustomBlockData.hasCustomBlockData(block, Adapt.instance)) {
      return false;
    }
    CustomBlockData data = new CustomBlockData(block, Adapt.instance);
    return data.has(temporaryOwnerKey, PersistentDataType.STRING)
        || data.has(temporaryOriginalKey, PersistentDataType.STRING)
        || data.has(temporaryExpiresKey, PersistentDataType.LONG);
  }

  TemporaryMarker temporary(Block block) {
    if (!isTemporary(block)) {
      return null;
    }
    CustomBlockData data = new CustomBlockData(block, Adapt.instance);
    String ownerId = data.get(temporaryOwnerKey, PersistentDataType.STRING);
    String original = data.get(temporaryOriginalKey, PersistentDataType.STRING);
    Long expires = data.get(temporaryExpiresKey, PersistentDataType.LONG);
    if (ownerId == null || original == null || expires == null) {
      return null;
    }
    return new TemporaryMarker(ownerId, original, expires);
  }

  void clearTemporary(Block block) {
    if (block == null || !CustomBlockData.hasCustomBlockData(block, Adapt.instance)) {
      return;
    }
    CustomBlockData data = new CustomBlockData(block, Adapt.instance);
    data.remove(temporaryOwnerKey);
    data.remove(temporaryOriginalKey);
    data.remove(temporaryExpiresKey);
  }

  record TemporaryMarker(String ownerId, String originalData, long expiresAt) {
  }
}
