package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.gui.NodePanel;
import art.arcane.adapt.api.golem.gui.NodePanelContext;
import art.arcane.adapt.api.golem.gui.NodePanelControl;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class BreakerNodePanel implements NodePanel {
  private static final Toggle SILK = new Toggle("breaker-silk", Id.minecraft("string"), GolemancyMessages.BREAKER_CORE_SILK_ON,
      GolemancyMessages.BREAKER_CORE_SILK_OFF, BreakerRules::toggleSilk);
  private static final Toggle FORTUNE = new Toggle("breaker-fortune", Id.minecraft("diamond"), GolemancyMessages.BREAKER_CORE_FORTUNE_ON,
      GolemancyMessages.BREAKER_CORE_FORTUNE_OFF, BreakerRules::toggleFortune);

  private final GolemBreakerCore core;
  private final Function<Id, ItemRef> icons;

  BreakerNodePanel(GolemBreakerCore core, Function<Id, ItemRef> icons) {
    this.core = Objects.requireNonNull(core);
    this.icons = Objects.requireNonNull(icons);
  }

  @Override
  public List<NodePanelControl> controls(NodePanelContext context) {
    NodeRecord record = context.record();
    GolemBreakerCore.Config config = core.config();
    int level = core.ownerLevel(context.viewer().uuid());
    return List.of(
        filter(record),
        toggle(SILK, BreakerRules.silk(config, level, record), level, config.silkTouchLevel),
        toggle(FORTUNE, BreakerRules.fortune(config, level, record), level, config.fortuneLevel));
  }

  private NodePanelControl filter(NodeRecord record) {
    boolean ready = BreakerRules.filtered(record);
    String name = ready
        ? C.GREEN + AdaptLanguage.text(GolemancyMessages.BREAKER_CORE_FILTER_READY)
        : C.RED + AdaptLanguage.text(GolemancyMessages.BREAKER_CORE_FILTER_NEEDED);
    return new NodePanelControl("breaker-filter", icons.apply(ready ? Id.minecraft("iron_pickaxe") : Id.minecraft("barrier")), name,
        List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.BREAKER_CORE_FILTER_LORE)), null, null, null);
  }

  private NodePanelControl toggle(Toggle toggle, boolean on, int level, int unlockLevel) {
    String name = on ? C.GREEN + AdaptLanguage.text(toggle.onName()) : C.YELLOW + AdaptLanguage.text(toggle.offName());
    ItemRef icon = icons.apply(toggle.icon());
    if (!GolemancyRules.unlocked(level, unlockLevel)) {
      return new NodePanelControl(toggle.id(), icon, name,
          List.of(C.RED + AdaptLanguage.text(GolemancyMessages.BREAKER_CORE_LOCKED, trusted("level", unlockLevel))), null, null, null);
    }
    return new NodePanelControl(toggle.id(), icon, name,
        List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TOGGLE), C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.BREAKER_CORE_EXCLUSIVE)),
        toggle.flip(), null, null);
  }

  private record Toggle(String id, Id icon, TextKey onName, TextKey offName, UnaryOperator<NodeRecord> flip) {
  }
}
