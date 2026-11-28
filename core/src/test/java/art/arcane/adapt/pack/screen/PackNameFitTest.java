package art.arcane.adapt.pack.screen;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.client.server.ScreenActions;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.ShortNameView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.pack.FakeMenuHost;
import art.arcane.adapt.pack.FakePackWindows;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.PackSamples;
import art.arcane.adapt.pack.layout.Card;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.surface.PackSupport;
import art.arcane.adapt.pack.surface.TitleRun;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No built-in text is cut where the pack draws it, with the pack's condensed font: every page of Skills, every leaf of
 * every skill page and every tab of every adaptation page, laid out over the player slots and on the top rows only,
 * carries no two-dot cut; the Mutate tokens and pick cards fit every skill name.
 */
class PackNameFitTest {
  private static final String CUT = "..";
  private static final int PAGES = 6;
  @TempDir
  Path data;

  private final FakePlayer player = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));
  private final FakePackWindows windows = new FakePackWindows();
  private final PackResources resources = PackSamples.resources();
  private final ScreenFixtures.Server server = new ScreenFixtures.Server();
  private final PackScreens screens = new PackScreens(new PackMenuHost(new FakeMenuHost(), windows, (PlayerRef ignored) -> true,
      () -> resources), server, server, new ScreenFixtures.Prompts(), new ScreenFixtures.Clock(),
      new ScreenFixtures.Speaker());
  private TestAdapt adapt;
  private CatalogIndex catalog;

  @BeforeEach
  void start() {
    server.screens = screens;
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    UUID viewer = player.uuid();
    CatalogView view = CatalogViews.build(registry, viewer, List.of(), true);
    Map<String, String> shortNames = new HashMap<>();
    for (ShortNameView shortName : CatalogViews.shortNames(registry, viewer, List.of())) {
      shortNames.put(shortName.adaptationId(), shortName.name());
    }
    catalog = new CatalogIndex(view, shortNames);
    List<String> skills = new ArrayList<>();
    List<SkillLineView> lines = new ArrayList<>();
    Map<String, List<List<String>>> rows = new HashMap<>();
    for (SkillView skill : view.skills()) {
      skills.add(skill.id());
      lines.add(new SkillLineView(skill.id(), 5000D, 50, 0.5D, 900D, 120L));
      List<List<String>> skillRows = new ArrayList<>(skill.rowGroups());
      if (skillRows.isEmpty()) {
        skillRows.add(skill.adaptations().stream().map(AdaptationView::id).toList());
      }
      rows.put(skill.id(), skillRows);
    }
    Map<String, List<String>> labels = new HashMap<>();
    for (GroupLabelView group : ScreenActions.groupLabels(registry)) {
      labels.put(group.skillId(), group.labels());
    }
    server.catalog = catalog;
    server.labels = labels;
    server.player = new PlayerView(new PlayerHeader(80, 0.5D, 40, 99, 1L, true), lines, Map.of(), List.of(),
        new PlayerAccess(false, true, true, skills, rows));
  }

  @AfterEach
  void stop() {
    AdaptPorts.uninstall(PackScreens.class);
    adapt.close();
  }

  @Test
  void noBuiltInTextIsCutOnAnyPage() {
    assertThat(catalog.catalog().skills()).hasSizeGreaterThanOrEqualTo(24);
    Set<String> cuts = new LinkedHashSet<>();
    for (PackSupport support : List.of(PackSupport.FULL, PackSupport.NONE)) {
      windows.support = support;
      String tier = support.lowerSlots() ? "" : " (top rows)";
      for (int page = 0; page < PAGES; page++) {
        screens.openSkills(player);
        screens.page(player, page);
        cuts(cuts, "Skills" + tier);
      }
      for (SkillView skill : catalog.catalog().skills()) {
        for (int leaf = 0; leaf < PAGES; leaf++) {
          screens.openSkill(player, skill.id());
          screens.page(player, leaf);
          cuts(cuts, skill.id());
        }
        for (AdaptationView adaptation : skill.adaptations()) {
          for (int tab = 0; tab < 3; tab++) {
            screens.openAdaptation(player, adaptation.id());
            screens.tab(player, tab);
            cuts(cuts, adaptation.id() + " tab " + tab + tier);
          }
        }
      }
    }
    assertThat(cuts).isEmpty();
  }

  @Test
  void everySkillNameFitsTheMutateTokensAndPickCards() {
    List<String> cut = new ArrayList<>();
    for (boolean lower : List.of(true, false)) {
      ScreenLayout spread = resources.layout().screen(PackMutateScreen.SCREEN, lower);
      ScreenLayout pick = resources.layout().screen(PackMutateScreen.PICK, lower);
      for (SkillView skill : catalog.catalog().skills()) {
        for (TextSpec spec : List.of(spread.text("lead.name"), spread.text("partner.name"), pick.cards().get(0).text("name"))) {
          Placement placed = resources.name(resources.composer(), spec, skill.name());
          if (placed.status() == Placement.Status.TRUNCATED) {
            cut.add(skill.name() + " in " + spec);
          }
        }
      }
    }
    assertThat(cut).isEmpty();
  }

  @Test
  void everyMutatedNameFitsTheSkillPageIdentityInTheDefaultFont() {
    List<String> wide = new ArrayList<>();
    for (boolean lower : List.of(true, false)) {
      TextSpec spec = resources.layout().screen(PackSkillScreen.SCREEN, lower).text("name");
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      for (Skill<?> lead : registry.getSkills()) {
        for (Skill<?> partner : registry.getSkills()) {
          if (lead == partner) {
            continue;
          }
          String name = MutatedText.mutationName(MutatedText.ENGLISH, new MutatedText.Parents(lead, partner, registry.getPairRegistry()));
          if (!resources.fitsDefaultFont(spec, name)) {
            wide.add(name);
          }
        }
      }
    }
    assertThat(wide).isEmpty();
  }

  @Test
  void everyMutatedAdaptationNameFitsTheSkillPageListInTheDefaultFont() {
    List<String> wide = new ArrayList<>();
    for (boolean lower : List.of(true, false)) {
      for (Card card : resources.layout().screen(PackSkillScreen.SCREEN, lower).cards()) {
        for (Recipe recipe : Recipe.values()) {
          String name = MutatedText.ENGLISH.text(MutatedMessages.recipe(recipe));
          if (!resources.fitsDefaultFont(card.text("name"), name)) {
            wide.add(name + " at " + card.text("name").width());
          }
        }
      }
    }
    assertThat(wide).isEmpty();
  }

  /** Adds the cut labels of the open page, named after {@code where}. */
  private void cuts(Set<String> cuts, String where) {
    FakePackWindows.Window window = windows.window(player);
    if (window == null) {
      return;
    }
    for (TitleRun run : window.surface.title()) {
      if (run.text().contains(CUT)) {
        cuts.add(where + ": " + run.text().strip());
      }
    }
  }
}
