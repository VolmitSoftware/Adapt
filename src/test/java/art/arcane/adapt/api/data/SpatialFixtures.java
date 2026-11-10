package art.arcane.adapt.api.data;

import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.content.block.ScaffoldMatter;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The contents of the golden Spatial region files in src/test/resources/art/arcane/adapt/api/data/spatial.
 * Those files were written once by Adapt 2.2.7 with Spatial 22.11.2 built from source, storing exactly these entries.
 */
final class SpatialFixtures {
  static final int REGION_X = -1;
  static final int REGION_Z = 2;
  static final int MIN_HEIGHT = -64;
  static final int MAX_HEIGHT = 320;
  static final List<String> NAMES = List.of(
      "earnings", "placement_stamp", "brewing_stand_owner", "scaffold", "plinth_slot", "node", "mixed");

  private static final UUID[] OWNERS = {
      UUID.fromString("6f1c2a4e-0b7d-4d8e-9a51-2f3e4d5c6b7a"),
      UUID.fromString("00000000-0000-0000-0000-000000000001"),
      UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffffe")
  };

  record Entry(int x, int y, int z, Object value) {
  }

  private SpatialFixtures() {
  }

  static String resource(String name) {
    return "/art/arcane/adapt/api/data/spatial/" + name + ".ttp";
  }

  static List<Entry> entries(String name, Function<String, ItemStack> items) {
    List<Entry> entries = new ArrayList<>();
    switch (name) {
      case "earnings" -> earnings(entries);
      case "placement_stamp" -> placementStamps(entries);
      case "brewing_stand_owner" -> brewingStandOwners(entries);
      case "scaffold" -> scaffolds(entries);
      case "plinth_slot" -> plinthSlots(entries, items);
      case "node" -> nodes(entries, items);
      case "mixed" -> mixed(entries, items);
      default -> throw new IllegalArgumentException(name);
    }
    return entries;
  }

  // One full 16x16x16 section with 127 distinct values exercises the hash palette and 7-bit packing.
  private static void earnings(List<Entry> entries) {
    for (int index = 0; index < 4096; index++) {
      entries.add(new Entry(-48 + (index & 15), 16 + ((index >> 4) & 15), 1120 + (index >> 8), new Earnings(1 + index % 127)));
    }
    entries.add(new Entry(-480, -64, 1530, new Earnings(127)));
    entries.add(new Entry(-1, 319, 1024, new Earnings(1)));
    entries.add(new Entry(-300, 100, 1300, new Earnings(64)));
  }

  // 316 distinct stamps in one section need a 9-bit palette index.
  private static void placementStamps(List<Entry> entries) {
    for (int index = 0; index < 4096; index += 13) {
      entries.add(new Entry(-16 + (index & 15), -64 + ((index >> 4) & 15), 1024 + (index >> 8),
          new PlacementStamp(1_700_000_000 + index, index * 2 + 1, -index)));
    }
    entries.add(new Entry(-200, 64, 1200, new PlacementStamp(Integer.MAX_VALUE, Integer.MIN_VALUE, 0)));
  }

  private static void brewingStandOwners(List<Entry> entries) {
    for (int index = 0; index < 20; index++) {
      entries.add(new Entry(-100 - index * 3, 60 + index, 1100 + index * 5, new BrewingStandOwner(OWNERS[index % OWNERS.length])));
    }
  }

  private static void scaffolds(List<Entry> entries) {
    for (int index = 0; index < 5; index++) {
      entries.add(new Entry(-64 + index, 70, 1090, new ScaffoldMatter.ScaffoldData(OWNERS[index % OWNERS.length], 1_000L * index + 7L)));
    }
  }

  private static void plinthSlots(List<Entry> entries, Function<String, ItemStack> items) {
    entries.add(new Entry(-10, 65, 1030, PlinthSlotItem.of(OWNERS[0], items.apply("sugar"))));
    entries.add(new Entry(-10, 66, 1030, PlinthSlotItem.of(OWNERS[1], items.apply("feather"))));
    entries.add(new Entry(-12, 65, 1031, PlinthSlotItem.unreadable(OWNERS[2])));
  }

  private static void nodes(List<Entry> entries, Function<String, ItemStack> items) {
    NodeRecord guard = NodeRecord.builder(OWNERS[0], NodeType.GUARD, BlockFace.UP).build();
    NodeRecord filter = NodeRecord.builder(OWNERS[1], NodeType.FILTER, BlockFace.NORTH)
        .channel(DyeColor.RED)
        .priority(5)
        .redstone(RedstoneMode.POWERED)
        .area(new NodeCuboid(-4, -1, -3, 6, 2, 5))
        .whitelist(true)
        .filterItems(List.of(items.apply("torch"), items.apply("sugar")))
        .filterTags(List.of("#minecraft:logs"))
        .data(Map.of("mode", "strict"))
        .build();
    NodeRecord deposit = NodeRecord.builder(OWNERS[2], NodeType.DEPOSIT, BlockFace.EAST).channel(DyeColor.BLUE).build();
    entries.add(new Entry(-33, -60, 1055, NodeFaces.EMPTY.with(guard)));
    entries.add(new Entry(-34, 200, 1056, NodeFaces.EMPTY.with(filter).with(deposit)));
    entries.add(new Entry(-511, 0, 1535, NodeFaces.EMPTY.with(deposit)));
  }

  // Several slice types in the same section exercise the per-slice framing.
  private static void mixed(List<Entry> entries, Function<String, ItemStack> items) {
    entries.add(new Entry(-40, 70, 1070, new Earnings(12)));
    entries.add(new Entry(-40, 70, 1070, new PlacementStamp(5, 6, 7)));
    entries.add(new Entry(-39, 71, 1071, new BrewingStandOwner(OWNERS[1])));
    entries.add(new Entry(-38, 72, 1072, PlinthSlotItem.of(OWNERS[2], items.apply("feather"))));
    entries.add(new Entry(-37, 73, 1073, NodeFaces.EMPTY.with(NodeRecord.builder(OWNERS[0], NodeType.HARVEST, BlockFace.SOUTH).build())));
    entries.add(new Entry(-36, 74, 1074, new ScaffoldMatter.ScaffoldData(OWNERS[0], 99L)));
  }
}
