package art.arcane.adapt.engine.gui;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/** Long lists in chest menu tooltips: capped so a tooltip stays readable, with the count of what is left out. */
public final class LoreLists {
  /** Names a tooltip lists before it says how many more there are. */
  public static final int CAP = 8;
  private static final Id TOOLS = Id.minecraft("iron_pickaxe");

  private LoreLists() {
  }

  /** One line per name, at most {@link #CAP}, then "and N more". */
  public static List<String> lines(String color, List<String> names) {
    List<String> lines = new ArrayList<>(Math.min(names.size(), CAP) + 1);
    for (int index = 0; index < names.size() && index < CAP; index++) {
      lines.add(color + names.get(index));
    }
    if (names.size() > CAP) {
      lines.add(TextFormat.DARK_GRAY + more(names.size() - CAP));
    }
    return lines;
  }

  /** The names joined with commas, at most {@link #CAP}, then "and N more"; empty for no names. */
  public static String joined(List<String> names) {
    if (names.isEmpty()) {
      return "";
    }
    String listed = String.join(", ", names.subList(0, Math.min(CAP, names.size())));
    return names.size() > CAP ? listed + ", " + more(names.size() - CAP) : listed;
  }

  public static String more(int count) {
    return AdaptLanguage.text(GuiMessages.AND_MORE, trusted("count", count));
  }

  /** The footer's Tools item listing the tools {@code adaptations} hand out; null when they hand out none. */
  public static GuiElement tools(List<? extends Adaptation<?>> adaptations) {
    Set<String> names = new LinkedHashSet<>();
    for (Adaptation<?> adaptation : adaptations) {
      for (MutationTool<?> tool : MutationTools.handedOut(adaptation)) {
        names.add(TextFormat.strip(MutationTools.name(tool)));
      }
    }
    if (names.isEmpty()) {
      return null;
    }
    return GuiTheme.element("tools", TOOLS, "gui", "navigation", "tools")
        .setName(TextFormat.WHITE + AdaptLanguage.text(GuiMessages.PACK_TOOLS))
        .addLore(lines(TextFormat.GRAY.toString(), new ArrayList<>(names)))
        .setRole(MenuRole.INFO);
  }
}
