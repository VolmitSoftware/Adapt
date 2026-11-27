package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.pack.layout.BarSpec;
import art.arcane.adapt.pack.layout.Card;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.text.Align;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackInk;
import art.arcane.adapt.pack.text.Placement;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * A skill's page for pack viewers, in the skill's kit. The identity row holds the icon, the name in the default font with
 * the level and XP bar at the right when Mutate leaves that place free, else on one line with them after it (the level
 * and a longer bar alone when the name does not fit, which the crumb then carries), the skill switch and Mutate
 * (Split on a mutated skill in debug mode, armed then confirmed). Below, the adaptations flow down the left column of
 * cells and then the right, each group under its heading cell; further cells turn to the next leaf, and a last leaf
 * holds the kept originals and the tools when there are any, on the page's still copy. A cell click opens the
 * adaptation, shift-click learns the next level (armed, then confirmed, when learning is permanent) and shift-right-click
 * unlearns one (armed, then confirmed), each checked by {@link LearnRules} before it is sent.
 */
final class PackSkillScreen {
  static final String SCREEN = "skill";
  /** The leaf of kept originals and tools: the page's still copy, so its icons run on through the dressing's slots. */
  static final String SHELF = "skill.shelf";
  static final String ENABLED = "enabled";
  static final String ON = "ON";
  static final String OFF = "OFF";
  static final String SPLIT = "split";
  static final String LEARN = "learn:";
  static final String UNLEARN = "unlearn:";
  /** Paper between the level and the XP bar after it. */
  static final int XP_GAP = 6;
  /** A margin pip and the gap after it. */
  static final int PIP_PITCH = 3;
  /** The least paper between a list row's name and its level count beside it; a name that would come closer wraps. */
  static final int COUNT_GAP = 6;

  private PackSkillScreen() {
  }

  static PackPage render(PackFrame frame) {
    CatalogIndex catalog = frame.catalog();
    PlayerView player = frame.player();
    PackViewer state = frame.state();
    SkillView skill = catalog.skill(state.skillId);
    if (skill == null || !player.access().canOpen(skill.id())) {
      return null;
    }
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    List<Card> seats = frame.layout(SCREEN).cards();
    List<List<Cell>> leaves = leaves(cells(frame, skill), seats.size());
    List<KeptView> kept = kept(frame, skill);
    List<ToolView> tools = tools(frame, skill);
    boolean shelf = !kept.isEmpty() || !tools.isEmpty();
    int count = leaves.size() + (shelf ? 1 : 0);
    int leaf = Math.min(state.page, count - 1);
    PageBuilder page = frame.page(leaf < leaves.size() ? SCREEN : SHELF, skill.id(), leaf,
        List.of(AdaptLanguage.text(GuiMessages.SKILLS), skill.name()));
    frame.chips(page, skill.id());
    identity(frame, page, skill);
    if (leaf < leaves.size()) {
      List<Cell> shown = leaves.get(leaf);
      List<TextSpec> nameSpecs = new ArrayList<>();
      List<String> names = new ArrayList<>();
      for (int seat = 0; seat < shown.size() && seat < seats.size(); seat++) {
        Cell cell = shown.get(seat);
        if (cell != null) {
          Named name = cell(frame, page, seats.get(seat), skill, cell);
          if (name != null) {
            nameSpecs.add(name.spec());
            names.add(name.markup());
          }
        }
      }
      page.names(nameSpecs, names);
    } else {
      shelf(frame, page, skill, kept, tools);
    }
    int learned = 0;
    int total = 0;
    for (List<AdaptationView> group : catalog.groups(player, skill.id())) {
      for (AdaptationView adaptation : group) {
        total++;
        learned += CatalogIndex.level(player, adaptation.id()) > 0 ? 1 : 0;
      }
    }
    TextSpec learnedSpec = page.layout().text("learned");
    if (learnedSpec != null) {
      String words = AdaptLanguage.text(GuiMessages.PACK_LEARNED_COUNT, trusted("learned", learned), trusted("total", total));
      boolean fits = page.resources().metrics().fits(LegacyText.plain(words), learnedSpec.width());
      page.text(learnedSpec.coloured(PackInk.of(skill.color())), fits ? words : learned + "/" + total);
    }
    page.foot((MenuClick click) -> screens.route(viewer, new Route.Skills()), leaf, count,
        (int target) -> screens.page(viewer, target), List.of());
    return page.build();
  }

