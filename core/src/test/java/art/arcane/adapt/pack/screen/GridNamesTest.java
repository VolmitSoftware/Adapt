package art.arcane.adapt.pack.screen;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.ShortNameView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.pack.FakeMenuHost;
import art.arcane.adapt.pack.FakePackWindows;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.PackSamples;
import art.arcane.adapt.pack.layout.BarSpec;
import art.arcane.adapt.pack.layout.Card;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.surface.PackSupport;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.pack.text.Placement;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The names of one grid share one font, and neighbouring Skills cards keep a gap between their names, for the built-in
 * skills in the order the page seats them.
 */
class GridNamesTest {
  /** The least paper between two names side by side. */
  private static final int NAME_GAP = 4;
  /** The least paper between the left list column and the right column's icons and headings. */
  private static final int COLUMN_GAP = 7;
  private static final String CONDENSED = "/condensed/";
  /** A card's height: two slot rows. */
  private static final int CARD_HEIGHT = 36;
  /** A text line's cell, descenders included. */
  private static final int LINE_CELL = 8;
  /** The least paper between a card's last name line and the next card row. */
  private static final int AIR_BELOW = 3;
  @TempDir
  Path data;

  private final FakePlayer player = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));
  private final FakePackWindows windows = new FakePackWindows();
  private final PackResources resources = PackSamples.resources();
  private final ScreenFixtures.Server server = new ScreenFixtures.Server();
  private final PackScreens screens = new PackScreens(new PackMenuHost(new FakeMenuHost(), windows, (PlayerRef ignored) -> true,
      () -> resources), server, server, new ScreenFixtures.Prompts(), new ScreenFixtures.Clock());
  private TestAdapt adapt;
  private List<String> names;
  /** Every adaptation name a skill page lists (the short name where there is one): the skills' own and every mutated skill's. */
  private Set<String> listNames;

  @BeforeEach
  void start() {
    server.screens = screens;
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    UUID viewer = player.uuid();
    CatalogView view = CatalogViews.build(registry, viewer, List.of(), true);
    server.catalog = new CatalogIndex(view, Map.of());
    List<String> skills = new ArrayList<>();
    List<SkillLineView> lines = new ArrayList<>();
    names = new ArrayList<>();
    listNames = new TreeSet<>();
    Map<String, String> shortNames = new HashMap<>();
    for (ShortNameView shortName : CatalogViews.shortNames(registry, viewer, List.of())) {
      shortNames.put(shortName.adaptationId(), shortName.name());
    }
    for (SkillView skill : view.skills()) {
      for (AdaptationView adaptation : skill.adaptations()) {
        listNames.add(LegacyText.plain(shortNames.getOrDefault(adaptation.id(), adaptation.name())));
      }
      skills.add(skill.id());
      lines.add(new SkillLineView(skill.id(), 5000D, 50, 0.5D, 900D, 120L));
      names.add(LegacyText.plain(skill.name()));
    }
    names.sort(Comparator.comparing((String name) -> name.toLowerCase(java.util.Locale.ROOT)));
    server.player = new PlayerView(new PlayerHeader(80, 0.5D, 40, 99, 1L, true), lines, Map.of(), List.of(),
        new PlayerAccess(false, true, true, skills, new HashMap<>()));
  }

  @AfterEach
  void stop() {
    AdaptPorts.uninstall(PackScreens.class);
    adapt.close();
  }

  @Test
  void neighbouringSkillsCardNamesKeepAGapOnEveryPageAndTier() {
    assertThat(names).hasSizeGreaterThanOrEqualTo(24);
    for (boolean lower : List.of(true, false)) {
      List<Card> cards = resources.layout().screen(PackSkillsScreen.SCREEN, lower).cards();
      for (int first = 0; first < names.size(); first += cards.size()) {
        List<String> page = names.subList(first, Math.min(names.size(), first + cards.size()));
        List<TextSpec> specs = new ArrayList<>();
        for (int seat = 0; seat < page.size(); seat++) {
          specs.add(cards.get(seat).text("name"));
        }
        PackFontMetrics font = resources.condensed(specs, page) ? resources.manifest().condensed().metrics() : resources.metrics();
        for (int seat = 0; seat + 1 < page.size(); seat++) {
          TextSpec left = specs.get(seat);
          TextSpec right = specs.get(seat + 1);
          if (left.y() != right.y()) {
            continue;
          }
          int end = left.x() + Math.min(left.width(), font.width(page.get(seat)));
          assertThat(right.x() - end).as(page.get(seat) + " beside " + page.get(seat + 1) + (lower ? "" : " (top rows)"))
              .isGreaterThanOrEqualTo(NAME_GAP);
        }
      }
    }
  }

  @Test
  void everySkillAndMutatedNameFitsASkillsCardUncutWithAirBelow() {
    List<String> all = new ArrayList<>(names);
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead != partner) {
          all.add(MutatedText.mutationName(MutatedText.ENGLISH, new MutatedText.Parents(lead, partner, registry.getPairRegistry())));
        }
      }
    }
    assertThat(all).hasSize(names.size() + 552);
    List<String> problems = new ArrayList<>();
    for (boolean lower : List.of(true, false)) {
      Card card = resources.layout().screen(PackSkillsScreen.SCREEN, lower).cards().get(0);
      TextSpec spec = card.text("name");
      for (String name : all) {
        boolean condensed = resources.condensed(List.of(spec), List.of(name));
        Placement placed = resources.name(resources.composer(), spec, name, condensed);
        int bottom = (spec.y() - card.top()) + ((placed.lines() - 1) * spec.pitch(LINE_CELL)) + LINE_CELL;
        if (placed.status() != Placement.Status.PLACED || CARD_HEIGHT - bottom < AIR_BELOW) {
          problems.add(name + (lower ? "" : " (top rows)") + ": " + placed);
        }
      }
    }
    assertThat(problems).isEmpty();
  }

  @Test
  void everyListNameKeepsClearOfItsCountUncut() {
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    int pairs = 0;
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        MutatedSkill mutated = lead == partner ? null : registry.getMutatedSkill(new SkillPair(lead.getId(), partner.getId()));
        if (mutated != null) {
          pairs++;
          for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
            listNames.add(LegacyText.plain(adaptation.getDisplayName()));
          }
        }
      }
    }
    for (Recipe recipe : Recipe.values()) {
      listNames.add(MutatedText.ENGLISH.text(MutatedMessages.recipe(recipe)));
    }
    assertThat(pairs).isEqualTo(552);
    assertThat(listNames).contains("Attunement", "Blood Trail");
    List<String> problems = new ArrayList<>();
    int checked = 0;
    for (boolean lower : List.of(true, false)) {
      for (Card card : resources.layout().screen(PackSkillScreen.SCREEN, lower).cards()) {
        TextSpec name = card.text("name");
        TextSpec count = card.text("level");
        for (String text : listNames) {
          for (String level : List.of("0/3", "10/10")) {
            int countWidth = resources.metrics().width(level);
            int countLeft = switch (count.align()) {
              case LEFT -> count.x();
              case CENTRE -> count.x() + ((count.width() - countWidth) / 2);
              case RIGHT -> count.x() + count.width() - countWidth;
            };
            TextSpec spec = PackSkillScreen.besideCount(name, count, countWidth);
            boolean condensed = resources.condensed(List.of(spec), List.of(text));
            Placement placed = resources.name(resources.composer(), spec, text, condensed);
            int inkEnd = spec.x() + placed.width() - 1;
            String where = text + " beside " + level + (lower ? "" : " (top rows)") + " at x " + name.x();
            if (placed.status() != Placement.Status.PLACED) {
              problems.add(where + ": " + placed);
            } else if (countLeft > name.x() && countLeft - inkEnd < PackSkillScreen.COUNT_GAP) {
              problems.add(where + ": " + (countLeft - inkEnd) + " px");
            }
            checked++;
          }
        }
      }
    }
    assertThat(checked).isGreaterThan(1000);
    assertThat(problems).isEmpty();
  }


  @Test
  void skillPageLeftColumnStopsShortOfTheRightColumn() {
    for (boolean lower : List.of(true, false)) {
      List<Card> cards = resources.layout().screen(PackSkillScreen.SCREEN, lower).cards();
      for (Card left : cards) {
        for (Card right : cards) {
          TextSpec name = left.text("name");
          TextSpec heading = right.text("heading");
          if (name.y() != right.text("name").y() || heading.x() <= name.x()) {
            continue;
          }
          BarSpec rule = left.bar("heading.rule");
          String where = (lower ? "player rows" : "top rows") + " y " + name.y();
          assertThat(heading.x() - (name.x() + name.width())).as(where).isGreaterThanOrEqualTo(COLUMN_GAP);
          assertThat(heading.x() - (rule.x() + rule.width())).as(where).isGreaterThanOrEqualTo(COLUMN_GAP);
        }
      }
    }
  }

  @Test
  void everySkillsPageDrawsItsNamesInOneFont() {
    for (PackSupport support : List.of(PackSupport.FULL, PackSupport.NONE)) {
      windows.support = support;
      for (int page = 0; page < 3; page++) {
        screens.openSkills(player);
        screens.page(player, page);
        Set<Boolean> condensed = new LinkedHashSet<>();
        for (TitleRun run : windows.window(player).surface.title()) {
          if (names.contains(run.text().strip())) {
            condensed.add(run.font().contains(CONDENSED));
          }
        }
        assertThat(condensed).as("Skills page " + page + " " + support).hasSize(1);
      }
    }
  }

  @Test
  void aNameTooWideForItsCardDrawsTheWholeGridCondensed() {
    ScreenLayout skills = resources.layout().screen(PackSkillsScreen.SCREEN, true);
    List<TextSpec> specs = List.of(skills.cards().get(0).text("name"), skills.cards().get(1).text("name"));
    assertThat(resources.condensed(specs, List.of("Agility", "Axes"))).isFalse();
    assertThat(resources.condensed(specs, List.of("Agility", "Enchanting"))).isTrue();
  }
}
