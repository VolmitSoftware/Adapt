package art.arcane.adapt.api.skill;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiGroupLayout;
import art.arcane.volmlib.util.format.ColorFormatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** What a skill's menu shows a player, shared by the chest menu and the client screens. */
public final class SkillMenus {
  private static final Pattern LEADING_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]+");

  private SkillMenus() {
  }

  /**
   * The adaptations the skill's page shows {@code player}: enabled, permitted ones, row by row in the configured order,
   * then by the plain name the page shows. Names follow the current language audience.
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
    Map<Adaptation<?>, String> sortKeys = new IdentityHashMap<>();
    Map<String, Integer> configuredOrder = GuiConfig.adaptationOrder(skill.getName(), known);
    Comparator<Adaptation<?>> defaultOrder =
        Comparator.comparingInt((Adaptation<?> adaptation) -> GuiConfig.rankOf(configuredOrder, adaptation.getName()))
            .thenComparing((Adaptation<?> adaptation) ->
                sortKeys.computeIfAbsent(adaptation, (Adaptation<?> key) -> sortKey(key.getShortName())))
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
    return LEADING_SYMBOLS.matcher(normalized).replaceFirst("");
  }
}