  private static void identity(PackFrame frame, PageBuilder page, SkillView skill) {
    PlayerView player = frame.player();
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    SkillLineView line = CatalogIndex.line(player, skill.id());
    int level = line == null ? 0 : line.level();
    List<String> lore = new ArrayList<>();
    if (!skill.description().isEmpty()) {
      lore.addAll(PackLabels.described(skill.description()));
    }
    lore.addAll(PackSkillsScreen.lore(frame, skill, line));
    page.put(page.layout().slot("icon"), PageBuilder.icon(skill.icon(), PackLabels.coloured(PackInk.of(skill.color()), skill.name()), lore), null, page.resources().icon(skill.icon(), page.kit(), false));
    String name = PackLabels.coloured(PackInk.of(skill.color()), skill.name());
    TextSpec nameSpec = page.layout().text("name");
    boolean fits = nameSpec != null && page.resources().fitsDefaultFont(nameSpec, LegacyText.plain(skill.name()));
    int nameLines = fits ? page.resources().lines(nameSpec, name) : 0;
    boolean sideFree = !screens.views().unmutateOffered(viewer, skill.id()) && !screens.views().mutateOffered(viewer, skill.id());
    Identity identity = identity(nameLines, sideFree);
    if (identity != Identity.ALONE) {
      page.text(nameSpec, name);
    }
    Placement levelPlaced = page.text(identity.level, AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", level)));
    TextSpec levelSpec = page.layout().text(identity.level);
    BarSpec xp = page.layout().bar(identity.bar);
    if (identity == Identity.BESIDE && xp != null && levelSpec != null) {
      xp = xp.from(levelSpec.x() + levelPlaced.width() + XP_GAP);
    }
    page.bar(xp, line == null ? 0D : line.progress(), PackInk.of(skill.color()));
    PreferenceView enabled = enabledPreference(skill);
    if (enabled != null && player.access().preferences()) {
      boolean on = ON.equals(CatalogIndex.preference(player, PreferenceOwner.SKILL, skill.id(), enabled));
      page.put(page.layout().slot("switch"), PageBuilder.named(AdaptLanguage.text(GuiMessages.PACK_SKILL_SWITCH),
              List.of(PackLabels.GREY + AdaptLanguage.text(on ? GuiMessages.PACK_ON : GuiMessages.PACK_OFF))),
          (MenuClick click) -> screens.preference(viewer, PreferenceOwner.SKILL, skill.id(), ENABLED, on ? OFF : ON),
          page.resources().item(on ? PackArt.CHECK_ON : PackArt.CHECK, page.kit()));
    }
    if (screens.views().unmutateOffered(viewer, skill.id())) {
      boolean armed = frame.state().armed(SPLIT, screens.now());
      String label = AdaptLanguage.text(armed ? GuiMessages.PACK_CONFIRM_SPLIT : GuiMessages.PACK_UNMUTATE);
      PackSlot slot = page.layout().slot("mutate");
      page.text("mutate", label);
      page.button("mutate", PackArt.BUTTON_PRIMARY, PageBuilder.named(label), (MenuClick click) -> screens.unmutate(viewer, skill.id(), slot),
          armed, PackMutateScreen.SEAL_COOLDOWN);
    } else if (screens.views().mutateOffered(viewer, skill.id())) {
      String label = AdaptLanguage.text(GuiMessages.PACK_MUTATE);
      page.text("mutate", label);
      page.button("mutate", PackArt.BUTTON_PRIMARY, PageBuilder.named(label),
          (MenuClick click) -> screens.route(viewer, new Route.Mutate(skill.id())), false, null);
    }
  }

  /** Where the identity row puts the level for a name of {@code nameLines} default-font lines (0 when it does not fit). */
  static Identity identity(int nameLines, boolean sideFree) {
    return nameLines > 0 && sideFree ? Identity.SIDE : nameLines == 1 ? Identity.BESIDE : Identity.ALONE;
  }

  /**
   * Headings and adaptations in reading order. A mutated skill's groups are headed by the skill each comes from (lead,
   * partner, the mutation itself) in that skill's ink; a grouped skill's by their names.
   */
  static List<Cell> cells(PackFrame frame, SkillView skill) {
    List<List<AdaptationView>> groups = frame.catalog().groups(frame.player(), skill.id());
    List<String> labels = frame.screens().views().groupLabels(frame.viewer(), skill.id());
    List<String> owners = frame.screens().views().groupOwners(frame.viewer(), skill.id());
    int ink = PackInk.of(skill.color());
    List<Cell> cells = new ArrayList<>();
    for (int index = 0; index < groups.size(); index++) {
      List<AdaptationView> group = groups.get(index);
      int declared = declared(skill, group, index);
      if (!owners.isEmpty()) {
        SkillView owner = declared < owners.size() ? frame.catalog().skill(owners.get(declared)) : null;
        if (owner != null) {
          cells.add(new Cell(owner.name(), PackInk.of(owner.color()), null));
        }
      } else if (declared < labels.size()) {
        cells.add(new Cell(labels.get(declared), ink, null));
      }
      for (AdaptationView adaptation : group) {
        cells.add(new Cell(null, ink, adaptation));
      }
    }
    return cells;
  }

  /** The index of {@code group} in the skill's declared row groups, found by its first adaptation; else {@code index}. */
  private static int declared(SkillView skill, List<AdaptationView> group, int index) {
    for (int row = 0; row < skill.rowGroups().size(); row++) {
      if (!group.isEmpty() && skill.rowGroups().get(row).contains(group.get(0).id())) {
        return row;
      }
    }
    return index;
  }

  /** The originals {@code skill} keeps without learning, whose adaptations the catalog knows. */
  static List<KeptView> kept(PackFrame frame, SkillView skill) {
    List<KeptView> kept = new ArrayList<>();
    for (KeptView entry : frame.screens().views().kept(frame.viewer())) {
      if (entry.ownerId().equals(skill.id()) && frame.catalog().adaptation(entry.adaptationId()) != null) {
        kept.add(entry);
      }
    }
    return kept;
  }

  /** The mutation tools the adaptations on {@code skill}'s page hand out, in page order. */
  static List<ToolView> tools(PackFrame frame, SkillView skill) {
    List<ToolView> all = frame.screens().views().tools(frame.viewer());
    List<ToolView> tools = new ArrayList<>();
    for (List<AdaptationView> group : frame.catalog().groups(frame.player(), skill.id())) {
      for (AdaptationView adaptation : group) {
        for (ToolView tool : all) {
          if (tool.adaptationId().equals(adaptation.id())) {
            tools.add(tool);
          }
        }
      }
    }
    return tools;
  }

  /**
   * The leaf after the adaptations: the originals kept without learning under their heading, then the tools the page's
   * adaptations hand out under theirs. On a mutated skill it is drawn in the kit of whichever parent that leaf takes.
   */
  private static void shelf(PackFrame frame, PageBuilder page, SkillView skill, List<KeptView> kept, List<ToolView> tools) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    int ink = PackInk.of(skill.color());
    if (!kept.isEmpty()) {
      heading(page, "kept.heading", "kept.rule", AdaptLanguage.text(MutatedMessages.KEPT), ink);
      List<PackSlot> seats = page.layout().slots("kept");
      int shown = kept.size() > seats.size() ? seats.size() - 1 : kept.size();
      for (int index = 0; index < shown; index++) {
        KeptView entry = kept.get(index);
        AdaptationView original = frame.catalog().adaptation(entry.adaptationId());
        page.put(seats.get(index), PageBuilder.icon(original.icon(), PackLabels.coloured(ink, original.name()), keptLines(original, entry)),
            (MenuClick click) -> screens.route(viewer, new Route.Adaptation(original.id())),
            page.resources().icon(original.icon(), page.kit(), false));
      }
      if (shown < kept.size() && !seats.isEmpty()) {
        List<String> names = new ArrayList<>();
        for (KeptView entry : kept.subList(shown, kept.size())) {
          names.add(PackLabels.GREY + frame.catalog().adaptation(entry.adaptationId()).name());
        }
        page.put(seats.get(seats.size() - 1), PageBuilder.named("+" + names.size(), names).withAmount(Math.min(99, names.size())), null,
            page.resources().item(PackArt.MORE, page.kit()));
      }
    }
    if (!tools.isEmpty()) {
      heading(page, "tools.heading", "tools.rule", AdaptLanguage.text(GuiMessages.PACK_TOOLS), ink);
      List<PackSlot> seats = page.layout().slots("tools");
      for (int index = 0; index < tools.size() && index < seats.size(); index++) {
        ToolView tool = tools.get(index);
        page.put(seats.get(index), PageBuilder.icon(tool.icon(), PackLabels.coloured(ink, tool.name()), toolLines(frame, tool)),
            (MenuClick click) -> screens.route(viewer, new Route.Adaptation(tool.adaptationId())),
            page.resources().icon(tool.icon(), page.kit(), false));
      }
    }
  }

