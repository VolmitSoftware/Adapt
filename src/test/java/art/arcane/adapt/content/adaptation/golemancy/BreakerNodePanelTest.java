package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.gui.NodePanelContext;
import art.arcane.adapt.api.golem.gui.NodePanelControl;
import art.arcane.adapt.api.golem.gui.NodePanels;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class BreakerNodePanelTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000024d1");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);

  private final GolemBreakerCore.Config config = new GolemBreakerCore.Config();
  private final Player viewer = mock(Player.class);
  private GolemBreakerCore core;
  private BreakerNodePanel panel;

  @BeforeEach
  void setUp() {
    core = mock(GolemBreakerCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(viewer.getUniqueId()).thenReturn(OWNER);
    panel = new BreakerNodePanel(core, type -> GolemTestItems.stack(type, 1));
  }

  @Test
  void aNodeWithoutAWhitelistFilterAsksForBlocks() {
    level(5);

    NodePanelControl empty = panel.controls(context(record(true))).getFirst();
    NodePanelControl ready = panel.controls(context(record(true, Material.COBBLESTONE))).getFirst();
    NodePanelControl blacklist = panel.controls(context(record(false, Material.COBBLESTONE))).getFirst();

    assertThat(plain(empty.name())).isEqualTo("Add Blocks to the Whitelist Filter");
    assertThat(empty.icon().getType()).isEqualTo(Material.BARRIER);
    assertThat(plain(blacklist.name())).isEqualTo("Add Blocks to the Whitelist Filter");
    assertThat(plain(ready.name())).isEqualTo("Breaks the Blocks in the Filter");
    assertThat(ready.icon().getType()).isEqualTo(Material.IRON_PICKAXE);
    assertThat(lore(ready)).containsExactly("Breakers break only blocks a whitelist filter lists");
    assertThat(ready.leftClick()).isNull();
    assertThat(ready.rightClick()).isNull();
  }

  @Test
  void theTogglesStayLockedBelowTheirLevels() {
    level(2);

    List<NodePanelControl> controls = panel.controls(context(record(true, Material.STONE)));

    assertThat(controls).extracting(NodePanelControl::id).containsExactly("breaker-filter", "breaker-silk", "breaker-fortune");
    assertThat(plain(controls.get(1).name())).isEqualTo("Silk Touch: Off");
    assertThat(lore(controls.get(1))).containsExactly("Unlocks at Breaker Core level 3");
    assertThat(controls.get(1).leftClick()).isNull();
    assertThat(plain(controls.get(2).name())).isEqualTo("Fortune I: Off");
    assertThat(lore(controls.get(2))).containsExactly("Unlocks at Breaker Core level 5");
    assertThat(controls.get(2).leftClick()).isNull();
  }

  @Test
  void unlockedTogglesSwitchTheNodeSettingAndTurnEachOtherOff() {
    level(5);
    NodeRecord plain = record(true, Material.STONE);

    List<NodePanelControl> controls = panel.controls(context(plain));
    NodeRecord silky = controls.get(1).leftClick().apply(plain);
    List<NodePanelControl> silkyControls = panel.controls(context(silky));
    NodeRecord lucky = silkyControls.get(2).leftClick().apply(silky);
    List<NodePanelControl> luckyControls = panel.controls(context(lucky));

    assertThat(lore(controls.get(1))).containsExactly("Click to switch", "Silk Touch and Fortune I turn each other off");
    assertThat(plain(silkyControls.get(1).name())).isEqualTo("Silk Touch: On");
    assertThat(plain(silkyControls.get(2).name())).isEqualTo("Fortune I: Off");
    assertThat(plain(luckyControls.get(1).name())).isEqualTo("Silk Touch: Off");
    assertThat(plain(luckyControls.get(2).name())).isEqualTo("Fortune I: On");
    assertThat(BreakerRules.on(lucky, BreakerRules.SILK_KEY)).isFalse();
    assertThat(luckyControls.get(2).leftClick().apply(lucky).data()).isEmpty();
  }

  @Test
  void aStoredToggleAboveTheOwnersLevelShowsAsOff() {
    level(3);

    List<NodePanelControl> controls = panel.controls(context(BreakerRules.toggleFortune(record(true, Material.STONE))));

    assertThat(plain(controls.get(2).name())).isEqualTo("Fortune I: Off");
  }

  @Test
  void panelsUnregisterOnlyTheirOwnRegistration() {
    BreakerNodePanel other = new BreakerNodePanel(core, type -> GolemTestItems.stack(type, 1));
    NodePanels.register(NodeType.BREAKER, panel);

    NodePanels.unregister(NodeType.BREAKER, other);
    assertThat(NodePanels.of(NodeType.BREAKER)).containsSame(panel);

    NodePanels.unregister(NodeType.BREAKER, panel);
    assertThat(NodePanels.of(NodeType.BREAKER)).isEmpty();
  }

  private void level(int level) {
    lenient().when(core.ownerLevel(OWNER)).thenReturn(level);
  }

  private NodePanelContext context(NodeRecord record) {
    return new NodePanelContext(viewer, KEY, record, 5);
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

  private static NodeRecord record(boolean whitelist, Material... filter) {
    List<ItemStack> items = new ArrayList<>(filter.length);
    for (Material type : filter) {
      items.add(GolemTestItems.stack(type, 1));
    }
    return NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.UP).filterItems(items).whitelist(whitelist).build();
  }
}
