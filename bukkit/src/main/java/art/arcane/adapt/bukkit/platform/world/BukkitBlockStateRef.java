package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.bukkit.Bukkit;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable block state over a private copy of Bukkit block data. Equality follows the block data. */
public final class BukkitBlockStateRef implements BlockStateRef {
  private final BlockData data;

  public BukkitBlockStateRef(BlockData data) {
    this.data = Objects.requireNonNull(data, "data");
  }

  public BlockData data() {
    return data.clone();
  }

  @Override
  public Id type() {
    return BukkitResolve.id(data.getMaterial());
  }

  @Override
  public boolean isAir() {
    return BukkitResolve.isAir(data.getMaterial());
  }

  @Override
  public boolean isSolid() {
    return data.getMaterial().isSolid();
  }

  @Override
  public String property(String name) {
    return properties().get(name);
  }

  @Override
  public Map<String, String> properties() {
    return parse(data.getAsString());
  }

  @Override
  public BlockStateRef with(String name, String value) {
    Map<String, String> properties = properties();
    if (!properties.containsKey(name) || value == null) {
      return this;
    }
    properties.put(name, value);
    StringBuilder text = new StringBuilder(data.getMaterial().getKey().toString()).append('[');
    boolean first = true;
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      if (!first) {
        text.append(',');
      }
      text.append(entry.getKey()).append('=').append(entry.getValue());
      first = false;
    }
    text.append(']');
    try {
      return new BukkitBlockStateRef(Bukkit.createBlockData(text.toString()));
    } catch (IllegalArgumentException invalid) {
      return this;
    }
  }

  @Override
  public int age() {
    return data instanceof Ageable ageable ? ageable.getAge() : -1;
  }

  @Override
  public int maxAge() {
    return data instanceof Ageable ageable ? ageable.getMaximumAge() : -1;
  }

  @Override
  public Id placementItem() {
    return BukkitResolve.id(data.getPlacementMaterial());
  }

  @Override
  public Id breakSound() {
    return BukkitResolve.id(data.getSoundGroup().getBreakSound());
  }

  @Override
  public boolean isPreferredTool(ItemRef tool) {
    return data.isPreferredTool(BukkitRefs.item(tool));
  }

  @Override
  public String asString() {
    return data.getAsString();
  }

  @Override
  public Object unwrap() {
    return data();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BukkitBlockStateRef that && data.equals(that.data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }

  @Override
  public String toString() {
    return data.getAsString();
  }

  static Map<String, String> parse(String state) {
    Map<String, String> properties = new LinkedHashMap<>();
    int open = state.indexOf('[');
    int close = state.lastIndexOf(']');
    if (open < 0 || close <= open + 1) {
      return properties;
    }
    for (String pair : state.substring(open + 1, close).split(",")) {
      int equals = pair.indexOf('=');
      if (equals > 0) {
        properties.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
      }
    }
    return properties;
  }
}
