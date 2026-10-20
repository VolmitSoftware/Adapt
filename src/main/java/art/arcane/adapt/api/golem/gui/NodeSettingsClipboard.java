package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.RedstoneMode;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NodeSettingsClipboard {
  private static final Map<UUID, NodeRecord> COPIES = new ConcurrentHashMap<>();

  private NodeSettingsClipboard() {
  }

  public static boolean copy(UUID owner, NodeRecord record) {
    if (!record.owner().equals(owner)) {
      return false;
    }
    COPIES.put(owner, record.toBuilder().build());
    return true;
  }

  public static Optional<NodeRecord> pasted(UUID owner, NodeRecord destination, int level, int maxRadius, int filterSlots) {
    NodeRecord source = COPIES.get(owner);
    if (source == null || !destination.owner().equals(owner) || source.type() != destination.type() || level < 1
        || !source.area().check(maxRadius).fits() || source.filterCount() > filterSlots
        || !NodeRules.allowsPriority(level) && source.priority() != NodeRecord.DEFAULT_PRIORITY
        || !NodeRules.allowsRedstone(level) && source.redstone() != RedstoneMode.IGNORE
        || !NodeRules.allowsBlacklistAndTags(level) && (!source.whitelist() || !source.filterTags().isEmpty())) {
      return Optional.empty();
    }
    return Optional.of(destination.toBuilder().channel(source.channel()).priority(source.priority()).redstone(source.redstone())
        .area(source.area()).filterItems(source.filterItems()).filterTags(source.filterTags()).whitelist(source.whitelist()).build());
  }

  public static void forget(UUID owner) {
    COPIES.remove(owner);
  }
}
