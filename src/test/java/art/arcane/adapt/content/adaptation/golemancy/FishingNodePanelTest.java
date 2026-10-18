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

class FishingNodePanelTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000027e1");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 64, 0, BlockFace.NORTH);
  private static final long NOW = 90_000L;

  private final GolemAnglerCore.Config config = new GolemAnglerCore.Config();
  private final AnglerWaters waters = new AnglerWaters();
  private final Player viewer = mock(Player.class);
  private final NodeRecord plain = NodeRecord.builder(OWNER, NodeType.FISHING, BlockFace.NORTH).build();
  private GolemAnglerCore core;
  private FishingNodePanel panel;

  @BeforeEach
  void setUp() {
    core = mock(GolemAnglerCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(viewer.getUniqueId()).thenReturn(OWNER);
    panel = new FishingNodePanel(core, waters, type -> GolemTestItems.stack(type, 1));
  }

  @Test
  void theWaterIndicatorShowsWhetherTheNodeFoundOpenWater() {
    level(1);

    NodePanelControl unknown = panel.controls(context(plain)).getFirst();
    waters.survey(KEY, NOW, () -> new AnglerWater.Survey(false, 0, 64, -3, 64, false));
    NodePanelControl closed = panel.controls(context(plain)).getFirst();
    waters.forget();
    waters.survey(KEY, NOW, () -> new AnglerWater.Survey(true, 0, 64, -3, 64, false));
    NodePanelControl open = panel.controls(context(plain)).getFirst();

    assertThat(plain(unknown.name())).isEqualTo("Needs Open Water");
    assertThat(unknown.icon().getType()).isEqualTo(Material.BARRIER);
    assertThat(plain(closed.name())).isEqualTo("Needs Open Water");
    assertThat(plain(open.name())).isEqualTo("Open Water Found");
    assertThat(open.icon().getType()).isEqualTo(Material.WATER_BUCKET);
    assertThat(lore(open)).containsExactly("Anglers need a 5 by 5 patch of open water in front of the node");
    assertThat(open.leftClick()).isNull();
    assertThat(open.rightClick()).isNull();
  }

  @Test
  void theJunkFilterStaysLockedBelowItsLevel() {
    level(2);

    List<NodePanelControl> controls = panel.controls(context(AnglerRules.toggleJunk(plain)));

    assertThat(controls).extracting(NodePanelControl::id).containsExactly("angler-water", "angler-junk");
    assertThat(plain(controls.get(1).name())).isEqualTo("Junk: Keep");
    assertThat(lore(controls.get(1))).containsExactly("Unlocks at Angler Core level 3");
    assertThat(controls.get(1).leftClick()).isNull();
  }

  @Test
  void theUnlockedJunkFilterSwitchesBetweenKeepingAndDiscarding() {
    level(3);

    NodePanelControl keep = panel.controls(context(plain)).get(1);
    NodeRecord discarding = keep.leftClick().apply(plain);
    NodePanelControl discard = panel.controls(context(discarding)).get(1);

    assertThat(plain(keep.name())).isEqualTo("Junk: Keep");
    assertThat(lore(keep)).containsExactly("Click to switch");
    assertThat(keep.icon().getType()).isEqualTo(Material.ROTTEN_FLESH);
    assertThat(plain(discard.name())).isEqualTo("Junk: Discard");
    assertThat(discard.leftClick().apply(discarding).data()).isEmpty();
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
}
