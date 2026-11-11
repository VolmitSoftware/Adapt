package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.data.StoredItem;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class NodeRecord {
  public static final int MIN_PRIORITY = 1;
  public static final int MAX_PRIORITY = 5;
  public static final int DEFAULT_PRIORITY = 3;
  public static final int MAX_FILTER_ENTRIES = 18;
  public static final int MAX_RADIUS = 32;
  public static final int DEFAULT_RADIUS = 2;
  public static final NodeCuboid DEFAULT_AREA = NodeCuboid.symmetric(DEFAULT_RADIUS, DEFAULT_RADIUS, DEFAULT_RADIUS);

  private final UUID owner;
  private final NodeType type;
  private final BlockFace face;
  private final DyeColor channel;
  private final int priority;
  private final RedstoneMode redstone;
  private final NodeCuboid area;
  private final List<StoredItem> filterEntries;
  private final List<String> filterTags;
  private final boolean whitelist;
  private final Map<String, String> data;
  private volatile DecodedFilter decodedFilter;

  private NodeRecord(Builder builder) {
    this.owner = builder.owner;
    this.type = builder.type;
    this.face = builder.face;
    this.channel = builder.channel;
    this.priority = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, builder.priority));
    this.redstone = builder.redstone;
    this.area = builder.area.bounded(MAX_RADIUS);
    this.filterEntries = List.copyOf(builder.filterEntries);
    this.filterTags = List.copyOf(builder.filterTags);
    this.whitelist = builder.whitelist;
    this.data = Map.copyOf(builder.data);
    if (filterCount() > MAX_FILTER_ENTRIES) {
      throw new IllegalArgumentException("A node filter holds at most " + MAX_FILTER_ENTRIES + " entries, got " + filterCount());
    }
    for (String tag : filterTags) {
      if (tag.length() < 2 || tag.charAt(0) != '#') {
        throw new IllegalArgumentException("Node filter tags are written as #name, got " + tag);
      }
    }
  }

  public static Builder builder(UUID owner, NodeType type, BlockFace face) {
    return new Builder(owner, type, face);
  }

  public static boolean isNodeFace(BlockFace face) {
    return switch (face) {
      case NORTH, SOUTH, EAST, WEST, UP, DOWN -> true;
      default -> false;
    };
  }

  public UUID owner() {
    return owner;
  }

  public NodeType type() {
    return type;
  }

  public BlockFace face() {
    return face;
  }

  public DyeColor channel() {
    return channel;
  }

  public int priority() {
    return priority;
  }

  public RedstoneMode redstone() {
    return redstone;
  }

  public NodeCuboid area() {
    return area;
  }

  /**
   * Every stored filter item in file order, readable or not. Reading this decodes nothing.
   */
  public List<StoredItem> filterEntries() {
    return filterEntries;
  }

  /**
   * The filter items that decode, as this record's own copies. The first call decodes them on the caller's thread.
   */
  public List<ItemStack> filterItems() {
    return decodedFilter().items;
  }

  public List<String> filterTags() {
    return filterTags;
  }

  /**
   * Filter items that are stored but do not decode. Each one keeps its filter slot and matches nothing,
   * so a filter with such entries never reads as the empty filter that accepts everything.
   */
  public int unreadableFilters() {
    return decodedFilter().unreadable;
  }

  public int filterCount() {
    return filterEntries.size() + filterTags.size();
  }

  public boolean whitelist() {
    return whitelist;
  }

  public Map<String, String> data() {
    return data;
  }

  public Builder toBuilder() {
    return new Builder(owner, type, face)
        .channel(channel)
        .priority(priority)
        .redstone(redstone)
        .area(area)
        .filterEntries(filterEntries)
        .filterTags(filterTags)
        .whitelist(whitelist)
        .data(data);
  }

  private DecodedFilter decodedFilter() {
    DecodedFilter known = decodedFilter;
    if (known != null) {
      return known;
    }
    List<ItemStack> items = new ArrayList<>(filterEntries.size());
    int unreadable = 0;
    for (StoredItem entry : filterEntries) {
      ItemStack item = entry.item();
      if (item != null) {
        items.add(item);
        continue;
      }
      unreadable++;
      entry.warnUnreadable(() -> "A filter item of the Golemancy " + type.id() + " node on face " + face + " owned by " + owner
          + " could not be read. It keeps its filter slot and matches nothing, and a blacklist accepts nothing."
          + " Its stored data is kept and read again the next time the node loads.");
    }
    DecodedFilter decoded = new DecodedFilter(List.copyOf(items), unreadable);
    decodedFilter = decoded;
    return decoded;
  }

  private boolean sameFilterEntries(NodeRecord record) {
    if (filterEntries.size() != record.filterEntries.size()) {
      return false;
    }
    for (int index = 0; index < filterEntries.size(); index++) {
      if (!filterEntries.get(index).sameItem(record.filterEntries.get(index))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof NodeRecord record)) {
      return false;
    }
    return priority == record.priority
        && whitelist == record.whitelist
        && owner.equals(record.owner)
        && area.equals(record.area)
        && type == record.type
        && face == record.face
        && channel == record.channel
        && redstone == record.redstone
        && sameFilterEntries(record)
        && filterTags.equals(record.filterTags)
        && data.equals(record.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(owner, type, face, channel, priority, redstone, area, filterEntries.size(), filterTags, whitelist, data);
  }

  @Override
  public String toString() {
    return "NodeRecord{" + type.id() + " " + face + " owner=" + owner + " channel=" + channel + " priority=" + priority
        + " redstone=" + redstone.id() + " area=" + area.minX() + ".." + area.maxX() + "/" + area.minY() + ".." + area.maxY() + "/"
        + area.minZ() + ".." + area.maxZ() + " filter=" + filterCount() + " whitelist=" + whitelist + " data=" + data + "}";
  }

  public static final class Builder {
    private final UUID owner;
    private final NodeType type;
    private final BlockFace face;
    private DyeColor channel;
    private int priority = DEFAULT_PRIORITY;
    private RedstoneMode redstone = RedstoneMode.IGNORE;
    private NodeCuboid area = DEFAULT_AREA;
    private List<StoredItem> filterEntries = List.of();
    private List<String> filterTags = List.of();
    private boolean whitelist = true;
    private Map<String, String> data = Map.of();

    private Builder(UUID owner, NodeType type, BlockFace face) {
      this.owner = Objects.requireNonNull(owner);
      this.type = Objects.requireNonNull(type);
      this.face = Objects.requireNonNull(face);
      if (!isNodeFace(face)) {
        throw new IllegalArgumentException("Nodes anchor to one of the six block faces, got " + face);
      }
    }

    public Builder channel(DyeColor channel) {
      this.channel = channel;
      return this;
    }

    public Builder priority(int priority) {
      this.priority = priority;
      return this;
    }

    public Builder redstone(RedstoneMode redstone) {
      this.redstone = Objects.requireNonNull(redstone);
      return this;
    }

    public Builder area(NodeCuboid area) {
      this.area = Objects.requireNonNull(area);
      return this;
    }

    /**
     * Replaces the filter items that decode. Entries that do not decode stay, after the given items.
     */
    public Builder filterItems(List<ItemStack> filterItems) {
      List<StoredItem> entries = new ArrayList<>(filterItems.size());
      for (ItemStack item : filterItems) {
        entries.add(StoredItem.of(item));
      }
      for (StoredItem entry : filterEntries) {
        if (!entry.readable()) {
          entries.add(entry);
        }
      }
      this.filterEntries = entries;
      return this;
    }

    public Builder filterEntries(List<StoredItem> filterEntries) {
      this.filterEntries = List.copyOf(filterEntries);
      return this;
    }

    public Builder filterTags(List<String> filterTags) {
      this.filterTags = List.copyOf(filterTags);
      return this;
    }

    public Builder whitelist(boolean whitelist) {
      this.whitelist = whitelist;
      return this;
    }

    public Builder data(Map<String, String> data) {
      this.data = new LinkedHashMap<>(data);
      return this;
    }

    public NodeRecord build() {
      return new NodeRecord(this);
    }
  }

  private record DecodedFilter(List<ItemStack> items, int unreadable) {
  }
}
