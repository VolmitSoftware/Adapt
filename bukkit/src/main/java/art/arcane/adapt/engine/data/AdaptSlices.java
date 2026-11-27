package art.arcane.adapt.engine.data;

import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.engine.data.unit.Earnings;
import art.arcane.adapt.engine.data.unit.PlacementStamp;
import art.arcane.adapt.content.matter.BrewingStandOwnerMatter;
import art.arcane.adapt.content.matter.NodeFacesMatter;
import art.arcane.adapt.content.matter.PlinthSlotItemMatter;
import art.arcane.adapt.content.golem.node.NodeFaces;
import art.arcane.adapt.content.golem.plinth.PlinthSlotItem;
import art.arcane.volmlib.util.matter.IrisMatter;

import java.util.Map;

/**
 * Stable slice IDs written into Adapt's mantle files. Never change an ID once released. The built-in golemancy slices
 * keep the mantle slices their saved data already uses; every other addon slice, built-in or not, is
 * stored in the one {@link #ADDON_SLICES} slice as its id and the bytes its codec wrote, so there is no limit on how
 * many addons declare slices.
 */
public final class AdaptSlices {
  public static final String EARNINGS = "adapt:earnings";
  public static final String PLACEMENT_STAMP = "adapt:placement_stamp";
  public static final String BREWING_STAND_OWNER = "adapt:brewing_stand_owner";
  public static final String NODE = "adapt:node";
  public static final String PLINTH_SLOT = "adapt:plinth_slot";
  public static final String ADDON_SLICES = "adapt:addon_slices";

  // Addon slice ids with a mantle slice of their own, and the value type each must be declared with.
  private static final Map<String, Class<?>> DEDICATED = Map.of(NODE, NodeFaces.class, PLINTH_SLOT, PlinthSlotItem.class);

  // Spatial wrote each slice under its value type's canonical class name; these strings are frozen file contents.
  private static final Map<String, Class<?>> SPATIAL_NAMES = Map.of(
      "art.arcane.adapt.api.data.unit.Earnings", Earnings.class,
      "art.arcane.adapt.api.data.unit.PlacementStamp", PlacementStamp.class,
      "art.arcane.adapt.content.matter.BrewingStandOwner", BrewingStandOwner.class,
      "art.arcane.adapt.api.golem.plinth.PlinthSlotItem", PlinthSlotItem.class,
      "art.arcane.adapt.api.golem.node.NodeFaces", NodeFaces.class
  );

  // Adapt 2.x declared a scaffold slice of this name and never stored one. A file that carries it is read past it.
  static final String SPATIAL_SCAFFOLD = "art.arcane.adapt.content.block.ScaffoldMatter.ScaffoldData";
  static final int SPATIAL_SCAFFOLD_VALUE_BYTES = 3 * Long.BYTES;
  static final int LONGEST_SPATIAL_NAME = SPATIAL_SCAFFOLD.length();

  private AdaptSlices() {
  }

  public static void register() {
    IrisMatter.registerSliceType(EARNINGS, new Earnings.EarningsMatter());
    IrisMatter.registerSliceType(PLACEMENT_STAMP, new PlacementStamp.PlacementStampMatter());
    IrisMatter.registerSliceType(BREWING_STAND_OWNER, new BrewingStandOwnerMatter());
    IrisMatter.registerSliceType(NODE, new NodeFacesMatter());
    IrisMatter.registerSliceType(PLINTH_SLOT, new PlinthSlotItemMatter());
    IrisMatter.registerSliceType(ADDON_SLICES, new AddonSliceMatter());
  }

  /**
   * Whether the slice has a mantle slice of its own, keyed by its value type.
   *
   * @throws IllegalStateException when the slice takes a dedicated id with another value type
   */
  static boolean dedicated(BlockSlice<?> slice) {
    Class<?> type = DEDICATED.get(slice.id().toString());
    if (type == null) {
      return false;
    }
    if (!type.getName().equals(slice.type().getName())) {
      throw new IllegalStateException("Block slice " + slice.id() + " is stored as " + type.getName() + ", not "
          + slice.type().getName());
    }
    return true;
  }

  static Class<?> spatialType(String className) {
    return SPATIAL_NAMES.get(className);
  }

  static boolean isSpatialName(String className) {
    return SPATIAL_NAMES.containsKey(className) || SPATIAL_SCAFFOLD.equals(className);
  }
}