  /** A kept original's tooltip: the level it is kept at, its description, and what that level gives. */
  static List<String> keptLines(AdaptationView original, KeptView entry) {
    List<String> lore = new ArrayList<>();
    lore.add(PackLabels.GREEN + AdaptLanguage.text(MutatedMessages.KEPT) + " "
        + AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", entry.level())));
    if (!original.description().isEmpty()) {
      lore.addAll(PackLabels.described(original.description()));
    }
    if (entry.level() >= 1 && entry.level() <= original.statLines().size()) {
      for (String stat : original.statLines().get(entry.level() - 1)) {
        lore.add(PackLabels.GREEN + PackLabels.stat(stat));
      }
    }
    return lore;
  }

  /** A heading in {@code colour} and its rule running on from it to the rule's end. */
  private static void heading(PageBuilder page, String role, String rule, String text, int colour) {
    TextSpec spec = page.layout().text(role);
    if (spec == null) {
      return;
    }
    Placement placed = page.text(spec.coloured(colour), text);
    page.rule(rule, spec.x() + placed.width() + PackFrame.RULE_GAP);
  }

  /** A tool's tooltip: the adaptation level its recipe unlocks at, green once reached, then the recipe's ingredients. */
  static List<String> toolLines(PackFrame frame, ToolView tool) {
    List<String> lines = new ArrayList<>();
    boolean met = CatalogIndex.level(frame.player(), tool.adaptationId()) >= tool.requiredLevel();
    String requirement = AdaptLanguage.text(GuiMessages.REQUIREMENT_LEVEL,
        trusted("adaptation", frame.catalog().adaptationName(tool.adaptationId())), trusted("level", PackLabels.roman(tool.requiredLevel())));
    lines.add((met ? PackLabels.GREEN : PackLabels.GREY) + AdaptLanguage.text(GuiMessages.REQUIRES, trusted("requirement", requirement)));
    for (ToolView.Ingredient ingredient : tool.ingredients()) {
      lines.add(PackLabels.GREY + ingredient.count() + " × " + PackLabels.itemWords(ingredient.itemId()));
    }
    return lines;
  }

  /**
   * Cells split into leaves of {@code seats}, filling the left column and then the right; a group that would run on from
   * the left column into the right one starts the right one instead when it fits there whole, and a heading that would
   * end a column moves to the next one, leaving the seats it skips empty (null).
   */
  static List<List<Cell>> leaves(List<Cell> cells, int seats) {
    int column = Math.max(1, seats / 2);
    List<List<Cell>> leaves = new ArrayList<>();
    List<Cell> leaf = new ArrayList<>();
    leaves.add(leaf);
    for (int index = 0; index < cells.size(); index++) {
      Cell cell = cells.get(index);
      int group = cell.heading() == null ? 0 : group(cells, index);
      if (group > 0 && leaf.size() > 0 && leaf.size() < column && leaf.size() + group > column && group <= column) {
        while (leaf.size() < column) {
          leaf.add(null);
        }
      } else if (cell.heading() != null && leaf.size() % column == column - 1) {
        leaf.add(null);
      }
      if (leaf.size() >= seats) {
        leaf = new ArrayList<>();
        leaves.add(leaf);
      }
      leaf.add(cell);
    }
    return leaves;
  }

  /** The seats of the group headed at {@code start}: its heading and the adaptations up to the next heading. */
  private static int group(List<Cell> cells, int start) {
    int end = start + 1;
    while (end < cells.size() && cells.get(end).heading() == null) {
      end++;
    }
    return end - start;
  }

  /** Draws one cell; returns an adaptation cell's name and where it goes for the list's names, null for a heading. */
  private static Named cell(PackFrame frame, PageBuilder page, Card card, SkillView skill, Cell cell) {
    if (cell.heading() != null) {
      Placement heading = page.name(card.text("heading"), PackLabels.coloured(cell.colour(), cell.heading()));
      BarSpec rule = card.bar("heading.rule");
      TextSpec spec = card.text("heading");
      if (rule != null && spec != null) {
        page.rule(rule.from(spec.x() + heading.width() + PackFrame.RULE_GAP));
      }
      return null;
    }
    PackSlot first = card.cells().get(0);
    page.paint(PackArt.shade(card.top()), page.resources().layout().grid().x(first));
    AdaptationView adaptation = cell.adaptation();
    KeptView kept = kept(frame, adaptation.id());
    int level = Math.max(LearnRules.floor(kept), CatalogIndex.level(frame.player(), adaptation.id()));
    boolean locked = !LearnRules.missing(frame.player(), adaptation).isEmpty();
    MenuIcon icon = PageBuilder.icon(adaptation.icon(), PackLabels.coloured(PackInk.of(skill.color()), adaptation.name()),
        detail(frame, adaptation));
    page.group(card.cells(), icon, (MenuClick click) -> click(frame, adaptation, click.kind()),
        page.resources().icon(adaptation.icon(), page.kit(), locked, card.shift()));
    TextSpec count = card.text("level");
    Placement counted = page.text(count, PackLabels.coloured(level > 0 ? PackInk.of(skill.color()) : PackLabels.FAINT,
        level + "/" + adaptation.maxLevel()));
    pips(page, card.bar(PackArt.PIPS), count, counted.width(), level, adaptation.maxLevel(), PackInk.of(skill.color()));
    return new Named(besideCount(card.text("name"), count, counted.width()),
        PackLabels.coloured(locked ? PackLabels.FAINT : PackLabels.INK, adaptation.name()));
  }

  /**
   * A row's name label kept {@link #COUNT_GAP} clear of its level count of {@code countWidth} when the count sits after
   * the name on its line, so a longer name wraps or breaks by the label's rule instead of running up to the count.
   */
  static TextSpec besideCount(TextSpec name, TextSpec count, int countWidth) {
    if (name == null || count == null) {
      return name;
    }
    int countLeft = switch (count.align()) {
      case LEFT -> count.x();
      case CENTRE -> count.x() + ((count.width() - countWidth) / 2);
      case RIGHT -> count.x() + count.width() - countWidth;
    };
    int room = countLeft - COUNT_GAP - name.x();
    if (countLeft <= name.x() || room >= name.width()) {
      return name;
    }
    return new TextSpec(name.x(), name.y(), Math.max(1, room), name.align(), name.colour(), name.lines(), name.pitch(), name.top(),
        name.breaks());
  }

  /** One small pip per level in the page margin above the row's count, learned ones in {@code colour} (see {@link #pipBoxes}). */
  static void pips(PageBuilder page, BarSpec bar, TextSpec count, int countWidth, int level, int max, int colour) {
    if (bar == null || count == null) {
      return;
    }
    List<BarSpec> boxes = pipBoxes(bar, count.align(), countWidth, max);
    for (int pip = 0; pip < boxes.size(); pip++) {
      BarSpec box = boxes.get(pip);
      page.rule(new BarSpec(box.x(), box.y(), box.width(), box.height(), pip < level ? colour : bar.colour()));
    }
  }

  /**
   * Where the {@code max} pips of a row go in {@code bar}: aligned to the count's side ({@code align}) and no wider than
   * the count text ({@code countWidth}), in one row when it fits, else in two (the first holding the extra pip); none when
   * two rows do not fit either.
   */
  static List<BarSpec> pipBoxes(BarSpec bar, Align align, int countWidth, int max) {
    List<BarSpec> boxes = new ArrayList<>();
    if (max < 1) {
      return boxes;
    }
    int room = Math.min(bar.width(), Math.max(countWidth, PIP_PITCH));
    int perRow = (max * PIP_PITCH) - 1 <= room ? max : (max + 1) / 2;
    if ((perRow * PIP_PITCH) - 1 > room) {
      return boxes;
    }
    for (int pip = 0; pip < max; pip++) {
      int row = pip / perRow;
      int inRow = Math.min(perRow, max - (row * perRow));
      int width = (inRow * PIP_PITCH) - 1;
      int x = align == Align.RIGHT ? bar.x() + bar.width() - width : bar.x();
      boxes.add(new BarSpec(x + ((pip % perRow) * PIP_PITCH), PackArt.pipRowY(bar, row), PIP_PITCH - 1, bar.height(), bar.colour()));
    }
    return boxes;
  }

  /** What a click on an adaptation cell does: open it, or learn or unlearn one level when the rules allow it. */
  static void click(PackFrame frame, AdaptationView adaptation, ClickKind kind) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    KeptView kept = kept(frame, adaptation.id());
    int level = Math.max(LearnRules.floor(kept), CatalogIndex.level(frame.player(), adaptation.id()));
    if (kind == ClickKind.SHIFT_LEFT) {
      LearnRules.LearnCheck check = LearnRules.learn(frame.catalog(), frame.player(), adaptation, level + 1, kept);
      if (check.allowed() && PackAdaptationScreen.confirmsLearn(frame, adaptation)) {
        screens.armed(viewer, LEARN + adaptation.id(), null, GuiMessages.PACK_CONFIRM_LEARN,
            () -> screens.learn(viewer, adaptation.id(), check.to()));
      } else if (check.allowed()) {
        screens.learn(viewer, adaptation.id(), check.to());
      } else {
        screens.notice(viewer, PackLabels.refusal(frame.catalog(), check));
      }
    } else if (kind == ClickKind.SHIFT_RIGHT) {
      LearnRules.UnlearnCheck check = LearnRules.unlearn(frame.catalog(), frame.player(), adaptation, level - 1, kept);
      if (check.allowed()) {
        screens.armed(viewer, UNLEARN + adaptation.id(), null, GuiMessages.PACK_CONFIRM_UNLEARN,
            () -> screens.unlearn(viewer, adaptation.id(), check.to()));
      } else {
        screens.notice(viewer, PackLabels.refusal(adaptation, check));
      }
    } else if (kind.isLeft() || kind.isRight()) {
      screens.route(viewer, new Route.Adaptation(adaptation.id()));
    }
  }

