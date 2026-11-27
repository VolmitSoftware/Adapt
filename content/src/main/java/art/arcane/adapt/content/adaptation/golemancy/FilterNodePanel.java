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


final class FilterNodePanel implements NodePanel {
  private final GolemSorterCore core;
  private final Function<Id, ItemRef> icons;

  FilterNodePanel(GolemSorterCore core, Function<Id, ItemRef> icons) {
    this.core = Objects.requireNonNull(core);
    this.icons = Objects.requireNonNull(icons);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    NodeRecord record = context.record();
    GolemSorterCore.Config config = core.config();
    int level = core.ownerLevel(context.viewer().uuid());
    return List.of(overflow(record, config, level), restock(record, config, level));
  }

  private NodePanelControl overflow(NodeRecord record, GolemSorterCore.Config config, int level) {
    boolean on = SorterRules.overflow(record);
    String name = on ? TextFormat.GREEN + Text.key("adapt.golemancy.sorter_core.overflow_on").resolve()
        : TextFormat.YELLOW + Text.key("adapt.golemancy.sorter_core.overflow_off").resolve();
    ItemRef icon = icons.apply(Id.minecraft("hopper"));
    if (!GolemancyRules.unlocked(level, config.filterLevel)) {
      return new NodePanelControl("sorter-overflow", icon, name, List.of(InputNodePanel.locked(config.filterLevel)), null, null, null);
    }
    return new NodePanelControl("sorter-overflow", icon, name, List.of(TextFormat.GRAY + Text.key("adapt.golemancy.sorter_core.overflow_lore").resolve(),
        TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.toggle").resolve()), SorterRules::toggleOverflow, null, null);
  }

  private NodePanelControl restock(NodeRecord record, GolemSorterCore.Config config, int level) {
    int target = SorterRules.restockTarget(record);
    String name = target > 0 ? TextFormat.GREEN + Text.key("adapt.golemancy.sorter_core.restock").withMarkup("amount", String.valueOf(target)).resolve()
        : TextFormat.YELLOW + Text.key("adapt.golemancy.sorter_core.restock_off").resolve();
    ItemRef icon = icons.apply(Id.minecraft("chest"));
    if (!GolemancyRules.unlocked(level, config.restockLevel)) {
      return new NodePanelControl("sorter-restock", icon, name, List.of(InputNodePanel.locked(config.restockLevel)), null, null, null);
    }
    return new NodePanelControl("sorter-restock", icon, name, List.of(TextFormat.GRAY + Text.key("adapt.golemancy.sorter_core.restock_lore").resolve(),
        TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.area_lore").resolve()),
        current -> SorterRules.stepRestock(current, true), current -> SorterRules.stepRestock(current, false), null);
  }
}
