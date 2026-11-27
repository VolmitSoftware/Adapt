package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.MutationCandidate;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationWords;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.pack.layout.Card;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.text.PackInk;
import art.arcane.adapt.pack.text.Placement;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuAction;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * The Mutate flow for pack viewers. A lead is picked from named cards (when opened without one), then a partner from
 * the candidates as named cards, in the lead's kit; then the spread: lead on the left, partner on the right, swap
 * between them, what each brings and loses under them, the mutated skill's name and its own adaptations across the
 * middle, a tab back to the partner cards, and the seal at the foot. The seal arms on the first click, fills from its
 * outer ends toward the mark, and mutates on a second click inside {@link PackScreens#SEAL_WINDOW_MS}.
 */
final class PackMutateScreen {
  static final String SCREEN = "mutate";
  /** The page of named cards a lead or a partner is picked from. */
  static final String PICK = "mutate.partners";
  static final Id SEAL_COOLDOWN = Id.adapt("seal");
  static final String SEAL = "seal";
  /** The foot tab that shows the partner candidates on a page without the player slots. */
  static final String PARTNER_TAB = "list";
  static final String GAIN_SIGN = "+";
  static final String LOSS_SIGN = "-";

  private PackMutateScreen() {
  }

  static PackPage render(PackFrame frame) {
    PackViewer state = frame.state();
    MutationSpreadView spread = state.spread;
    if (spread == null) {
      return state.leads.isEmpty() ? null : leads(frame);
    }
    SkillView lead = frame.catalog().skill(spread.lead());
    if (lead == null) {
      return null;
    }
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    MutationWords words = spread.words();
    SkillView partner = spread.partner().isEmpty() ? null : frame.catalog().skill(spread.partner());
    List<String> crumbs = List.of(AdaptLanguage.text(GuiMessages.SKILLS), lead.name(), words.title());
    if (state.partners || partner == null) {
      return partners(frame, spread, lead, partner, crumbs);
    }
    PageBuilder page = frame.page(SCREEN, lead.id(), 0, crumbs);
    side(page, "lead", lead, spread.leadSide(), words.lead(), words);
    side(page, "partner", partner, spread.partnerSide(), words.partner(), words);
    page.put(page.layout().slot("swap"), PageBuilder.named(words.swap()),
        (MenuClick click) -> screens.partner(viewer, spread.partner(), spread.lead()),
        page.resources().sprite(PackArt.SWAP_SPRITE, page.kit(), false));
    medals(page, page.layout().slots("gains.lead"), brings(spread.leadSide()), PackInk.of(lead.color()), false);
    medals(page, page.layout().slots("loses.lead"), spread.leadSide().lost(), PackInk.of(lead.color()), true);
    medals(page, page.layout().slots("gains.partner"), brings(spread.partnerSide()), PackInk.of(partner.color()), false);
    medals(page, page.layout().slots("loses.partner"), spread.partnerSide().lost(), PackInk.of(partner.color()), true);
    boolean gains = !brings(spread.leadSide()).isEmpty() || !brings(spread.partnerSide()).isEmpty();
    boolean losses = !spread.leadSide().lost().isEmpty() || !spread.partnerSide().lost().isEmpty();
    page.text("gains.sign", gains ? GAIN_SIGN : null);
    page.text("loses.sign", losses ? LOSS_SIGN : null);
    page.text("result", spread.name());
    page.text("level", spread.summary());
    page.rule("result.rule", Integer.MIN_VALUE);
    medals(page, page.layout().slots("own"), spread.mutated(), PackLabels.GOLD_INK, false);
    page.control(page.layout().slot("tab.partner"), PageBuilder.named(words.partner()), (MenuClick click) -> screens.partners(viewer, true),
        PackArt.tab(PARTNER_TAB, false));
    seal(frame, page, words, PackInk.of(lead.color()), PackInk.of(partner.color()));
    page.foot((MenuClick click) -> screens.route(viewer, new Route.Skill(spread.lead())), 0, 1, (int ignored) -> { }, List.of());
    return page.build();
  }

  /**
   * The partner pick: every candidate as a named card with its level, greyed with the reason in its tooltip when it
   * cannot join, the chosen one marked; a click on an eligible one shows the spread with it. Back returns to the spread
   * once a partner is chosen, else to the lead's page.
   */
  private static PackPage partners(PackFrame frame, MutationSpreadView spread, SkillView lead, SkillView partner, List<String> crumbs) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    MutationWords words = spread.words();
    List<Pick> picks = new ArrayList<>();
    for (MutationCandidate candidate : spread.candidates()) {
      SkillView skill = frame.catalog().skill(candidate.skillId());
      if (skill == null) {
        continue;
      }
      List<String> lore = new ArrayList<>();
      lore.add(PackLabels.GREY + words.level(candidate.level()));
      if (!candidate.reason().isEmpty()) {
        lore.add(PackLabels.RED + candidate.reason());
      }
      MenuAction pick = candidate.eligible() ? (MenuClick click) -> screens.partner(viewer, spread.lead(), candidate.skillId()) : null;
      picks.add(new Pick(skill, candidate.level(), candidate.eligible(), candidate.skillId().equals(spread.partner()), lore, pick));
    }
    MenuAction back = partner != null ? (MenuClick click) -> screens.partners(viewer, false)
        : (MenuClick click) -> screens.route(viewer, new Route.Skill(spread.lead()));
    return picker(frame, lead.id(), crumbs, AdaptLanguage.text(MutationMessages.PICK_PARTNER), picks, back);
  }

  /** The lead pick: every skill the viewer may lead a mutation with, as a named card; a click opens its spread. */
  private static PackPage leads(PackFrame frame) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    List<Pick> picks = new ArrayList<>();
    for (String id : frame.state().leads) {
      SkillView skill = frame.catalog().skill(id);
      if (skill == null) {
        continue;
      }
      SkillLineView line = CatalogIndex.line(frame.player(), id);
      int level = line == null ? 0 : line.level();
      List<String> lore = List.of(PackLabels.GREY + AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", level)));
      picks.add(new Pick(skill, level, true, false, lore, (MenuClick click) -> screens.openMutate(viewer, id)));
    }
    String title = AdaptLanguage.text(MutationMessages.MUTATE);
    return picker(frame, PackArt.TOME, List.of(AdaptLanguage.text(GuiMessages.SKILLS), title), AdaptLanguage.text(MutationMessages.PICK_LEAD),
        picks, (MenuClick click) -> screens.route(viewer, new Route.Skills()));
  }

  /**
   * A page of skill cards to pick from, each its icon with the level beside it and the name under it, the names of a
   * page in one font, paged, under {@code heading}.
   */
  private static PackPage picker(PackFrame frame, String kit, List<String> crumbs, String heading, List<Pick> picks, MenuAction back) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    PageBuilder page = frame.page(PICK, kit, 0, crumbs);
    List<Card> cards = page.layout().cards();
    int perPage = Math.max(1, cards.size());
    int pages = Math.max(1, (picks.size() + perPage - 1) / perPage);
    int current = Math.min(frame.state().page, pages - 1);
    List<TextSpec> nameSpecs = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (int seat = 0; seat < cards.size(); seat++) {
      int index = (current * perPage) + seat;
      if (index >= picks.size()) {
        break;
      }
      Pick pick = picks.get(index);
      Card card = cards.get(seat);
      SkillView skill = pick.skill();
      if (pick.selected()) {
        page.face(PackArt.TILE_SELECTED, card.icon(), card.shift());
      }
      page.group(card.cells(), PageBuilder.icon(skill.icon(), PackLabels.coloured(PackInk.of(skill.color()), skill.name()), pick.lore()),
          pick.action(), page.resources().icon(skill.icon(), page.kit(), !pick.eligible(), card.shift()));
      page.text(card.text("level"), PackLabels.coloured(pick.eligible() ? PackLabels.INK : PackLabels.FAINT,
          AdaptLanguage.text(GuiMessages.PACK_LV, trusted("level", pick.level()))));
      nameSpecs.add(card.text("name"));
      names.add(PackLabels.coloured(pick.eligible() ? PackInk.of(skill.color()) : PackLabels.MUTED, skill.name()));
    }
    page.names(nameSpecs, names);
    TextSpec spec = page.layout().text("heading");
    Placement placed = page.text(spec, heading);
    page.rule("heading.rule", spec == null ? Integer.MIN_VALUE : spec.x() + placed.width() + PackFrame.RULE_GAP);
    page.foot(back, current, pages, (int target) -> screens.page(viewer, target), List.of());
    return page.build();
  }

  /** One card of a pick page. */
  private record Pick(SkillView skill, int level, boolean eligible, boolean selected, List<String> lore, MenuAction action) {
  }

  private static void seal(PackFrame frame, PageBuilder page, MutationWords words, int leadInk, int partnerInk) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    boolean armed = frame.state().armed(SEAL, screens.now());
    int fill = screens.sealFill(frame.state());
    List<String> lore = List.of(PackLabels.RED + words.permanent());
    String label = armed ? AdaptLanguage.text(GuiMessages.PACK_CONFIRM_MUTATE) : words.title();
    PackSlot mark = page.layout().slot("seal.mark");
    List<PackSlot> segments = page.layout().slots("seal");
    int markAt = segments.indexOf(mark);
    for (int index = 0; index < segments.size(); index++) {
      PackSlot slot = segments.get(index);
      String part = index == 0 ? "l" : index == segments.size() - 1 ? "r" : "m";
      SlotLook look;
      if (slot.equals(mark)) {
        look = page.resources().animated(armed ? PackArt.SEAL_ARMED : PackArt.SEAL_IDLE, page.kit());
      } else {
        int depth = index < markAt ? index : segments.size() - 1 - index;
        boolean filled = depth < fill;
        look = page.resources().item(PackArt.seal(filled, part), page.kit());
        if (filled) {
          look = look.withModelData(List.of(), List.of(), List.of(index < markAt ? leadInk : partnerInk));
        }
      }
      page.put(slot, PageBuilder.named(label, lore), (MenuClick click) -> screens.seal(viewer, mark), look.withCooldownGroup(SEAL_COOLDOWN));
    }
    page.text("seal.label", label);
    page.text("seal.warning", words.permanent());
  }

  private static void side(PageBuilder page, String role, SkillView skill, MutationSide side, String title, MutationWords words) {
    List<String> lore = List.of(PackLabels.GREY + title + ", " + words.level(side.level()));
    page.put(page.layout().slot(role), PageBuilder.icon(skill.icon(), PackLabels.coloured(PackInk.of(skill.color()), skill.name()), lore), null, page.resources().icon(skill.icon(), page.kit(), false));
    page.name(role + ".name", PackLabels.coloured(PackInk.of(skill.color()), skill.name()));
    page.text(role + ".role", title);
  }

  /** What a parent brings to the mutation: its upgrades, kept originals and tools. */
  static List<MutationEntry> brings(MutationSide side) {
    List<MutationEntry> brings = new ArrayList<>(side.upgrades());
    brings.addAll(side.kept());
    brings.addAll(side.tools());
    return brings;
  }

  /** Entries one per slot; when they outnumber the slots, the last slot folds the rest into a "more" item. */
  private static void medals(PageBuilder page, List<PackSlot> seats, List<MutationEntry> entries, int ink, boolean lost) {
    int shown = entries.size() > seats.size() ? seats.size() - 1 : entries.size();
    for (int seat = 0; seat < shown; seat++) {
      MutationEntry entry = entries.get(seat);
      List<String> lore = new ArrayList<>();
      lore.add((lost ? PackLabels.RED : PackLabels.GREEN) + AdaptLanguage.text(lost ? GuiMessages.PACK_LOST : GuiMessages.PACK_GAINED));
      for (String line : entry.detail().split("\n")) {
        if (!line.isEmpty()) {
          lore.add(PackLabels.GREY + line);
        }
      }
      page.put(seats.get(seat), PageBuilder.icon(entry.icon(), PackLabels.coloured(ink, entry.name()), lore), null, page.resources().icon(entry.icon(), page.kit(), lost));
    }
    if (shown < entries.size() && !seats.isEmpty()) {
      List<String> names = new ArrayList<>();
      for (MutationEntry entry : entries.subList(shown, entries.size())) {
        names.add(PackLabels.GREY + entry.name());
      }
      MenuIcon more = PageBuilder.named("+" + names.size(), names).withAmount(Math.min(99, names.size()));
      page.put(seats.get(seats.size() - 1), more, null, page.resources().item(PackArt.MORE, page.kit()));
    }
  }
}
