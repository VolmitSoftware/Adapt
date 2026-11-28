package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

/**
 * Immutable block state (Bukkit block data, vanilla block state). Property names and values use
 * vanilla spelling, for example {@code age} and {@code 7}.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface BlockStateRef {
  /** Block type id, for example {@code minecraft:wheat}. */
  Id type();

  /** True for air, cave air and void air. */
  boolean isAir();

  /** Bukkit Material#isSolid: the block blocks movement. */
  boolean isSolid();

  /** Property value, or null when the block has no such property. */
  String property(String name);

  /** Every property name and value of this state. */
  Map<String, String> properties();

  /** New state with one property changed; returns this state when the property or value is invalid. */
  BlockStateRef with(String name, String value);

  /** Crop or plant age, or -1 when the block is not ageable. */
  int age();

  /** Highest age the block reaches, or -1 when it is not ageable. */
  int maxAge();

  /** Item that places this state, such as {@code wheat_seeds} for wheat; air when no item places it. */
  Id placementItem();

  /** Sound event this state plays when it breaks. */
  Id breakSound();

  /** True when {@code tool} gets this state's drops (no tool needed, or the correct tool). */
  boolean isPreferredTool(ItemRef tool);

  /**
   * True when {@code tool}'s mining rules name this state at a high enough tier (vanilla
   * {@code isCorrectToolForDrops}), whether or not the state needs a tool for its drops. A log is correct for an
   * axe and not for a pickaxe.
   */
  @ApiStatus.Experimental
  boolean isCorrectTool(ItemRef tool);

  /** Vanilla string form, such as {@code minecraft:wheat[age=7]}. */
  String asString();

  /** Adapter-internal access to the native block state. Content code must not call this. */
  @ApiStatus.Internal
  Object unwrap();
}
