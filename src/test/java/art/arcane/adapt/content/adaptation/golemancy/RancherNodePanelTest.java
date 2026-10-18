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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class RancherNodePanelTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000025c1");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);

  private final GolemRancherCore.Config config = new GolemRancherCore.Config();
  private final Player viewer = mock(Player.class);
  private final NodeRecord pasture = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).build();
  private GolemRancherCore core;
  private RancherNodePanel panel;

  @BeforeEach
  void setUp() {
    core = mock(GolemRancherCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.species()).thenReturn(List.of(EntityType.COW, EntityType.SHEEP));
    lenient().when(viewer.getUniqueId()).thenReturn(OWNER);
    panel = new RancherNodePanel(core, type -> GolemTestItems.stack(type, 1));
  }

  @Test
  void thePanelShowsSpeciesKeepCountAndTheFourActions() {
    level(5);

    List<NodePanelControl> controls = panel.controls(context(pasture));

    assertThat(controls).extracting(NodePanelControl::id)
        .containsExactly("rancher-species", "rancher-keep", "rancher-breed", "rancher-shear", "rancher-milk", "rancher-cull");
    assertThat(plain(controls.get(0).name())).isEqualTo("Species: All Animals");
    assertThat(controls.get(0).icon().getType()).isEqualTo(Material.LEAD);
    assertThat(plain(controls.get(1).name())).isEqualTo("Keep 8 Adults per Species");
    assertThat(plain(controls.get(2).name())).isEqualTo("Breeding: On");
    assertThat(plain(controls.get(3).name())).isEqualTo("Shearing: On");
    assertThat(plain(controls.get(4).name())).isEqualTo("Milking: On");
    assertThat(plain(controls.get(5).name())).isEqualTo("Culling: Off");
    assertThat(lore(controls.get(5))).containsExactly("Click to switch", "Only iron golems cull");
  }

  @Test
  void speciesCyclesThroughTheTendedAnimals() {
    level(1);
    NodePanelControl species = panel.controls(context(pasture)).getFirst();

    NodeRecord cows = species.leftClick().apply(pasture);
    NodePanelControl shown = panel.controls(context(cows)).getFirst();

    assertThat(plain(shown.name())).isEqualTo("Species: Cow");
    assertThat(shown.icon().getType()).isEqualTo(Material.COW_SPAWN_EGG);
    assertThat(RancherRules.species(species.rightClick().apply(pasture))).contains(EntityType.SHEEP);
    assertThat(lore(species)).containsExactly("Left-click for the next species, right-click for the previous");
  }

  @Test
  void theKeepCountRisesAndFallsWithinItsLimits() {
    level(1);
    NodePanelControl keep = panel.controls(context(pasture)).get(1);

    assertThat(RancherRules.keep(keep.leftClick().apply(pasture), 8)).isEqualTo(9);
    assertThat(RancherRules.keep(keep.rightClick().apply(pasture), 8)).isEqualTo(7);
    NodeRecord full = pasture.toBuilder().data(Map.of("keep", "64")).build();
    assertThat(RancherRules.keep(panel.controls(context(full)).get(1).leftClick().apply(full), 8)).isEqualTo(64);
    assertThat(lore(keep)).containsExactly("Left-click to raise, right-click to lower");
  }

  @Test
  void actionsStayLockedBelowTheirLevels() {
    level(2);

    List<NodePanelControl> controls = panel.controls(context(pasture));

    assertThat(controls.get(3).leftClick()).isNotNull();
    assertThat(plain(controls.get(4).name())).isEqualTo("Milking: Off");
    assertThat(lore(controls.get(4))).containsExactly("Unlocks at Rancher Core level 3");
    assertThat(controls.get(4).leftClick()).isNull();
    assertThat(lore(controls.get(5))).containsExactly("Unlocks at Rancher Core level 4", "Only iron golems cull");
    assertThat(controls.get(5).leftClick()).isNull();
  }

  @Test
  void unlockedTogglesSwitchTheirAction() {
    level(4);
    List<NodePanelControl> controls = panel.controls(context(pasture));

    NodeRecord culling = controls.get(5).leftClick().apply(pasture);
    NodeRecord quiet = controls.get(2).leftClick().apply(culling);

    assertThat(plain(panel.controls(context(culling)).get(5).name())).isEqualTo("Culling: On");
    assertThat(RancherRules.enabled(quiet, RancherRules.Action.BREED)).isFalse();
    assertThat(RancherRules.enabled(quiet, RancherRules.Action.CULL)).isTrue();
  }

  @Test
  void panelsUnregisterOnlyTheirOwnRegistration() {
    RancherNodePanel other = new RancherNodePanel(core, type -> GolemTestItems.stack(type, 1));
    NodePanels.register(NodeType.PASTURE, panel);

    NodePanels.unregister(NodeType.PASTURE, other);
    assertThat(NodePanels.of(NodeType.PASTURE)).containsSame(panel);

    NodePanels.unregister(NodeType.PASTURE, panel);
    assertThat(NodePanels.of(NodeType.PASTURE)).isEmpty();
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