  /**
   * The cell tooltip, in the order the native skill page shows the pointed adaptation: its level in words, the next
   * step (what learning it costs, or what stops it), the description, then the next level's stats.
   */
  static List<String> detail(PackFrame frame, AdaptationView adaptation) {
    KeptView kept = kept(frame, adaptation.id());
    int level = Math.max(LearnRules.floor(kept), CatalogIndex.level(frame.player(), adaptation.id()));
    List<String> lore = new ArrayList<>();
    lore.add(PackLabels.GREY + (level > 0 ? AdaptLanguage.text(GuiMessages.PACK_LEVEL_OF, trusted("level", PackLabels.roman(level)),
        trusted("maximum", PackLabels.roman(adaptation.maxLevel()))) : AdaptLanguage.text(GuiMessages.PACK_NOT_LEARNED)));
    if (level < adaptation.maxLevel()) {
      lore.addAll(PackLabels.next(frame.catalog(), LearnRules.learn(frame.catalog(), frame.player(), adaptation, level + 1, kept)));
    } else {
      lore.add(PackLabels.GREEN + AdaptLanguage.text(GuiMessages.PACK_LEARNED));
    }
    if (!adaptation.description().isEmpty()) {
      lore.addAll(PackLabels.described(adaptation.description()));
    }
    if (level < adaptation.maxLevel()) {
      for (String stat : adaptation.statLines().get(level)) {
        lore.add(PackLabels.GREEN + PackLabels.stat(stat));
      }
    }
    return lore;
  }

