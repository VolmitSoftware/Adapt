package art.arcane.adapt.api.skill;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiGroupLayout;
import art.arcane.volmlib.util.format.ColorFormatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What a skill's menu shows a player, shared by the chest menu and the client screens. */
public final class SkillMenus {
  private SkillMenus() {
  }

  /**
   * The adaptations the skill's page shows {@code player}: enabled, permitted ones, row by row in the configured order,
   * then by plain display name. Display names follow the current language audience.
   */
  public static List<List<Adaptation<?>>> adaptationRows(Skill<?> skill, PlayerRef player) {
    List<Adaptation<?>> visible = new ArrayList<>();
    List<String> known = new ArrayList<>();
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      known.add(adaptation.getName());
      if (!adaptation.isEnabled() || !adaptation.getSkill().isEnabled()) {
        continue;
      }
      if (!adaptation.hasUsePermission(player, adaptation)) {
        continue;
      }
      visible.add(adaptation);
    }
    Map<String, Integer> configuredOrder = GuiConfig.adaptationOrder(skill.getName(), known);
    Comparator<Adaptation<?>> defaultOrder =
        Comparator.comparingInt((Adaptation<?> adaptation) -> GuiConfig.rankOf(configuredOrder, adaptation.getName()))
            .thenComparing((Adaptation<?> adaptation) -> sortKey(adaptation.getDisplayName()))
            .thenComparing(Adaptation::getName, String.CASE_INSENSITIVE_ORDER);
    return GuiGroupLayout.arrange(
        visible,
        Adaptation::getName,
        (Adaptation<?> adaptation) -> GuiConfig.rankOf(configuredOrder, adaptation.getName()),
        defaultOrder,
        skill.getAdaptationRowGroups()
    );
  }

  /** Lower-case plain text without leading symbols, the menus' sort key. */
  public static String sortKey(String value) {
    if (value == null) {
      return "";
    }
    String normalized = ColorFormatter.stripColor(value).toLowerCase(Locale.ROOT).trim();
    return normalized.replaceFirst("^[^\\p{L}\\p{N}]+", "");
  }
}
