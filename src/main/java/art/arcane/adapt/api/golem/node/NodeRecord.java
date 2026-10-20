package art.arcane.adapt.api.golem.node;

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
  private final List<ItemStack> filterItems;
  private final List<String> filterTags;
  private final boolean whitelist;
  private final Map<String, String> data;

  private NodeRecord(Builder builder) {
    this.owner = builder.owner;
    this.type = builder.type;
    this.face = builder.face;
    this.channel = builder.channel;
    this.priority = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, builder.priority));
    this.redstone = builder.redstone;
    this.area = builder.area.bounded(MAX_RADIUS);
    this.filterItems = List.copyOf(builder.filterItems);
    this.filterTags = List.copyOf(builder.filterTags);
    this.whitelist = builder.whitelist;
    this.data = Map.copyOf(builder.data);
    if (filterItems.size() + filterTags.size() > MAX_FILTER_ENTRIES) {
      throw new IllegalArgumentException("A node filter holds at most " + MAX_FILTER_ENTRIES + " entries, got " + (filterItems.size() + filterTags.size()));
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

  public List<ItemStack> filterItems() {
    return filterItems;
  }

  public List<String> filterTags() {
    return filterTags;
  }

  public int filterCount() {
    return filterItems.size() + filterTags.size();
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
        .filterItems(filterItems)
        .filterTags(filterTags)
        .whitelist(whitelist)
        .data(data);
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
        && filterItems.equals(record.filterItems)
        && filterTags.equals(record.filterTags)
        && data.equals(record.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(owner, type, face, channel, priority, redstone, area, filterItems, filterTags, whitelist, data);
  }

  @Override
  public String toString() {
    return "NodeRecord{" + type.id() + " " + face + " owner=" + owner + " channel=" + channel + " priority=" + priority
        + " redstone=" + redstone.id() + " area=" + area.minX() + ".." + area.maxX() + "/" + area.minY() + ".." + area.maxY() + "/"
        + area.minZ() + ".." + area.maxZ() + " filter=" + filterCount()
        + " whitelist=" + whitelist + " data=" + data + "}";
  }

  public static final class Builder {
    private final UUID owner;
    private final NodeType type;
    private final BlockFace face;
    private DyeColor channel;
    private int priority = DEFAULT_PRIORITY;
    private RedstoneMode redstone = RedstoneMode.IGNORE;
    private NodeCuboid area = DEFAULT_AREA;
    private List<ItemStack> filterItems = List.of();
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

    public Builder filterItems(List<ItemStack> filterItems) {
      List<ItemStack> copies = new ArrayList<>(filterItems.size());
      for (ItemStack item : filterItems) {
        copies.add(Objects.requireNonNull(item).clone());
      }
      this.filterItems = copies;
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
}