  /** The kept or lent original record for {@code adaptationId} on this player, or null. */
  static KeptView kept(PackFrame frame, String adaptationId) {
    for (KeptView kept : frame.screens().views().kept(frame.viewer())) {
      if (kept.adaptationId().equals(adaptationId)) {
        return kept;
      }
    }
    return null;
  }

  private static PreferenceView enabledPreference(SkillView skill) {
    for (PreferenceView preference : skill.preferences()) {
      if (preference.id().equals(ENABLED)) {
        return preference;
      }
    }
    return null;
  }

  /**
   * Where the identity row puts the level and XP bar: at the right of the name in the Mutate button's place when that is
   * free on the page, after a one-line name, or alone with a longer bar when the name is left to the crumb.
   */
  enum Identity {
    BESIDE("level", PackArt.XP),
    SIDE("level.side", PackArt.XP_SIDE),
    ALONE("level.alone", PackArt.XP_ALONE);

    private final String level;
    private final String bar;

    Identity(String level, String bar) {
      this.level = level;
      this.bar = bar;
    }
  }

  /** An adaptation row's name and the label it is placed in. */
  record Named(TextSpec spec, String markup) {
  }

  /** One seat of the flow: a group heading in its colour, or an adaptation. */
  record Cell(String heading, int colour, AdaptationView adaptation) {
  }
}
