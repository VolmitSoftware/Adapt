package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.pack.layout.Card;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackInk;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Skills for pack viewers, seated as the native client seats them: one card for every enabled skill the player may open
 * or is shown, in name order, a mutation in its lead's seat and its partner left out. A skill the player has not started
 * shows greyed, or is left out when the server hides inactive skills. The search field and the knowledge and power totals
 * head the page, the cards follow, the pager takes the foot. Each card is a 3 by 2 block of slots with "Lv" and the XP
 * bar beside the icon and the name under it on up to two lines, the names of a page in one font; knowledge and the XP
 * guide are in the tooltip. The search field filters by name through a prompt (right click clears it).
 */
final class PackSkillsScreen {
  static final String SCREEN = "skills";
  static final String KIT = PackArt.TOME;

  private PackSkillsScreen() {
  }

  static PackPage render(PackFrame frame) {
    CatalogIndex catalog = frame.catalog();
    PlayerView player = frame.player();
    PackViewer state = frame.state();
    PlayerRef viewer = frame.viewer();
    PackScreens screens = frame.screens();
    List<Seat> skills = matching(seats(frame), state.search);
    PageBuilder page = frame.page(SCREEN, KIT, 0, List.of(AdaptLanguage.text(GuiMessages.SKILLS)));
    List<Card> seats = page.layout().cards();
    int pages = Math.max(1, (skills.size() + seats.size() - 1) / Math.max(1, seats.size()));
    int current = Math.min(state.page, pages - 1);
    List<TextSpec> nameSpecs = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (int seat = 0; seat < seats.size(); seat++) {
      int index = (current * seats.size()) + seat;
      if (index >= skills.size()) {
        break;
      }
      Seat shown = skills.get(index);
      seat(frame, page, seats.get(seat), shown.skill(), shown.started());
      nameSpecs.add(seats.get(seat).text("name"));
      names.add(PackLabels.coloured(shown.started() ? PackInk.of(shown.skill().color()) : PackLabels.MUTED, shown.skill().name()));
    }
    page.names(nameSpecs, names);
    String search = state.search.isEmpty() ? AdaptLanguage.text(GuiMessages.PACK_SEARCH) : state.search;
    page.text("search", search);
    page.button("search", PackArt.FIELD, PageBuilder.named(AdaptLanguage.text(GuiMessages.PACK_SEARCH),
        state.search.isEmpty() ? List.of() : List.of(PackLabels.GREY + state.search)), (MenuClick click) -> {
      if (click.kind() == ClickKind.RIGHT && !state.search.isEmpty()) {
        state.search = "";
        screens.page(viewer, 0);
      } else {
        screens.search(viewer);
      }
    }, false, null);
    long knowledge = 0L;
    for (Seat seat : seats(frame)) {
      SkillLineView line = CatalogIndex.line(player, seat.skill().id());
      knowledge += line == null ? 0L : Math.max(0L, line.knowledge());
    }
    frame.stats(page, knowledge);
    if (skills.isEmpty()) {
      page.text("empty", AdaptLanguage.text(state.search.isEmpty() ? GuiMessages.NO_SKILLS_AVAILABLE : GuiMessages.PACK_NO_MATCHES));
    }
    page.foot(null, current, pages, (int target) -> screens.page(viewer, target), List.of());
    return page.build();
  }

  private static void seat(PackFrame frame, PageBuilder page, Card card, SkillView skill, boolean on) {
    PackScreens screens = frame.screens();
    PlayerRef viewer = frame.viewer();
    SkillLineView line = CatalogIndex.line(frame.player(), skill.id());
    int level = line == null ? 0 : line.level();
    MenuIcon icon = PageBuilder.icon(skill.icon(), PackLabels.coloured(PackInk.of(skill.color()), skill.name()), lore(frame, skill, line));
    page.group(card.cells(), icon, (MenuClick click) -> screens.route(viewer, new Route.Skill(skill.id())),
        page.resources().icon(skill.icon(), KIT, !on));
    page.text(card.text("level"), PackLabels.coloured(on ? PackLabels.INK : PackLabels.FAINT,
        AdaptLanguage.text(GuiMessages.PACK_LV, trusted("level", level))));
    page.bar(card.bar(PackArt.XP), on && line != null ? line.progress() : 0D, PackInk.of(skill.color()));
  }

