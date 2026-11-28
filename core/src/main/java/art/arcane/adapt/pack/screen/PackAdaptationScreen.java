package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.pack.layout.BarSpec;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.SlotCycle;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.text.PackInk;
import art.arcane.adapt.pack.text.Placement;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * An adaptation's page for pack viewers, in its skill's kit, as Levels, Recipes and Settings tabs. Levels: identity,
 * one pip per level (a click makes that level the target), description, the target level's stats, Learn up to the
 * target (armed, then confirmed, when learning is permanent) and Unlearn (on the first click). Recipes: the 3x3 grid
 * and result of the picked recipe group, its alternatives cycling every {@link #RECIPE_TICKS} ticks, and one pick slot
 * per group when there are several. Settings: one row per preference with its control (next or previous choice, a
 * switch for on and off, a lock when it cannot change) and Reset (armed, then confirmed).
 */
final class PackAdaptationScreen {
  static final String SCREEN = "adaptation";
  static final String LEVELS = "levels";
  static final String RECIPES = "recipes";
  static final String SETTINGS = "settings";
  static final int RECIPE_TICKS = 24;
  static final String PREF_RULE = "pref.rule";
  static final String PREF_ROWS = "pref.rows";
  /** The foot label between back and the tabs: the level picked on Levels, the recipe count on Recipes. */
  static final String FOOT_COUNT = "foot.count";
  static final String LEARN = "learn";
  static final String UNLEARN = "unlearn";
  static final String RESET = "pref.reset";
  private static final Id AIR = Id.minecraft("air");

  private PackAdaptationScreen() {
  }

  static PackPage render(PackFrame frame) {
    PackViewer state = frame.state();
    AdaptationView adaptation = frame.catalog().adaptation(state.adaptationId);
    SkillView skill = adaptation == null ? null : frame.catalog().skill(adaptation.skillId());
    if (skill == null || !frame.player().access().shows(adaptation.skillId(), adaptation.id())) {
      return null;
    }
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    List<List<RecipeView>> recipes = groups(screens.views().recipes(viewer, adaptation.id()));
    List<String> tabs = new ArrayList<>(List.of(LEVELS));
    if (!recipes.isEmpty()) {
      tabs.add(RECIPES);
    }
    if (!adaptation.preferences().isEmpty() && frame.player().access().preferences()) {
      tabs.add(SETTINGS);
    }
    String tab = tabs.get(Math.min(state.tab, tabs.size() - 1));
    PageBuilder page = frame.page(SCREEN + "." + tab, skill.id(), 0, List.of(skill.name(), adaptation.name()));
    frame.stats(page, skill.id());
    switch (tab) {
      case RECIPES -> recipes(frame, page, skill, recipes);
      case SETTINGS -> settings(frame, page, adaptation);
      default -> levels(frame, page, skill, adaptation);
    }
    List<PageBuilder.Tab> foot = new ArrayList<>();
    if (tabs.size() > 1) {
      for (int index = 0; index < tabs.size(); index++) {
        int target = index;
        foot.add(new PageBuilder.Tab(tabs.get(index), tabName(tabs.get(index)), tabs.get(index).equals(tab),
            (MenuClick click) -> screens.tab(viewer, target)));
      }
    }
    page.foot((MenuClick click) -> screens.back(viewer, new Route.Skill(skill.id())), 0, 1, (int ignored) -> { }, foot);
    return page.build();
  }

  private static String tabName(String tab) {
    return AdaptLanguage.text(switch (tab) {
      case RECIPES -> GuiMessages.PACK_RECIPES;
      case SETTINGS -> GuiMessages.PACK_SETTINGS;
      default -> GuiMessages.LEVELS;
    });
  }

  /** The level the page previews and Learn goes to: the picked pip, else the next level, else the current one. */
  static int target(PackFrame frame, AdaptationView adaptation, int level) {
    int picked = frame.state().pickedLevel;
    if (picked > 0 && picked <= adaptation.maxLevel()) {
      return picked;
    }
    return level < adaptation.maxLevel() ? level + 1 : level;
  }

  private static void levels(PackFrame frame, PageBuilder page, SkillView skill, AdaptationView adaptation) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    CatalogIndex catalog = frame.catalog();
    KeptView kept = PackSkillScreen.kept(frame, adaptation.id());
    int floor = LearnRules.floor(kept);
    int level = Math.max(floor, CatalogIndex.level(frame.player(), adaptation.id()));
    int target = target(frame, adaptation, level);
    String rest = description(page, adaptation.description());
    String shown = rest.isEmpty() ? adaptation.description() : rest;
    page.put(page.layout().slot("icon"), PageBuilder.icon(adaptation.icon(), PackLabels.coloured(PackInk.of(skill.color()), adaptation.name()),
        shown.isEmpty() ? List.of() : PackLabels.described(shown)), null, page.resources().icon(adaptation.icon(), page.kit(), level == 0));
    page.name("name", PackLabels.coloured(PackInk.of(skill.color()), adaptation.name()));
    page.text("state", level > 0 ? AdaptLanguage.text(GuiMessages.PACK_LEVEL_OF, trusted("level", PackLabels.roman(level)),
        trusted("maximum", PackLabels.roman(adaptation.maxLevel()))) : AdaptLanguage.text(GuiMessages.PACK_NOT_LEARNED));
    List<PackSlot> pips = page.layout().slots("pips");
    for (int index = 0; index < pips.size() && index < adaptation.maxLevel(); index++) {
      int pip = index + 1;
      String look = pip <= floor ? "kept" : pip <= level ? (target < level && pip > target ? "remove" : "learned")
          : pip <= target ? "target" : "empty";
      List<String> lore = new ArrayList<>();
      for (String stat : adaptation.statLines().get(index)) {
        lore.add(PackLabels.GREEN + PackLabels.stat(stat));
      }
      lore.addAll(pip > level
          ? PackLabels.next(catalog, LearnRules.learn(catalog, frame.player(), adaptation, pip, kept))
          : PackLabels.unlearn(adaptation, LearnRules.unlearn(catalog, frame.player(), adaptation, pip - 1, kept)));
      SlotLook pipLook = page.resources().item(pip == target ? PackArt.pipRing(look, pip) : PackArt.pip(look, pip), page.kit());
      if (look.equals(PackArt.PIP_LEARNED)) {
        pipLook = pipLook.withModelData(List.of(), List.of(), List.of(PackInk.of(skill.color())));
      }
      page.put(pips.get(index), PageBuilder.named(PackLabels.coloured(PackInk.of(skill.color()),
              AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", PackLabels.roman(pip)))), lore),
          (MenuClick click) -> screens.pick(viewer, pip), pipLook);
    }
    List<String> unshown = adaptation.maxLevel() > 0 ? stats(page, adaptation.statLines().get(Math.max(1, target) - 1)) : List.of();
    if (level < adaptation.maxLevel()) {
      int learnTo = Math.max(target, level + 1);
      LearnRules.LearnCheck check = LearnRules.learn(catalog, frame.player(), adaptation, learnTo, kept);
      boolean armed = frame.state().armed(LEARN, screens.now());
      String label = armed ? AdaptLanguage.text(GuiMessages.PACK_CONFIRM_LEARN)
          : AdaptLanguage.text(GuiMessages.PACK_LEARN, trusted("level", PackLabels.roman(learnTo)));
      page.name("learn", armed ? AdaptLanguage.text(GuiMessages.CONFIRM) : label);
      TextSpec cost = page.layout().text("learn.cost");
      Placement costPlaced = page.text(cost, String.valueOf(check.knowledgeCost()));
      PackSlot learn = page.layout().slot("learn");
      if (cost != null && learn != null && costPlaced.width() > 0) {
        page.paint(PackArt.knowledgeMark(page.resources().layout().grid().y(learn)), cost.x() + costPlaced.width() + 1);
      }
      PackSlot slot = page.layout().slot(LEARN);
      boolean confirms = confirmsLearn(frame, adaptation);
      List<String> learnLore = new ArrayList<>(PackLabels.next(catalog, check));
      for (String stat : unshown) {
        learnLore.add(PackLabels.GREEN + PackLabels.stat(stat));
      }
      page.button(LEARN, PackArt.BUTTON_PRIMARY, PageBuilder.named(label, learnLore), (MenuClick click) -> {
        if (check.allowed() && confirms) {
          screens.armed(viewer, LEARN, slot, GuiMessages.PACK_CONFIRM_LEARN, () -> change(frame, adaptation, kept, learnTo));
        } else {
          change(frame, adaptation, kept, learnTo);
        }
      }, armed, confirms ? PackMutateScreen.SEAL_COOLDOWN : null);
    }
    if (level > floor) {
      int unlearnTo = target < level ? target : level - 1;
      LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, frame.player(), adaptation, unlearnTo, kept);
      String label = AdaptLanguage.text(GuiMessages.PACK_UNLEARN);
      page.text(UNLEARN, label);
      page.button(UNLEARN, PackArt.BUTTON_DESTRUCTIVE, PageBuilder.named(label, PackLabels.unlearn(adaptation, check)),
          (MenuClick click) -> {
            if (check.allowed()) {
              change(frame, adaptation, kept, unlearnTo);
            } else {
              screens.refuse(viewer, PackLabels.refusal(adaptation, check));
            }
          }, false, null);
    }
    TextSpec targetSpec = page.layout().text(FOOT_COUNT);
    page.text(targetSpec == null ? null : targetSpec.coloured(PackInk.of(skill.color())),
        AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", PackLabels.roman(target))));
  }

  /** True when learning {@code adaptation} cannot be undone, so a learn waits for a confirming click. */
  static boolean confirmsLearn(PackFrame frame, AdaptationView adaptation) {
    return adaptation.permanent() && !CatalogIndex.access(frame.player()).debug();
  }

  /**
   * The description wrapped inside its label. One longer than the label shows as many whole lines as fit, ending at the
   * last sentence that ends inside them when there is one, else at the last whole word, with no dots; the rest is
   * returned for the icon's tooltip (empty when it all fits).
   */
  static String description(PageBuilder page, String description) {
    TextSpec spec = page.layout().text("description");
    if (spec == null || description.isEmpty()) {
      return "";
    }
    String plain = LegacyText.plain(MarkupPort.current().legacy(description)).replaceAll("\\s+", " ").trim();
    List<String> lines = page.resources().metrics().wrapWords(plain, spec.width());
    if (lines.size() <= spec.lines()) {
      page.text(spec, description);
      return "";
    }
    String visible = String.join(" ", lines.subList(0, spec.lines()));
    int sentence = sentenceEnd(visible);
    if (sentence > 0) {
      visible = visible.substring(0, sentence);
    }
    page.text(spec, visible);
    return plain.substring(Math.min(plain.length(), visible.length())).trim();
  }

  /** The index just past the last sentence end ({@code .}, {@code !} or {@code ?} before a space) in {@code text}, or 0. */
  static int sentenceEnd(String text) {
    for (int index = text.length() - 2; index > 0; index--) {
      char c = text.charAt(index);
      if ((c == '.' || c == '!' || c == '?') && text.charAt(index + 1) == ' ') {
        return index + 1;
      }
    }
    return 0;
  }

  /**
   * The stat lines, each wrapped onto as many lines as it needs while the label has lines left; the stats that do not
   * fit are returned whole for the Learn tooltip.
   */
  static List<String> stats(PageBuilder page, List<String> lines) {
    TextSpec stats = page.layout().text("stats");
    List<String> unshown = new ArrayList<>();
    int used = 0;
    for (String line : lines) {
      String text = PackLabels.stat(line);
      if (stats == null || !unshown.isEmpty() || used >= stats.lines()) {
        unshown.add(line);
        continue;
      }
      TextSpec rest = new TextSpec(stats.x(), stats.y() + (used * stats.pitch(page.resources().metrics().lineHeight())), stats.width(),
          stats.align(), stats.colour(), stats.lines() - used, stats.pitch());
      String plain = LegacyText.plain(MarkupPort.current().legacy(text));
      int needed = page.resources().metrics().wrapWords(plain, rest.width()).size();
      if (used + needed > stats.lines()) {
        unshown.add(line);
        continue;
      }
      page.text(new TextSpec(rest.x(), rest.y(), rest.width(), rest.align(), rest.colour(), needed, rest.pitch()), text);
      used += needed;
    }
    return unshown;
  }

  /** Learns or unlearns to {@code target} when the rules allow it, else says why not. */
  static void change(PackFrame frame, AdaptationView adaptation, KeptView kept, int target) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    int level = Math.max(LearnRules.floor(kept), CatalogIndex.level(frame.player(), adaptation.id()));
    if (target > level) {
      LearnRules.LearnCheck check = LearnRules.learn(frame.catalog(), frame.player(), adaptation, target, kept);
      if (check.allowed()) {
        screens.learn(viewer, adaptation.id(), check.to());
      } else {
        screens.refuse(viewer, PackLabels.refusal(frame.catalog(), check));
      }
      return;
    }
    LearnRules.UnlearnCheck check = LearnRules.unlearn(frame.catalog(), frame.player(), adaptation, target, kept);
    if (check.allowed()) {
      screens.unlearn(viewer, adaptation.id(), check.to());
    } else {
      screens.refuse(viewer, PackLabels.refusal(adaptation, check));
    }
  }

  private static void recipes(PackFrame frame, PageBuilder page, SkillView skill, List<List<RecipeView>> groups) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    int picked = Math.min(frame.state().recipe, groups.size() - 1);
    List<RecipeView> group = groups.get(picked);
    page.text("recipe.name", PackLabels.coloured(PackInk.of(skill.color()), name(group.get(0).result())));
    List<PackSlot> grid = page.layout().slots("recipe.grid");
    for (PackSlot tile : grid) {
      page.face(PackArt.TILE, tile);
    }
    PackSlot result = page.layout().slot("recipe.result");
    page.face(PackArt.TILE_SELECTED, result);
    if (result != null) {
      page.paint(PackArt.arrow(page.resources().layout().grid().y(result)), page.resources().layout().grid().x(result));
    }
    for (int cell = 0; cell < grid.size(); cell++) {
      List<MenuIcon> frames = new ArrayList<>(group.size());
      for (RecipeView recipe : group) {
        frames.add(item(cell(recipe, cell)));
      }
      place(page, grid.get(cell), frames);
    }
    List<MenuIcon> results = new ArrayList<>(group.size());
    for (RecipeView recipe : group) {
      results.add(item(recipe.result()));
    }
    place(page, page.layout().slot("recipe.result"), results);
    List<PackSlot> picks = groups.size() > 1 ? page.layout().slots("recipe.pick") : List.of();
    if (picked < picks.size()) {
      page.face(PackArt.TILE_SELECTED, picks.get(picked));
    }
    for (int index = 0; index < picks.size() && index < groups.size(); index++) {
      RecipeView first = groups.get(index).get(0);
      int choice = index;
      MenuIcon icon = PageBuilder.icon(first.result().icon(), name(first.result()), List.of(PackLabels.GREY
          + AdaptLanguage.text(GuiMessages.PACK_UNLOCKS_AT, trusted("level", first.requiredLevel()))));
      page.put(picks.get(index), icon, (MenuClick click) -> screens.recipe(viewer, choice),
          page.resources().icon(first.result().icon(), page.kit(), index != picked));
    }
    if (groups.size() > 1) {
      page.text(FOOT_COUNT, AdaptLanguage.text(GuiMessages.PACK_RECIPE_COUNT, trusted("count", groups.size())));
    }
  }

  private static void place(PageBuilder page, PackSlot slot, List<MenuIcon> frames) {
    if (slot == null || frames.stream().allMatch((MenuIcon icon) -> icon.type().equals(AIR))) {
      return;
    }
    page.put(slot, frames.get(0), null, null);
    if (frames.size() > 1) {
      page.cycle(new SlotCycle(slot, RECIPE_TICKS, frames));
    }
  }

  /** The input in 3x3 grid cell {@code cell}: shaped recipes keep their width, the rest fill row by row. */
  static RecipeView.Item cell(RecipeView recipe, int cell) {
    int row = cell / 3;
    int column = cell % 3;
    int index;
    if (recipe.kind() == RecipeView.Kind.SHAPED) {
      int width = Math.max(1, recipe.width());
      index = column < width ? (row * width) + column : -1;
    } else {
      index = cell;
    }
    return index < 0 || index >= recipe.inputs().size() ? RecipeView.Item.NONE : recipe.inputs().get(index);
  }

  private static MenuIcon item(RecipeView.Item item) {
    if (item.empty()) {
      return MenuIcon.of(AIR, null, List.of());
    }
    return PageBuilder.stack(item.icon(), item.name().isEmpty() ? null : item.name(), List.of(), item.count());
  }

  /** The item's own name, else its item id's name in words ({@code minecraft:cobweb} is "Cobweb"). */
  static String name(RecipeView.Item item) {
    if (!item.name().isEmpty()) {
      return item.name();
    }
    String words = PackLabels.itemWords(item.icon().itemId());
    return words == null ? AdaptLanguage.text(GuiMessages.PACK_RECIPES) : words;
  }

  /** Recipes in page order, those sharing a group together. */
  static List<List<RecipeView>> groups(List<RecipeView> recipes) {
    Map<String, List<RecipeView>> grouped = new LinkedHashMap<>();
    int loose = 0;
    for (RecipeView recipe : recipes) {
      String key = recipe.group().isEmpty() ? "#" + loose++ : recipe.group();
      grouped.computeIfAbsent(key, (String ignored) -> new ArrayList<>()).add(recipe);
    }
    return new ArrayList<>(grouped.values());
  }

  private static void settings(PackFrame frame, PageBuilder page, AdaptationView adaptation) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    int level = CatalogIndex.level(frame.player(), adaptation.id());
    List<PackSlot> rows = page.layout().slots(PREF_ROWS);
    for (int row = 0; row < rows.size() && row < adaptation.preferences().size(); row++) {
      PreferenceView preference = adaptation.preferences().get(row);
      String current = CatalogIndex.preference(frame.player(), PreferenceOwner.ADAPTATION, adaptation.id(), preference);
      PackSlot control = rows.get(row);
      int x = page.resources().layout().grid().x(control);
      int y = page.resources().layout().grid().y(control);
      String refusal = refusal(preference, level);
      boolean toggle = toggle(preference);
      TextSpec labelSpec = page.layout().text("pref.label");
      boolean valued = !toggle && labelSpec != null
          && page.resources().metrics().fits(LegacyText.plain(MarkupPort.current().legacy(preference.label())), labelSpec.width());
      page.name(page.layout().text(valued ? "pref.label" : "pref.label.single").moved(x, y),
          PackLabels.coloured(refusal == null ? PackLabels.INK : PackLabels.FAINT, preference.label()));
      if (valued) {
        page.name(page.layout().text("pref.value").moved(x, y), label(preference, current));
      }
      if (row < Math.min(rows.size(), adaptation.preferences().size()) - 1) {
        BarSpec rule = page.layout().bar(PREF_RULE);
        page.rule(rule == null ? null : rule.moved(x, y));
      }
      if (refusal != null) {
        page.put(control, PageBuilder.named(preference.label(), List.of(PackLabels.GREY + refusal)), null,
            page.resources().item(PackArt.LOCK_OFF, page.kit()));
        continue;
      }
      if (toggle) {
        boolean on = PackSkillScreen.ON.equals(current);
        page.put(control, PageBuilder.named(preference.label(), List.of(PackLabels.GREY + label(preference, current))),
            (MenuClick click) -> screens.preference(viewer, PreferenceOwner.ADAPTATION, adaptation.id(), preference.id(),
                on ? PackSkillScreen.OFF : PackSkillScreen.ON), page.resources().item(on ? PackArt.CHECK_ON : PackArt.CHECK, page.kit()));
        continue;
      }
      List<String> lore = new ArrayList<>();
      for (PreferenceChoiceView choice : preference.choices()) {
        boolean chosen = choice.value().equals(current);
        boolean open = choice.minimumLevel() <= level;
        lore.add(PackLabels.coloured(chosen ? PackLabels.BLUE : PackLabels.MUTED,
            (chosen ? "> " : "  ") + choice.label() + (open ? "" : " (" + AdaptLanguage.text(GuiMessages.PACK_LV,
                trusted("level", choice.minimumLevel())) + ")")));
      }
      page.put(control, PageBuilder.named(preference.label(), lore), (MenuClick click) -> {
        String next = step(preference, current, level, click.kind() == ClickKind.RIGHT ? -1 : 1);
        if (next != null) {
          screens.preference(viewer, PreferenceOwner.ADAPTATION, adaptation.id(), preference.id(), next);
        }
      }, page.resources().item(PackArt.NEXT, page.kit()));
    }
    boolean armed = frame.state().armed(RESET, screens.now());
    String label = AdaptLanguage.text(armed ? GuiMessages.PACK_CONFIRM_RESET : GuiMessages.PACK_RESET);
    page.text(RESET, label);
    PackSlot slot = page.layout().slot(RESET);
    page.button(RESET, PackArt.BUTTON, PageBuilder.named(AdaptLanguage.text(armed ? GuiMessages.PACK_CONFIRM_RESET : GuiMessages.PREFERENCE_RESET)),
        (MenuClick click) -> screens.armed(viewer, RESET, slot, GuiMessages.PACK_CONFIRM_RESET,
            () -> screens.reset(viewer, PreferenceOwner.ADAPTATION, adaptation.id())),
        armed, PackMutateScreen.SEAL_COOLDOWN);
  }

  /** A preference of exactly the choices on and off shows as a switch. */
  static boolean toggle(PreferenceView preference) {
    return preference.choices().size() == 2 && preference.choices().stream()
        .allMatch((PreferenceChoiceView choice) -> choice.value().equals(PackSkillScreen.ON)
            || choice.value().equals(PackSkillScreen.OFF));
  }

  /** The next choice the player may pick, {@code direction} steps on and wrapping, or null when there is none. */
  static String step(PreferenceView preference, String current, int level, int direction) {
    List<PreferenceChoiceView> choices = preference.choices();
    int at = 0;
    for (int index = 0; index < choices.size(); index++) {
      if (choices.get(index).value().equals(current)) {
        at = index;
      }
    }
    for (int moved = 1; moved < choices.size(); moved++) {
      PreferenceChoiceView choice = choices.get(Math.floorMod(at + (direction * moved), choices.size()));
      if (choice.minimumLevel() <= level) {
        return choice.value();
      }
    }
    return null;
  }

  /** Why the preference cannot change now, or null when it can. */
  static String refusal(PreferenceView preference, int level) {
    if (!preference.playerEditable()) {
      return AdaptLanguage.text(GuiMessages.PREFERENCE_SERVER_CONTROLLED);
    }
    if (level <= 0) {
      return AdaptLanguage.text(GuiMessages.PREFERENCE_LEARN_FIRST);
    }
    return null;
  }

  private static String label(PreferenceView preference, String value) {
    for (PreferenceChoiceView choice : preference.choices()) {
      if (choice.value().equals(value)) {
        return choice.label();
      }
    }
    return value;
  }
}
