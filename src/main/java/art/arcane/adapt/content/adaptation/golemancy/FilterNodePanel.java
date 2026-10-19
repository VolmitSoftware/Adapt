package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.gui.NodePanel;
import art.arcane.adapt.api.golem.gui.NodePanelContext;
import art.arcane.adapt.api.golem.gui.NodePanelControl;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class FilterNodePanel implements NodePanel {
  private final GolemSorterCore core;
  private final Function<Material, ItemStack> icons;

  FilterNodePanel(GolemSorterCore core, Function<Material, ItemStack> icons) {
    this.core = Objects.requireNonNull(core);
    this.icons = Objects.requireNonNull(icons);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    NodeRecord record = context.record();
    GolemSorterCore.Config config = core.config();
    int level = core.ownerLevel(context.viewer().getUniqueId());
    return List.of(overflow(record, config, level), restock(record, config, level));
  }

  private NodePanelControl overflow(NodeRecord record, GolemSorterCore.Config config, int level) {
    boolean on = SorterRules.overflow(record);
    String name = on ? C.GREEN + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_OVERFLOW_ON)
        : C.YELLOW + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_OVERFLOW_OFF);
    ItemStack icon = icons.apply(Material.HOPPER);
    if (!GolemancyRules.unlocked(level, config.filterLevel)) {
      return new NodePanelControl("sorter-overflow", icon, name, List.of(InputNodePanel.locked(config.filterLevel)), null, null, null);
    }
    return new NodePanelControl("sorter-overflow", icon, name, List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_OVERFLOW_LORE),
        C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TOGGLE)), SorterRules::toggleOverflow, null, null);
  }

  private NodePanelControl restock(NodeRecord record, GolemSorterCore.Config config, int level) {
    int target = SorterRules.restockTarget(record);
    String name = target > 0 ? C.GREEN + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_RESTOCK, trusted("amount", target))
        : C.YELLOW + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_RESTOCK_OFF);
    ItemStack icon = icons.apply(Material.CHEST);
    if (!GolemancyRules.unlocked(level, config.restockLevel)) {
      return new NodePanelControl("sorter-restock", icon, name, List.of(InputNodePanel.locked(config.restockLevel)), null, null, null);
    }
    return new NodePanelControl("sorter-restock", icon, name, List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_RESTOCK_LORE),
        C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_AREA_LORE)),
        current -> SorterRules.stepRestock(current, true), current -> SorterRules.stepRestock(current, false), null);
  }
}
