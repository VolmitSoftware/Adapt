package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.gui.NodePanelContext;
import art.arcane.adapt.api.golem.gui.NodePanelControl;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class SorterNodePanelsTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000002711");
  private static final NodeKey KEY = new NodeKey(SorterYard.WORLD, 0, 64, 0, BlockFace.UP);

  private final GolemSorterCore.Config config = new GolemSorterCore.Config();
  private final Player viewer = mock(Player.class);
  private final List<String> opened = new ArrayList<>();
  private GolemSorterCore core;
  private InputNodePanel input;
  private FilterNodePanel filter;

  @BeforeEach
  void setUp() {
    core = mock(GolemSorterCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(viewer.getUniqueId()).thenReturn(OWNER);
    input = new InputNodePanel(core, type -> GolemTestItems.stack(type, 1), (player, key) -> opened.add(player.getUniqueId() + " " + key.x()));
    filter = new FilterNodePanel(core, type -> GolemTestItems.stack(type, 1));
  }

  @Test
  void aLevelOneInputNodeShowsItsRadiusAndLockedMatchingAndFetch() {
    level(1);
    NodeRecord record = record(NodeType.INPUT);

    List<NodePanelControl> controls = input.controls(context(record));

    assertThat(controls).extracting(NodePanelControl::id).containsExactly("sorter-match", "sorter-radius", "sorter-fetch");
    assertThat(plain(controls.get(0).name())).isEqualTo("Matching: Same Item");
    assertThat(lore(controls.get(0))).containsExactly("How a Sorter decides which container already holds an item", "Unlocks at Sorter Core level 2");
    assertThat(controls.get(0).leftClick()).isNull();
    assertThat(plain(controls.get(1).name())).isEqualTo("Destination Radius: 8");
    assertThat(lore(controls.get(1))).containsExactly("Up to 8 blocks at your Sorter Core level", "Left-click to grow, right-click to shrink");
    NodeRecord narrower = controls.get(1).rightClick().apply(record);
    assertThat(SorterRules.radius(narrower, 8)).isEqualTo(6);
    assertThat(SorterRules.radius(controls.get(1).leftClick().apply(narrower), 8)).isEqualTo(8);
    assertThat(lore(controls.get(2))).containsExactly("Unlocks at Sorter Core level 5");
    assertThat(controls.get(2).open()).isNull();
  }

  @Test
  void aLevelFiveInputNodeCyclesItsMatchingAndOpensFetch() {
    level(5);
    NodeRecord record = record(NodeType.INPUT);

    List<NodePanelControl> controls = input.controls(context(record));

    assertThat(plain(controls.get(0).name())).isEqualTo("Matching: Enchantments and Potions");
    assertThat(lore(controls.get(0))).containsExactly("How a Sorter decides which container already holds an item", "Left-click for next, right-click for previous");
    NodeRecord exact = controls.get(0).leftClick().apply(record);
    assertThat(plain(input.controls(context(exact)).get(0).name())).isEqualTo("Matching: Same Item");
    assertThat(plain(input.controls(context(controls.get(0).rightClick().apply(record))).get(0).name())).isEqualTo("Matching: Item Groups");
    assertThat(plain(controls.get(1).name())).isEqualTo("Destination Radius: 24");
    assertThat(plain(controls.get(2).name())).isEqualTo("Fetch Items");
    assertThat(lore(controls.get(2))).containsExactly("Choose items for a Sorter to bring you");

    controls.get(2).open().accept(viewer);

    assertThat(opened).containsExactly(OWNER + " 0");
  }

  @Test
  void aFilterNodeTogglesOverflowAndUnlocksRestockAtItsLevel() {
    level(3);
    NodeRecord record = record(NodeType.FILTER);

    List<NodePanelControl> controls = filter.controls(context(record));

    assertThat(controls).extracting(NodePanelControl::id).containsExactly("sorter-overflow", "sorter-restock");
    assertThat(plain(controls.get(0).name())).isEqualTo("Overflow: Off");
    assertThat(lore(controls.get(0))).containsExactly("An overflow takes items no other container wants", "Click to switch");
    NodeRecord overflow = controls.get(0).leftClick().apply(record);
    assertThat(plain(filter.controls(context(overflow)).get(0).name())).isEqualTo("Overflow: On");
    assertThat(plain(controls.get(1).name())).isEqualTo("Restock: Off");
    assertThat(lore(controls.get(1))).containsExactly("Unlocks at Sorter Core level 4");
    assertThat(controls.get(1).leftClick()).isNull();

    level(4);
    NodePanelControl restock = filter.controls(context(record)).get(1);
    NodeRecord one = restock.leftClick().apply(record);
    assertThat(lore(restock)).containsExactly("Sorters keep this many of each listed item here", "Left-click to grow, right-click to shrink");
    assertThat(plain(filter.controls(context(one)).get(1).name())).isEqualTo("Restock: 1 of Each Filter Item");
    assertThat(SorterRules.restockTarget(restock.rightClick().apply(one))).isZero();
  }

  @Test
  void aFilterNodeBelowItsLevelLocksOverflow() {
    level(2);

    NodePanelControl overflow = filter.controls(context(record(NodeType.FILTER))).getFirst();

    assertThat(lore(overflow)).containsExactly("Unlocks at Sorter Core level 3");
    assertThat(overflow.leftClick()).isNull();
  }

  private void level(int level) {
    lenient().when(core.ownerLevel(OWNER)).thenReturn(level);
  }

  private NodePanelContext context(NodeRecord record) {
    return new NodePanelContext(viewer, KEY, record, 5);
  }

  private static NodeRecord record(NodeType type) {
    return NodeRecord.builder(OWNER, type, BlockFace.UP).build();
  }

  private static List<String> lore(NodePanelControl control) {
    List<String> lines = new ArrayList<>(control.lore().size());
    for (String line : control.lore()) {
      lines.add(plain(line));
    }
    return lines;
  }

  private static String plain(String text) {
    return text.replaceAll("§[0-9a-fk-orx]", "");
  }
}
