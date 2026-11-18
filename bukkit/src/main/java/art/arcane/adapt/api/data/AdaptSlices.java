package art.arcane.adapt.api.data;

import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeMatter;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.api.golem.plinth.PlinthSlotMatter;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import art.arcane.adapt.content.matter.BrewingStandOwnerMatter;
import art.arcane.volmlib.util.matter.IrisMatter;

import java.util.Map;

/**
 * Stable slice IDs written into Adapt's mantle files. Never change an ID once released.
 */
public final class AdaptSlices {
  public static final String EARNINGS = "adapt:earnings";
  public static final String PLACEMENT_STAMP = "adapt:placement_stamp";
  public static final String BREWING_STAND_OWNER = "adapt:brewing_stand_owner";
  public static final String PLINTH_SLOT = "adapt:plinth_slot";
  public static final String NODE = "adapt:node";

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
    IrisMatter.registerSliceType(PLINTH_SLOT, new PlinthSlotMatter());
    IrisMatter.registerSliceType(NODE, new NodeMatter());
  }

  static Class<?> spatialType(String className) {
    return SPATIAL_NAMES.get(className);
  }

  static boolean isSpatialName(String className) {
    return SPATIAL_NAMES.containsKey(className) || SPATIAL_SCAFFOLD.equals(className);
  }
}
