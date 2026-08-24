package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.Adapt;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MutationItemIdentity {
  private static final int SNAPSHOT_MAGIC = 0x414D4352;
  private static final int SNAPSHOT_VERSION = 1;
  private static final int MAX_SNAPSHOT_CHARACTERS = 16_384;
  private static final int MAX_SNAPSHOT_BYTES = 12_288;
  private static final int MAX_SNAPSHOT_MODIFIERS = 32;
  private static final int MAX_SNAPSHOT_KEY_LENGTH = 128;
  private static final int MAX_REPORTED_CORRUPT_SNAPSHOTS = 64;
  private static final double MAX_SNAPSHOT_ATTRIBUTE_AMOUNT = 1_000_000D;

  private final NamespacedKey itemIdKey;
  private final NamespacedKey craftedOwnerKey;
  private final NamespacedKey temperboundBondKey;
  private final NamespacedKey temperboundPieceKey;
  private final NamespacedKey crackedKey;
  private final NamespacedKey crackedAttributesKey;
  private final NamespacedKey masterworkKey;
  private final NamespacedKey brokenKey;
  private final LinkedHashSet<String> reportedCorruptSnapshots = new LinkedHashSet<>();

  MutationItemIdentity(Plugin plugin) {
    itemIdKey = new NamespacedKey(plugin, "mutation-item-id");
    craftedOwnerKey = new NamespacedKey(plugin, "mutation-crafted-owner");
    temperboundBondKey = new NamespacedKey(plugin, "mutation-temperbound-bond");
    temperboundPieceKey = new NamespacedKey(plugin, "mutation-temperbound-piece");
    crackedKey = new NamespacedKey(plugin, "mutation-cracked");
    crackedAttributesKey = new NamespacedKey(plugin, "mutation-cracked-attributes");
    masterworkKey = new NamespacedKey(plugin, "mutation-masterwork");
    brokenKey = new NamespacedKey(plugin, "mutation-broken");
  }

  String ensureItemId(ItemStack item) {
    if (!isDurable(item)) {
      return "";
    }
    PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
    String existing = data.get(itemIdKey, PersistentDataType.STRING);
    if (existing != null && !existing.isBlank()) {
      return existing;
    }
    String itemId = UUID.randomUUID().toString();
    set(item, itemIdKey, itemId);
    return itemId;
  }

  String markCrafted(ItemStack item, UUID ownerId) {
    if (!isDurable(item) || ownerId == null) {
      return "";
    }
    String itemId = ensureItemId(item);
    set(item, craftedOwnerKey, ownerId.toString());
    return itemId;
  }

  boolean wasCraftedBy(ItemStack item, UUID ownerId) {
    if (!isDurable(item) || ownerId == null) {
      return false;
    }
    String owner = get(item, craftedOwnerKey);
    return ownerId.toString().equals(owner);
  }

  String itemId(ItemStack item) {
    return get(item, itemIdKey);
  }

  void attuneArmorPiece(ItemStack item, String bondId, String pieceId) {
    if (!isDurable(item) || bondId == null || pieceId == null) {
      return;
    }
    set(item, itemIdKey, pieceId);
    set(item, temperboundBondKey, bondId);
    set(item, temperboundPieceKey, pieceId);
    setFlag(item, crackedKey, false);
  }

  boolean isAttunedPiece(ItemStack item, String bondId, List<String> pieceIds) {
    String pieceId = attunedPieceId(item, bondId);
    return pieceIds != null && !pieceId.isBlank() && pieceIds.contains(pieceId);
  }

  String attunedPieceId(ItemStack item, String bondId) {
    if (!isDurable(item) || bondId == null || bondId.isBlank()) {
      return "";
    }
    String storedBond = get(item, temperboundBondKey);
    String pieceId = get(item, temperboundPieceKey);
    return bondId.equals(storedBond) && pieceId != null ? pieceId : "";
  }

  void markMasterwork(ItemStack item, String itemId) {
    if (!isDurable(item) || itemId == null || itemId.isBlank()) {
      return;
    }
    set(item, itemIdKey, itemId);
    set(item, masterworkKey, itemId);
  }

  boolean isMasterwork(ItemStack item, String itemId) {
    return itemId != null
        && !itemId.isBlank()
        && itemId.equals(get(item, itemIdKey))
        && itemId.equals(get(item, masterworkKey));
  }

  boolean isMasterwork(ItemStack item) {
    String itemId = get(item, masterworkKey);
    return itemId != null && !itemId.isBlank();
  }

  boolean isBrokenMasterwork(ItemStack item) {
    return isMasterwork(item) && isBroken(item);
  }

  boolean isCracked(ItemStack item) {
    return hasFlag(item, crackedKey);
  }

  void setCracked(ItemStack item, boolean cracked) {
    if (!cracked) {
      clearCracked(item);
      return;
    }
    if (!isArmor(item) || isCracked(item)) {
      return;
    }
    String encoded;
    try {
      encoded = encodeSnapshot(captureSnapshot(item));
    } catch (RuntimeException error) {
      reportCorruptSnapshot(item, "capture", error);
      encoded = encodeSnapshot(tombstone(item));
    }
    suppressCrackedAttributes(item, encoded);
  }

  boolean isBroken(ItemStack item) {
    return hasFlag(item, brokenKey);
  }

  void setBroken(ItemStack item, boolean broken) {
    setFlag(item, brokenKey, broken);
  }

  void copyIdentity(ItemStack source, ItemStack result) {
    if (source == null
        || result == null
        || source.getType().isAir()
        || result.getType().isAir()
        || source.getType() != result.getType()) {
      return;
    }
    copyIdentityValues(source, result);
  }

  boolean copyIdentityForUpgrade(ItemStack source, ItemStack result) {
    if (!isValidEquipmentUpgrade(source, result)) {
      return false;
    }
    copyIdentityValues(source, result);
    return true;
  }

  boolean clearCracked(ItemStack item) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    String encoded = meta.getPersistentDataContainer().get(crackedAttributesKey, PersistentDataType.STRING);
    if (encoded == null || encoded.isBlank()) {
      return false;
    }
    try {
      CrackedSnapshot snapshot = decodeSnapshot(encoded);
      if (!snapshot.restorable() || !matchesBinding(snapshot, item)) {
        throw new IllegalArgumentException("Cracked armor snapshot does not match the current bound item");
      }
      meta.setAttributeModifiers(snapshot.explicitModifiers() ? modifiers(snapshot) : null);
    } catch (RuntimeException error) {
      reportCorruptSnapshot(item, encoded, error);
      return false;
    }
    meta.getPersistentDataContainer().remove(crackedAttributesKey);
    meta.getPersistentDataContainer().remove(crackedKey);
    item.setItemMeta(meta);
    return true;
  }

  void clearBroken(ItemStack item) {
    setFlag(item, brokenKey, false);
  }

  boolean hasEquipmentIdentity(ItemStack item) {
    String temperboundBond = get(item, temperboundBondKey);
    return isMasterwork(item) || (temperboundBond != null && !temperboundBond.isBlank());
  }

  boolean isCrackedArmor(ItemStack item) {
    return isArmor(item) && isCracked(item);
  }

  boolean hasSuppressedCrackedAttributes(ItemStack item) {
    String snapshot = get(item, crackedAttributesKey);
    return isCrackedArmor(item) && snapshot != null && !snapshot.isBlank();
  }

  boolean isTool(ItemStack item) {
    return isDurable(item) && isToolMaterial(item.getType());
  }

  static boolean isToolMaterial(Material material) {
    if (material == null) {
      return false;
    }
    String name = material.name();
    return name.endsWith("_AXE")
        || name.endsWith("_HOE")
        || name.endsWith("_PICKAXE")
        || name.endsWith("_SHOVEL");
  }

  boolean isDurable(ItemStack item) {
    return item != null && !item.getType().isAir() && item.getType().getMaxDurability() > 0;
  }

  private boolean isArmor(ItemStack item) {
    if (!isDurable(item)) {
      return false;
    }
    EquipmentSlot slot = item.getType().getEquipmentSlot();
    return slot == EquipmentSlot.FEET
        || slot == EquipmentSlot.LEGS
        || slot == EquipmentSlot.CHEST
        || slot == EquipmentSlot.HEAD;
  }

  private void copyIdentityValues(ItemStack source, ItemStack result) {
    boolean cracked = hasFlag(source, crackedKey);
    String sourceSnapshot = cracked ? get(source, crackedAttributesKey) : null;
    for (NamespacedKey key : identityKeys()) {
      String value = get(source, key);
      if (value != null) {
        set(result, key, value);
      }
    }
    if (cracked) {
      suppressCrackedAttributes(result, reboundSnapshot(source, result, sourceSnapshot));
    } else {
      setFlag(result, crackedKey, false);
    }
    setFlag(result, brokenKey, hasFlag(source, brokenKey));
  }

  private List<NamespacedKey> identityKeys() {
    return List.of(
        itemIdKey,
        craftedOwnerKey,
        temperboundBondKey,
        temperboundPieceKey,
        masterworkKey
    );
  }

  private void suppressCrackedAttributes(ItemStack item, String attributes) {
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(crackedAttributesKey, PersistentDataType.STRING, attributes);
    meta.getPersistentDataContainer().set(crackedKey, PersistentDataType.BYTE, (byte) 1);
    meta.setAttributeModifiers(ImmutableMultimap.of());
    item.setItemMeta(meta);
  }

  private boolean isValidEquipmentUpgrade(ItemStack source, ItemStack result) {
    if (!isDurable(source) || !isDurable(result)) {
      return false;
    }
    EquipmentSlot sourceSlot = source.getType().getEquipmentSlot();
    EquipmentSlot resultSlot = result.getType().getEquipmentSlot();
    if (sourceSlot != resultSlot) {
      return false;
    }
    if (isArmor(source) || isArmor(result)) {
      return isArmor(source) && isArmor(result);
    }
    return isTool(source)
        && isTool(result)
        && toolFamily(source.getType()).equals(toolFamily(result.getType()));
  }

  private String toolFamily(Material material) {
    String name = material.name();
    int separator = name.lastIndexOf('_');
    return separator < 0 ? name : name.substring(separator + 1);
  }

  private CrackedSnapshot captureSnapshot(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    boolean explicitModifiers = meta.hasAttributeModifiers();
    Multimap<Attribute, AttributeModifier> modifiers = explicitModifiers ? meta.getAttributeModifiers() : null;
    if (modifiers == null || modifiers.isEmpty()) {
      return boundSnapshot(item, true, false, List.of());
    }
    if (modifiers.size() > MAX_SNAPSHOT_MODIFIERS) {
      throw new IllegalArgumentException("Cracked armor has too many explicit attribute modifiers");
    }
    EquipmentSlot itemSlot = item.getType().getEquipmentSlot();
    ImmutableMultimap.Builder<Attribute, AttributeModifier> validated = ImmutableMultimap.builder();
    for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
      Attribute attribute = entry.getKey();
      AttributeModifier modifier = entry.getValue();
      if (attribute == null
          || modifier == null
          || attribute.getKey() == null
          || modifier.getKey() == null
          || modifier.getSlotGroup() == null
          || !modifier.getSlotGroup().test(itemSlot)
          || !Double.isFinite(modifier.getAmount())
          || Math.abs(modifier.getAmount()) > MAX_SNAPSHOT_ATTRIBUTE_AMOUNT) {
        throw new IllegalArgumentException("Cracked armor has an invalid explicit attribute modifier");
      }
      validated.put(attribute, modifier);
    }
    return boundSnapshot(item, true, true, snapshotEntries(validated.build()));
  }

  private List<SnapshotModifier> snapshotEntries(Multimap<Attribute, AttributeModifier> modifiers) {
    ImmutableMultimap<Attribute, AttributeModifier> immutable = ImmutableMultimap.copyOf(modifiers);
    ArrayList<SnapshotModifier> entries = new ArrayList<>(immutable.size());
    for (Map.Entry<Attribute, AttributeModifier> entry : immutable.entries()) {
      AttributeModifier modifier = entry.getValue();
      entries.add(new SnapshotModifier(
          entry.getKey().getKey().toString(),
          modifier.getKey().toString(),
          modifier.getAmount(),
          modifier.getOperation().name(),
          slotGroupName(modifier.getSlotGroup())
      ));
    }
    return List.copyOf(entries);
  }

  private CrackedSnapshot boundSnapshot(
      ItemStack item,
      boolean restorable,
      boolean explicitModifiers,
      List<SnapshotModifier> modifiers
  ) {
    String itemId = valueOrEmpty(get(item, itemIdKey));
    String bondId = valueOrEmpty(get(item, temperboundBondKey));
    String pieceId = valueOrEmpty(get(item, temperboundPieceKey));
    boolean validBinding = !itemId.isBlank() && !bondId.isBlank() && itemId.equals(pieceId);
    return new CrackedSnapshot(
        item.getType().name(),
        item.getType().getEquipmentSlot().name(),
        itemId,
        bondId,
        pieceId,
        restorable && validBinding,
        explicitModifiers,
        modifiers
    );
  }

  private String reboundSnapshot(ItemStack source, ItemStack result, String encoded) {
    try {
      if (encoded == null || encoded.isBlank()) {
        throw new IllegalArgumentException("Cracked armor is missing its attribute snapshot");
      }
      CrackedSnapshot sourceState = decodeSnapshot(encoded);
      if (!matchesBinding(sourceState, source)) {
        throw new IllegalArgumentException("Cracked armor source snapshot does not match its bound item");
      }
      CrackedSnapshot resultState = boundSnapshot(
          result,
          sourceState.restorable(),
          sourceState.explicitModifiers(),
          sourceState.modifiers()
      );
      return encodeSnapshot(resultState);
    } catch (RuntimeException error) {
      reportCorruptSnapshot(source, valueOrEmpty(encoded), error);
      return encodeSnapshot(tombstone(result));
    }
  }

  private CrackedSnapshot tombstone(ItemStack item) {
    String material = item == null ? "" : item.getType().name();
    String slot = item == null ? "" : item.getType().getEquipmentSlot().name();
    return new CrackedSnapshot(material, slot, "", "", "", false, false, List.of());
  }

  private boolean matchesBinding(CrackedSnapshot snapshot, ItemStack item) {
    return snapshot != null
        && item != null
        && snapshot.material().equals(item.getType().name())
        && snapshot.slot().equals(item.getType().getEquipmentSlot().name())
        && snapshot.itemId().equals(valueOrEmpty(get(item, itemIdKey)))
        && snapshot.bondId().equals(valueOrEmpty(get(item, temperboundBondKey)))
        && snapshot.pieceId().equals(valueOrEmpty(get(item, temperboundPieceKey)))
        && snapshot.itemId().equals(snapshot.pieceId());
  }

  private Multimap<Attribute, AttributeModifier> modifiers(CrackedSnapshot snapshot) {
    ImmutableMultimap.Builder<Attribute, AttributeModifier> modifiers = ImmutableMultimap.builder();
    EquipmentSlot itemSlot = EquipmentSlot.valueOf(snapshot.slot());
    if (snapshot.modifiers().size() > MAX_SNAPSHOT_MODIFIERS) {
      throw new IllegalArgumentException("Cracked armor snapshot exceeds the modifier limit");
    }
    for (SnapshotModifier entry : snapshot.modifiers()) {
      NamespacedKey attributeKey = NamespacedKey.fromString(entry.attributeKey());
      NamespacedKey modifierKey = NamespacedKey.fromString(entry.modifierKey());
      Attribute attribute = attributeKey == null ? null : Registry.ATTRIBUTE.get(attributeKey);
      AttributeModifier.Operation operation = enumValue(AttributeModifier.Operation.class, entry.operation());
      EquipmentSlotGroup slotGroup = slotGroup(entry.slotGroup());
      if (attribute == null
          || modifierKey == null
          || operation == null
          || slotGroup == null
          || !slotGroup.test(itemSlot)
          || !Double.isFinite(entry.amount())
          || Math.abs(entry.amount()) > MAX_SNAPSHOT_ATTRIBUTE_AMOUNT) {
        throw new IllegalArgumentException("Cracked armor snapshot contains an invalid modifier");
      }
      modifiers.put(attribute, new AttributeModifier(modifierKey, entry.amount(), operation, slotGroup));
    }
    return modifiers.build();
  }

  private String encodeSnapshot(CrackedSnapshot snapshot) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
      DataOutputStream output = new DataOutputStream(bytes);
      output.writeInt(SNAPSHOT_MAGIC);
      output.writeInt(SNAPSHOT_VERSION);
      writeString(output, snapshot.material());
      writeString(output, snapshot.slot());
      writeString(output, snapshot.itemId());
      writeString(output, snapshot.bondId());
      writeString(output, snapshot.pieceId());
      output.writeBoolean(snapshot.restorable());
      output.writeBoolean(snapshot.explicitModifiers());
      output.writeInt(snapshot.modifiers().size());
      for (SnapshotModifier modifier : snapshot.modifiers()) {
        writeString(output, modifier.attributeKey());
        writeString(output, modifier.modifierKey());
        output.writeDouble(modifier.amount());
        writeString(output, modifier.operation());
        writeString(output, modifier.slotGroup());
      }
      output.flush();
      byte[] encoded = bytes.toByteArray();
      if (encoded.length > MAX_SNAPSHOT_BYTES) {
        throw new IllegalArgumentException("Cracked armor snapshot exceeds the byte limit");
      }
      return Base64.getEncoder().encodeToString(encoded);
    } catch (IOException error) {
      throw new IllegalStateException("Failed to encode cracked armor attributes", error);
    }
  }

  private CrackedSnapshot decodeSnapshot(String encoded) {
    if (encoded == null || encoded.isBlank() || encoded.length() > MAX_SNAPSHOT_CHARACTERS) {
      throw new IllegalArgumentException("Cracked armor snapshot exceeds the encoded size limit");
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      if (bytes.length > MAX_SNAPSHOT_BYTES) {
        throw new IllegalArgumentException("Cracked armor snapshot exceeds the decoded size limit");
      }
      DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
      if (input.readInt() != SNAPSHOT_MAGIC || input.readInt() != SNAPSHOT_VERSION) {
        throw new IllegalArgumentException("Cracked armor snapshot has an unsupported format");
      }
      String material = readString(input);
      String slot = readString(input);
      String itemId = readString(input);
      String bondId = readString(input);
      String pieceId = readString(input);
      boolean restorable = input.readBoolean();
      boolean explicitModifiers = input.readBoolean();
      int modifierCount = input.readInt();
      if (modifierCount < 0 || modifierCount > MAX_SNAPSHOT_MODIFIERS) {
        throw new IllegalArgumentException("Cracked armor snapshot has an invalid modifier count");
      }
      if (!explicitModifiers && modifierCount != 0) {
        throw new IllegalArgumentException("Cracked armor snapshot has modifiers without an explicit modifier state");
      }
      ArrayList<SnapshotModifier> modifiers = new ArrayList<>(modifierCount);
      for (int index = 0; index < modifierCount; index++) {
        modifiers.add(new SnapshotModifier(
            readString(input),
            readString(input),
            input.readDouble(),
            readString(input),
            readString(input)
        ));
      }
      if (input.available() != 0) {
        throw new IllegalArgumentException("Cracked armor snapshot contains trailing data");
      }
      return new CrackedSnapshot(
          material,
          slot,
          itemId,
          bondId,
          pieceId,
          restorable,
          explicitModifiers,
          modifiers
      );
    } catch (IOException | RuntimeException error) {
      throw new IllegalArgumentException("Failed to decode cracked armor attributes", error);
    }
  }

  private void writeString(DataOutputStream output, String value) throws IOException {
    String normalized = valueOrEmpty(value);
    if (normalized.length() > MAX_SNAPSHOT_KEY_LENGTH) {
      throw new IllegalArgumentException("Cracked armor snapshot field exceeds the length limit");
    }
    output.writeUTF(normalized);
  }

  private String readString(DataInputStream input) throws IOException {
    String value = input.readUTF();
    if (value.length() > MAX_SNAPSHOT_KEY_LENGTH) {
      throw new IllegalArgumentException("Cracked armor snapshot field exceeds the length limit");
    }
    return value;
  }

  private String slotGroupName(EquipmentSlotGroup group) {
    if (group == EquipmentSlotGroup.ANY) {
      return "any";
    }
    if (group == EquipmentSlotGroup.MAINHAND) {
      return "mainhand";
    }
    if (group == EquipmentSlotGroup.OFFHAND) {
      return "offhand";
    }
    if (group == EquipmentSlotGroup.HAND) {
      return "hand";
    }
    if (group == EquipmentSlotGroup.FEET) {
      return "feet";
    }
    if (group == EquipmentSlotGroup.LEGS) {
      return "legs";
    }
    if (group == EquipmentSlotGroup.CHEST) {
      return "chest";
    }
    if (group == EquipmentSlotGroup.HEAD) {
      return "head";
    }
    if (group == EquipmentSlotGroup.ARMOR) {
      return "armor";
    }
    if (group == EquipmentSlotGroup.BODY) {
      return "body";
    }
    if (group == EquipmentSlotGroup.SADDLE) {
      return "saddle";
    }
    throw new IllegalArgumentException("Unsupported equipment slot group");
  }

  private EquipmentSlotGroup slotGroup(String name) {
    return switch (valueOrEmpty(name)) {
      case "any" -> EquipmentSlotGroup.ANY;
      case "mainhand" -> EquipmentSlotGroup.MAINHAND;
      case "offhand" -> EquipmentSlotGroup.OFFHAND;
      case "hand" -> EquipmentSlotGroup.HAND;
      case "feet" -> EquipmentSlotGroup.FEET;
      case "legs" -> EquipmentSlotGroup.LEGS;
      case "chest" -> EquipmentSlotGroup.CHEST;
      case "head" -> EquipmentSlotGroup.HEAD;
      case "armor" -> EquipmentSlotGroup.ARMOR;
      case "body" -> EquipmentSlotGroup.BODY;
      case "saddle" -> EquipmentSlotGroup.SADDLE;
      default -> null;
    };
  }

  private <T extends Enum<T>> T enumValue(Class<T> type, String name) {
    try {
      return Enum.valueOf(type, name);
    } catch (IllegalArgumentException | NullPointerException error) {
      return null;
    }
  }

  private void reportCorruptSnapshot(ItemStack item, String encoded, RuntimeException error) {
    String material = item == null ? "unknown" : item.getType().name();
    String fingerprint = material + ":" + Integer.toHexString(valueOrEmpty(encoded).hashCode());
    synchronized (reportedCorruptSnapshots) {
      if (!reportedCorruptSnapshots.add(fingerprint)) {
        return;
      }
      if (reportedCorruptSnapshots.size() > MAX_REPORTED_CORRUPT_SNAPSHOTS) {
        reportedCorruptSnapshots.remove(reportedCorruptSnapshots.iterator().next());
      }
    }
    Adapt.error(new IllegalStateException(
        "Rejected corrupt cracked-armor attribute snapshot " + fingerprint, error));
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private String get(ItemStack item, NamespacedKey key) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
      return null;
    }
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }

  private boolean hasFlag(ItemStack item, NamespacedKey key) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
      return false;
    }
    Byte value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
    return value != null && value == (byte) 1;
  }

  private void set(ItemStack item, NamespacedKey key, String value) {
    if (item == null || item.getType().isAir() || value == null) {
      return;
    }
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    item.setItemMeta(meta);
  }

  private void setFlag(ItemStack item, NamespacedKey key, boolean enabled) {
    if (item == null || item.getType().isAir()) {
      return;
    }
    ItemMeta meta = item.getItemMeta();
    if (enabled) {
      meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    } else {
      meta.getPersistentDataContainer().remove(key);
    }
    item.setItemMeta(meta);
  }

  private record CrackedSnapshot(
      String material,
      String slot,
      String itemId,
      String bondId,
      String pieceId,
      boolean restorable,
      boolean explicitModifiers,
      List<SnapshotModifier> modifiers
  ) {
    private CrackedSnapshot {
      material = material == null ? "" : material;
      slot = slot == null ? "" : slot;
      itemId = itemId == null ? "" : itemId;
      bondId = bondId == null ? "" : bondId;
      pieceId = pieceId == null ? "" : pieceId;
      modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }
  }

  private record SnapshotModifier(
      String attributeKey,
      String modifierKey,
      double amount,
      String operation,
      String slotGroup
  ) {
  }
}
