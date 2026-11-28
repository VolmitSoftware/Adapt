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
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackInk;
import art.arcane.adapt.pack.text.Placement;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * A skill's page for pack viewers, in the skill's kit. The identity row holds the icon, the name in the default font with
 * the level and XP bar at the right when Mutate leaves that place free, else on one line with them after it (the level
 * and a longer bar alone when the name does not fit, which the crumb then carries), the skill switch and Mutate
 * (Split on a mutated skill in debug mode, armed then confirmed). Below, the adaptations fill two columns left to right,
 * each group under its heading and kept to one leaf (see {@link #leaves}); further groups turn to the next leaf, and a
 * last leaf holds the kept originals and the tools when there are any, on the page's still copy. A cell click opens the
 * adaptation, shift-click learns the next level (armed, then confirmed, when learning is permanent) and shift-right-click
 * unlearns one, each checked by {@link LearnRules} before it is sent.
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
  /** Paper between the level and the XP bar after it. */
  static final int XP_GAP = 6;
  /** The least paper between a list row's name and its box's right edge. */
  static final int BOX_PAD = 2;
  /** Inks below this saturation or above this brightness wash at the stronger alpha. */
  private static final float STRONG_SATURATION = 0.45F;
  private static final float STRONG_BRIGHTNESS = 0.85F;
  /** How far a row's outline is mixed from its skill's ink towards the paper. */
  static final float OUTLINE_SHARE = 0.45F;

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
    int gap = gap(seats);
    List<List<Cell>> leaves = leaves(cells(frame, skill), seats.size(), gap, frame.lower());
    List<KeptView> kept = kept(frame, skill);
    List<ToolView> tools = tools(frame, skill);
    boolean shelf = !kept.isEmpty() || !tools.isEmpty();
    int count = leaves.size() + (shelf ? 1 : 0);
    int leaf = Math.min(state.page, count - 1);
    PageBuilder page = frame.page(leaf < leaves.size() ? SCREEN : SHELF, skill.id(), leaf,
        List.of(AdaptLanguage.text(GuiMessages.SKILLS), skill.name()));
    frame.stats(page, skill.id());
    identity(frame, page, skill);
    if (leaf < leaves.size()) {
      List<Cell> shown = leaves.get(leaf);
      List<TextSpec> nameSpecs = new ArrayList<>();
      List<String> names = new ArrayList<>();
      int lines = seats.size() / 2 + (gap < 0 ? 0 : 1);
      for (int place = 0; place < shown.size() && place < lines * 2; place++) {
        Cell cell = shown.get(place);
        if (cell == null) {
          continue;
        }
        Card card = seat(seats, gap, place);
        Card beside = place % 2 == 0 && cell.heading() != null && shown.get(place + 1) == null ? seat(seats, gap, place + 1) : null;
        Named name = cell(frame, page, card, beside, skill, cell);
        if (name != null) {
          nameSpecs.add(name.spec());
          names.add(name.markup());
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
    page.foot((MenuClick click) -> screens.back(viewer, new Route.Skills()), leaf, count,
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
      page.control(page.layout().slot("switch"), PageBuilder.named(AdaptLanguage.text(GuiMessages.PACK_SKILL_SWITCH),
              List.of(PackLabels.GREY + AdaptLanguage.text(on ? GuiMessages.PACK_ON : GuiMessages.PACK_OFF))),
          (MenuClick click) -> screens.preference(viewer, PreferenceOwner.SKILL, skill.id(), ENABLED, on ? OFF : ON),
          on ? PackArt.CHECK_ON : PackArt.CHECK);
    }
    if (screens.views().unmutateOffered(viewer, skill.id())) {
      boolean armed = frame.state().armed(SPLIT, screens.now());
      String label = AdaptLanguage.text(armed ? GuiMessages.PACK_CONFIRM_SPLIT : GuiMessages.PACK_UNMUTATE);
      PackSlot slot = page.layout().slot("mutate");
      page.text("mutate", label);
      page.button("mutate", mutateFace(page), PageBuilder.named(label), (MenuClick click) -> screens.unmutate(viewer, skill.id(), slot),
          armed, PackMutateScreen.SEAL_COOLDOWN);
    } else if (screens.views().mutateOffered(viewer, skill.id())) {
      String label = AdaptLanguage.text(GuiMessages.PACK_MUTATE);
      page.text("mutate", label);
      page.button("mutate", mutateFace(page), PageBuilder.named(label),
          (MenuClick click) -> screens.route(viewer, new Route.Mutate(skill.id())), false, null);
    }
  }

  /** The kit's own Mutate face when the pack has one for the Mutate row, else the plain primary button. */
  static String mutateFace(PageBuilder page) {
    PackSlot slot = page.layout().slot("mutate");
    String face = PackArt.mutate(page.kit());
    boolean baked = slot != null && page.resources().hasPaint(PackArt.face(face, page.resources().layout().grid().y(slot)));
    return baked ? face : PackArt.BUTTON_PRIMARY;
  }

  /** Where the identity row puts the level: under a one-line name, at the free side of a two-line one, else alone. */
  static Identity identity(int nameLines, boolean sideFree) {
    return nameLines == 1 ? Identity.BESIDE : nameLines > 1 && sideFree ? Identity.SIDE : Identity.ALONE;
  }

  /**
   * Headings and adaptations in reading order. A mutated skill's groups are headed by the skill each comes from (lead,
   * partner, the mutation itself) in that skill's ink; a grouped skill's by their names.
   */
  static List<Cell> cells(PackFrame frame, SkillView skill) {
    List<SkillView> owners = new ArrayList<>();
    for (String owner : frame.screens().views().groupOwners(frame.viewer(), skill.id())) {
      owners.add(frame.catalog().skill(owner));
    }
    return cells(skill, frame.catalog().groups(frame.player(), skill.id()), frame.screens().views().groupLabels(frame.viewer(), skill.id()),
        owners);
  }

  /**
   * {@code groups} as headings and adaptations: under {@code owners} (one per declared group, null where unknown) when
   * there are any, else under {@code labels}.
   */
  static List<Cell> cells(SkillView skill, List<List<AdaptationView>> groups, List<String> labels, List<SkillView> owners) {
    int ink = PackInk.of(skill.color());
    List<Cell> cells = new ArrayList<>();
    for (int index = 0; index < groups.size(); index++) {
      List<AdaptationView> group = groups.get(index);
      int declared = declared(skill, group, index);
      if (!owners.isEmpty()) {
        SkillView owner = declared < owners.size() ? owners.get(declared) : null;
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

  /** The card of place {@code place} of a leaf, two places to a line; a place on the gap line is its heading place there. */
  static Card seat(List<Card> seats, int gap, int place) {
    int column = seats.size() / 2;
    int line = place / 2;
    int seat = ((place % 2) * column) + (gap >= 0 && line > gap ? line - 1 : line);
    return gap >= 0 && line == gap ? Card.between(seats.get(seat - 1), seats.get(seat)) : seats.get(seat);
  }

  /**
   * The seat in each list column below which the gap between the chest rows and the inventory rows lies (the first seat
   * on the inventory rows), or -1 when the columns do not cross it.
   */
  static int gap(List<Card> seats) {
    int column = seats.size() / 2;
    for (int seat = 1; seat < column; seat++) {
      if (Card.gap(seats.get(seat - 1), seats.get(seat))) {
        return seat;
      }
    }
    return -1;
  }

  /**
   * Cells split into leaves of {@code seats}, two to a line and read left to right, line by line. A group never runs
   * from one leaf onto the next: one that does not fit what is left of a leaf starts the next with its heading, unless
   * it is too long for a whole leaf, when it runs on under its heading repeated. A heading takes a line of its own, or
   * the free right place after a group that ends on a left place when its group starts on the next line. With a
   * {@code gap} seat (see {@link #gap}) each column has one more line, in the gap above that seat, which holds only a
   * heading: a heading that would land on the line above the gap moves into the gap. When {@code headed}, the first
   * line, under the page header, likewise holds only a heading. Places left empty are null.
   */
  static List<List<Cell>> leaves(List<Cell> cells, int seats, int gap, boolean headed) {
    Flow flow = new Flow(Math.max(1, seats / 2) + (gap < 0 ? 0 : 1), gap, headed);
    int index = 0;
    while (index < cells.size()) {
      Cell cell = cells.get(index);
      if (cell.heading() == null) {
        flow.item(cell);
        index++;
        continue;
      }
      int end = index + group(cells, index);
      List<Cell> items = cells.subList(index + 1, end);
      if (!flow.fits(flow.at, items.size()) && (flow.fits(0, items.size()) || !flow.fits(flow.at, Math.min(1, items.size())))) {
        flow.turn();
      }
      flow.group(cell, items);
      index = end;
    }
    return flow.leaves;
  }

  /** The leaves being filled, and the next free place of the last one. */
  private static final class Flow {
    private final int lines;
    private final int gap;
    private final boolean headed;
    private final List<List<Cell>> leaves = new ArrayList<>();
    private List<Cell> leaf;
    private int at;

    private Flow(int lines, int gap, boolean headed) {
      this.lines = lines;
      this.gap = gap;
      this.headed = headed;
      turn();
    }

    /** Starts a new leaf, unless the last one is still empty. */
    private void turn() {
      if (leaf != null && at == 0) {
        return;
      }
      leaf = new ArrayList<>(Collections.nCopies(lines * 2, (Cell) null));
      leaves.add(leaf);
      at = 0;
    }

    private boolean headingOnly(int line) {
      return line == gap || (headed && line == 0);
    }

    /** Where a heading goes from place {@code from} on: its own line, the free right place, or the gap; -1 for none. */
    private int headingPlace(int from) {
      int line = from / 2;
      if (gap >= 0 && line == gap - 1) {
        return gap * 2;
      }
      if (from % 2 == 1) {
        return line + 1 < lines ? from : -1;
      }
      return line < lines ? from : -1;
    }

    /** The next place an adaptation can take from {@code from} on, passing heading-only lines; -1 for none. */
    private int itemPlace(int from) {
      if (from % 2 == 1) {
        return from;
      }
      int line = from / 2;
      while (line < lines && headingOnly(line)) {
        line++;
      }
      return line < lines ? line * 2 : -1;
    }

    /** Whether a heading and {@code count} adaptations fit on this leaf from place {@code from}. */
    private boolean fits(int from, int count) {
      int place = headingPlace(from);
      if (place < 0) {
        return false;
      }
      place = (place / 2 + 1) * 2;
      for (int item = 0; item < count; item++) {
        place = itemPlace(place);
        if (place < 0) {
          return false;
        }
        place++;
      }
      return true;
    }

    private void group(Cell heading, List<Cell> items) {
      heading(heading);
      for (Cell item : items) {
        int place = itemPlace(at);
        if (place < 0) {
          turn();
          heading(heading);
          place = itemPlace(at);
        }
        leaf.set(place, item);
        at = place + 1;
      }
    }

    private void heading(Cell heading) {
      int place = headingPlace(at);
      if (place < 0) {
        turn();
        place = headingPlace(at);
      }
      leaf.set(place, heading);
      at = (place / 2 + 1) * 2;
    }

    private void item(Cell item) {
      int place = itemPlace(at);
      if (place < 0) {
        turn();
        place = itemPlace(at);
      }
      leaf.set(place, item);
      at = place + 1;
    }
  }

  /** The seats of the group headed at {@code start}: its heading and the adaptations up to the next heading. */
  private static int group(List<Cell> cells, int start) {
    int end = start + 1;
    while (end < cells.size() && cells.get(end).heading() == null) {
      end++;
    }
    return end - start;
  }

  /**
   * Draws one cell; returns an adaptation cell's name and where it goes for the list's names, null for a heading. A
   * heading with the place {@code beside} it free runs its rule on to the end of that place's rule.
   */
  private static Named cell(PackFrame frame, PageBuilder page, Card card, Card beside, SkillView skill, Cell cell) {
    if (cell.heading() != null) {
      Placement heading = page.name(card.text("heading"), PackLabels.coloured(cell.colour(), cell.heading()));
      BarSpec rule = card.bar("heading.rule");
      TextSpec spec = card.text("heading");
      BarSpec further = beside == null ? null : beside.bar("heading.rule");
      if (rule != null && further != null) {
        rule = new BarSpec(rule.x(), rule.y(), further.x() + further.width() - rule.x(), rule.height(), rule.colour());
      }
      if (rule != null && spec != null) {
        page.rule(rule.from(spec.x() + heading.width() + PackFrame.RULE_GAP));
      }
      return null;
    }
    AdaptationView adaptation = cell.adaptation();
    KeptView kept = kept(frame, adaptation.id());
    int level = Math.max(LearnRules.floor(kept), CatalogIndex.level(frame.player(), adaptation.id()));
    boolean learnable = level < adaptation.maxLevel()
        && LearnRules.learn(frame.catalog(), frame.player(), adaptation, level + 1, kept).allowed();
    Row row = Row.of(level, learnable);
    boolean maxed = level >= adaptation.maxLevel() && adaptation.maxLevel() > 0;
    int ink = PackInk.of(skill.color());
    BarSpec box = card.bar(PackArt.BOX);
    if (box != null) {
      box(page, box, row, level, adaptation.maxLevel(), ink);
    }
    boolean faded = row == Row.CLOSED;
    MenuIcon icon = PageBuilder.icon(adaptation.icon(), PackLabels.coloured(ink, adaptation.name()), detail(frame, adaptation));
    page.group(card.cells(), icon, (MenuClick click) -> click(frame, adaptation, click.kind()),
        page.resources().icon(adaptation.icon(), page.kit(), faded));
    return new Named(inBox(card.text("name"), box, maxed), PackLabels.coloured(faded ? PackLabels.FAINT : PackLabels.INK, adaptation.name()));
  }

  /**
   * A row's box, outlined in {@link #outline} of {@code ink}: a row not learned dotted, a learned one solid over the row
   * wash, washed in {@code ink} from the left by the learned share, and when maxed edged in {@code ink} at both ends,
   * clear of the name's rows, with the kit's ornament at its right end.
   */
  static void box(PageBuilder page, BarSpec box, Row row, int level, int max, int ink) {
    int outline = outline(ink);
    if (row != Row.LEARNED) {
      int at = box.x();
      for (int segment : PackArt.segments(box.width())) {
        page.paint(PackArt.dots(segment, box.y()), at, outline);
        at += segment;
      }
      page.paint(PackArt.dotSide(box.y()), box.x(), outline);
      page.paint(PackArt.dotSide(box.y()), box.x() + box.width() - 1, outline);
      return;
    }
    int at = box.x();
    for (int segment : PackArt.segments(box.width())) {
      page.paint(PackArt.box(segment, box.y()), at);
      at += segment;
    }
    int washed = max < 1 ? 0 : (int) Math.round((double) box.width() * Math.min(level, max) / max);
    boolean strong = strong(ink);
    at = box.x();
    for (int segment : PackArt.segments(washed)) {
      page.paint(PackArt.wash(segment, box.y(), strong), at, ink);
      at += segment;
    }
    for (BarSpec edge : PackArt.outline(box)) {
      page.rule(new BarSpec(edge.x(), edge.y(), edge.width(), edge.height(), outline));
    }
    if (max > 0 && level >= max) {
      for (BarSpec edge : PackArt.border(box)) {
        page.rule(new BarSpec(edge.x(), edge.y(), edge.width(), edge.height(), ink));
      }
      page.paint(PackArt.ornament(page.kit(), box.y()), box.x() + box.width() - PackArt.ORNAMENT_GAP);
    }
  }

  /** The faint line round every row of a page in {@code ink}: the ink {@link #OUTLINE_SHARE} of the way to the paper. */
  static int outline(int ink) {
    int paper = PackArt.PAPER & 0xFFFFFF;
    int mixed = 0;
    for (int shift = 0; shift <= 16; shift += 8) {
      int from = (ink >> shift) & 0xFF;
      int to = (paper >> shift) & 0xFF;
      mixed |= (from + Math.round((to - from) * OUTLINE_SHARE)) << shift;
    }
    return mixed;
  }

  /** Whether {@code ink} is pale or grey enough that its learned wash needs the stronger alpha to read on the row wash. */
  static boolean strong(int ink) {
    float[] hsb = Color.RGBtoHSB((ink >> 16) & 0xFF, (ink >> 8) & 0xFF, ink & 0xFF, null);
    return hsb[1] < STRONG_SATURATION || hsb[2] > STRONG_BRIGHTNESS;
  }

  /**
   * A row's name label kept {@link #BOX_PAD} inside its box, and on a maxed row clear of the ornament at the box's end,
   * so a longer name wraps or breaks by the label's rule instead of crossing the box's edge.
   */
  static TextSpec inBox(TextSpec name, BarSpec box, boolean maxed) {
    if (name == null || box == null) {
      return name;
    }
    int right = box.x() + box.width() - (maxed ? PackArt.ORNAMENT_GAP + PackArt.ORNAMENT_WIDTH + PackArt.ORNAMENT_GAP + 1 : BOX_PAD + 1);
    int room = right - name.x();
    if (room >= name.width()) {
      return name;
    }
    return new TextSpec(name.x(), name.y(), Math.max(1, room), name.align(), name.colour(), name.lines(), name.pitch(), name.top(),
        name.breaks());
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
        screens.refuse(viewer, PackLabels.refusal(frame.catalog(), check));
      }
    } else if (kind == ClickKind.SHIFT_RIGHT) {
      LearnRules.UnlearnCheck check = LearnRules.unlearn(frame.catalog(), frame.player(), adaptation, level - 1, kept);
      if (check.allowed()) {
        screens.unlearn(viewer, adaptation.id(), check.to());
      } else {
        screens.refuse(viewer, PackLabels.refusal(adaptation, check));
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

  /** How a row's box reads: learned, open to learning now, or closed by its requirements, its cost or the server. */
  enum Row {
    LEARNED,
    OPEN,
    CLOSED;

    static Row of(int level, boolean learnable) {
      return level > 0 ? LEARNED : learnable ? OPEN : CLOSED;
    }
  }

  /** An adaptation row's name and the label it is placed in. */
  record Named(TextSpec spec, String markup) {
  }

  /** One seat of the flow: a group heading in its colour, or an adaptation. */
  record Cell(String heading, int colour, AdaptationView adaptation) {
  }
}
