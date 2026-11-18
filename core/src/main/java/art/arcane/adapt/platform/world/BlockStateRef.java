package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;

/**
 * Immutable block state (Bukkit block data, vanilla block state). Property names and values use
 * vanilla spelling, for example {@code age} and {@code 7}.
 */
public interface BlockStateRef {
  Id type();

  boolean isAir();

  /** Bukkit Material#isSolid: the block blocks movement. */
  boolean isSolid();

  /** Property value, or null when the block has no such property. */
  String property(String name);

  Map<String, String> properties();

  /** New state with one property changed; returns this state when the property or value is invalid. */
  BlockStateRef with(String name, String value);

  /** Crop or plant age, or -1 when the block is not ageable. */
  int age();

  int maxAge();

  /** Item that places this state, such as {@code wheat_seeds} for wheat; air when no item places it. */
  Id placementItem();

  /** Sound event this state plays when it breaks. */
  Id breakSound();

  /** True when {@code tool} gets this state's drops (no tool needed, or the correct tool). */
  boolean isPreferredTool(ItemRef tool);

  /** Vanilla string form, such as {@code minecraft:wheat[age=7]}. */
  String asString();

  Object unwrap();
}
