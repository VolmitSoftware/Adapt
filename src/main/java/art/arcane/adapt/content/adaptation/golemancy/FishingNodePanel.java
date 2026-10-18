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

final class FishingNodePanel implements NodePanel {
  private final GolemAnglerCore core;
  private final AnglerWaters waters;
  private final Function<Material, ItemStack> icons;

  FishingNodePanel(GolemAnglerCore core, AnglerWaters waters, Function<Material, ItemStack> icons) {
    this.core = Objects.requireNonNull(core);
    this.waters = Objects.requireNonNull(waters);
    this.icons = Objects.requireNonNull(icons);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    boolean open = waters.get(context.key()).map(AnglerWater.Survey::open).orElse(false);
    int level = core.ownerLevel(context.viewer().getUniqueId());
    return List.of(water(open), junk(context.record(), level));
  }

  private NodePanelControl water(boolean open) {
    String name = open
        ? C.GREEN + AdaptLanguage.text(GolemancyMessages.ANGLER_CORE_WATER_READY)
        : C.RED + AdaptLanguage.text(GolemancyMessages.ANGLER_CORE_WATER_NEEDED);
    return new NodePanelControl("angler-water", icons.apply(open ? Material.WATER_BUCKET : Material.BARRIER), name,
        List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.ANGLER_CORE_WATER_LORE)), null, null, null);
  }

  private NodePanelControl junk(NodeRecord record, int level) {
    GolemAnglerCore.Config config = core.config();
    boolean discard = AnglerRules.discardJunk(config, level, record);
    String name = discard
        ? C.GREEN + AdaptLanguage.text(GolemancyMessages.ANGLER_CORE_JUNK_DISCARD)
        : C.YELLOW + AdaptLanguage.text(GolemancyMessages.ANGLER_CORE_JUNK_KEEP);
    ItemStack icon = icons.apply(Material.ROTTEN_FLESH);
    if (!AnglerRules.unlocked(level, config.junkFilterLevel)) {
      return new NodePanelControl("angler-junk", icon, name,
          List.of(C.RED + AdaptLanguage.text(GolemancyMessages.ANGLER_CORE_LOCKED, trusted("level", config.junkFilterLevel))), null, null, null);
    }
    return new NodePanelControl("angler-junk", icon, name, List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TOGGLE)),
        AnglerRules::toggleJunk, null, null);
  }
}
