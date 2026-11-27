package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.golem.gui.NodePanel;
import art.arcane.adapt.content.golem.gui.NodePanelContext;
import art.arcane.adapt.content.golem.gui.NodePanelControl;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;


final class FishingNodePanel implements NodePanel {
  private final GolemAnglerCore core;
  private final AnglerWaters waters;
  private final Function<Id, ItemRef> icons;

  FishingNodePanel(GolemAnglerCore core, AnglerWaters waters, Function<Id, ItemRef> icons) {
    this.core = Objects.requireNonNull(core);
    this.waters = Objects.requireNonNull(waters);
    this.icons = Objects.requireNonNull(icons);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    boolean open = waters.get(context.key()).map(AnglerWater.Survey::open).orElse(false);
    int level = core.ownerLevel(context.viewer().uuid());
    return List.of(water(open), junk(context.record(), level));
  }

  private NodePanelControl water(boolean open) {
    String name = open
        ? TextFormat.GREEN + Text.key("adapt.golemancy.angler_core.water_ready").resolve()
        : TextFormat.RED + Text.key("adapt.golemancy.angler_core.water_needed").resolve();
    return new NodePanelControl("angler-water", icons.apply(open ? Id.minecraft("water_bucket") : Id.minecraft("barrier")), name,
        List.of(TextFormat.GRAY + Text.key("adapt.golemancy.angler_core.water_lore").resolve()), null, null, null);
  }

  private NodePanelControl junk(NodeRecord record, int level) {
    GolemAnglerCore.Config config = core.config();
    boolean discard = AnglerRules.discardJunk(config, level, record);
    String name = discard
        ? TextFormat.GREEN + Text.key("adapt.golemancy.angler_core.junk_discard").resolve()
        : TextFormat.YELLOW + Text.key("adapt.golemancy.angler_core.junk_keep").resolve();
    ItemRef icon = icons.apply(Id.minecraft("rotten_flesh"));
    if (!GolemancyRules.unlocked(level, config.junkFilterLevel)) {
      return new NodePanelControl("angler-junk", icon, name,
          List.of(TextFormat.RED + Text.key("adapt.golemancy.angler_core.locked").withMarkup("level", String.valueOf(config.junkFilterLevel)).resolve()), null, null, null);
    }
    return new NodePanelControl("angler-junk", icon, name, List.of(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.toggle").resolve()),
        AnglerRules::toggleJunk, null, null);
  }
}