  private static List<Seat> matching(List<Seat> seats, String search) {
    if (search.isEmpty()) {
      return seats;
    }
    String needle = search.toLowerCase(Locale.ROOT);
    List<Seat> matched = new ArrayList<>();
    for (Seat seat : seats) {
      if (LegacyText.plain(seat.skill().name()).toLowerCase(Locale.ROOT).contains(needle)) {
        matched.add(seat);
      }
    }
    return matched;
  }

  /**
   * The seats in order: every enabled skill the player may open or is shown, by name, a mutation shown in its lead's
   * seat and its partner left out; skills not started are left out when the server hides inactive skills.
   */
  static List<Seat> seats(PackFrame frame) {
    PlayerView player = frame.player();
    List<SkillView> roster = new ArrayList<>();
    Map<String, SkillView> leading = new HashMap<>();
    Set<String> partners = new HashSet<>();
    for (SkillView skill : frame.catalog().catalog().skills()) {
      XpGuideView guide = frame.screens().views().guide(frame.viewer(), skill.id());
      if (guide != null && guide.mutated()) {
        if (guide.parents().size() == 2) {
          leading.put(guide.parents().get(0), skill);
          partners.add(guide.parents().get(1));
        }
      } else if (skill.enabled() && (player.access().canOpen(skill.id()) || player.access().skills().contains(skill.id()))) {
        roster.add(skill);
      }
    }
    roster.sort(Comparator.comparing((SkillView skill) -> LegacyText.plain(skill.name()).toLowerCase(Locale.ROOT))
        .thenComparing(SkillView::id));
    boolean inactive = frame.screens().views().inactiveSkills(frame.viewer());
    List<Seat> seats = new ArrayList<>();
    for (SkillView skill : roster) {
      if (partners.contains(skill.id())) {
        continue;
      }
      SkillView shown = leading.getOrDefault(skill.id(), skill);
      boolean started = shown != skill || started(player, shown);
      if (started || inactive) {
        seats.add(new Seat(shown, started));
      }
    }
    return seats;
  }

  /** Whether the player has started {@code skill}: earned experience or knowledge in it, or learned something there. */
  static boolean started(PlayerView player, SkillView skill) {
    SkillLineView line = CatalogIndex.line(player, skill.id());
    return line != null && (line.xp() > 0D || line.knowledge() > 0L) || CatalogIndex.powerUsed(player, skill) > 0;
  }

  /** One seat: the skill it shows and whether the player has started it. */
  record Seat(SkillView skill, boolean started) {
  }

  /** The seat tooltip: level, XP to the next level, knowledge and the XP guide (each parent's titled on a mutated skill). */
  static List<String> lore(PackFrame frame, SkillView skill, SkillLineView line) {
    List<String> lore = new ArrayList<>();
    int level = line == null ? 0 : line.level();
    lore.add(PackLabels.GREY + AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", level)));
    if (line != null) {
      lore.add(PackLabels.GREY + AdaptLanguage.text(GuiMessages.PACK_XP, trusted("xp", (long) Math.ceil(line.xpToNext())),
          trusted("level", level + 1)));
      lore.add(PackLabels.BLUE_TEXT + AdaptLanguage.text(GuiMessages.PACK_KNOWLEDGE, trusted("knowledge", line.knowledge())));
    }
    XpGuideView guide = frame.screens().views().guide(frame.viewer(), skill.id());
    if (guide != null) {
      for (XpGuideView.Section section : guide.sections()) {
        if (guide.sections().size() > 1) {
          lore.add(PackLabels.GREY + section.title());
        }
        for (String text : section.lines()) {
          lore.add(PackLabels.GREY + text);
        }
      }
    }
    return lore;
  }
}
