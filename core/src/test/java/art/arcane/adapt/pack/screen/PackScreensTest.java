package art.arcane.adapt.pack.screen;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.server.AdaptScreens;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.pack.FakeMenuHost;
import art.arcane.adapt.pack.FakePackWindows;
import art.arcane.adapt.pack.Goldens;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.PackSamples;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.link.PackAudience;
import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSupport;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.pack.text.TitleComposer;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.pack.text.Placement;
import art.arcane.adapt.pack.text.Align;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PackScreensTest {
  private final FakePlayer player = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));
  private final FakeMenuHost delegate = new FakeMenuHost();
  private final FakePackWindows windows = new FakePackWindows();
  private final PackResources resources = PackSamples.resources();
  private final Set<PlayerRef> book = new HashSet<>(Set.of(player));
  private final PackMenuHost host = new PackMenuHost(delegate, windows, book::contains, () -> resources);
  private final ScreenFixtures.Server server = new ScreenFixtures.Server();
  private final ScreenFixtures.Clock clock = new ScreenFixtures.Clock();
  private final ScreenFixtures.Prompts prompts = new ScreenFixtures.Prompts();
  private final PackScreens screens = new PackScreens(host, server, server, prompts, clock);

  PackScreensTest() {
    server.screens = screens;
  }

  @AfterEach
  void uninstall() {
    AdaptPorts.uninstall(PackScreens.class);
  }

  private FakePackWindows.Window window() {
    return windows.window(player);
  }

  private ScreenLayout layout(String screen) {
    return resources.layout().screen(screen, windows.support.lowerSlots());
  }


  private String dump() {
    return PackSamples.dump(window().menu, window().surface);
  }

  private void click(String screen, String role, ClickKind kind) {
    assertThat(window().click(layout(screen).slot(role), kind)).as(role).isTrue();
  }

  private void clickCard(String screen, int card, ClickKind kind) {
    assertThat(window().click(layout(screen).cards().get(card).icon(), kind)).as("card " + card).isTrue();
  }

  /** Clicks card {@code card} of the Mutate pick page. */
  private void pick(int card) {
    window().click(layout(PackMutateScreen.PICK).cards().get(card).icon(), ClickKind.LEFT);
  }

  private MenuButton button(String screen, String role) {
    return window().button(layout(screen).slot(role));
  }

  @Test
  void skillsGolden() {
    assertThat(screens.openSkills(player)).isTrue();
    Goldens.check("skills", dump());
  }

  @Test
  void skillsTopRowsOnlyGolden() {
    windows.support = PackSupport.NONE;
    assertThat(screens.openSkills(player)).isTrue();
    assertThat(window().surface.lower()).isEmpty();
    Goldens.check("skills-top", dump());
  }

  @Test
  void viewersWithoutThePackGetNoPackScreen() {
    book.clear();
    assertThat(screens.openSkills(player)).isFalse();
    assertThat(screens.openSkill(player, "adapt:agility")).isFalse();
    assertThat(windows.opened).isEmpty();
  }

  @Test
  void aSeatOpensItsSkillInTheSameWindow() {
    screens.openSkills(player);

    clickCard("skills", 0, ClickKind.LEFT);

    assertThat(server.routes).containsExactly(new Route.Skill("adapt:agility"));
    assertThat(windows.opened).hasSize(1);
    assertThat(window().updates).isEqualTo(1);
    assertThat(window().menu.title()).isEqualTo("Agility");
  }

  @Test
  void everySlotOfASeatClicksLikeItsIcon() {
    screens.openSkills(player);
    PackSlot cell = layout("skills").cards().get(1).cells().get(5);

    window().click(cell, ClickKind.RIGHT);

    assertThat(server.routes).containsExactly(new Route.Skill("adapt:rift"));
  }

  @Test
  void searchFiltersSkillsAndRightClickClearsIt() {
    screens.openSkills(player);
    prompts.answer = "rif";

    click("skills", "search", ClickKind.LEFT);

    assertThat(prompts.asked).hasSize(1);
    assertThat(window().button(layout("skills").cards().get(0).icon()).icon().name()).contains("Rift");
    assertThat(window().button(layout("skills").cards().get(1).icon())).isNull();

    click("skills", "search", ClickKind.RIGHT);

    assertThat(window().button(layout("skills").cards().get(1).icon()).icon().name()).contains("Rift");
  }

  @Test
  void chipsOpenTheSpendingListInTheLedger() {
    screens.openSkills(player);

    click("skills", "chip.knowledge", ClickKind.LEFT);

    assertThat(windows.opened).hasSize(2);
    assertThat(window().menu.button(0).icon().name()).isEqualTo("Super Jump");
    window().click(0, ClickKind.LEFT);
    assertThat(server.routes).containsExactly(new Route.Adaptation("adapt:agility/super_jump"));
  }

  @Test
  void skillGolden() {
    assertThat(screens.openSkill(player, "adapt:agility")).isTrue();
    Goldens.check("skill", dump());
  }

  @Test
  void skillTopRowsOnlyGolden() {
    windows.support = PackSupport.NONE;
    server.unmutateOffered = true;
    assertThat(screens.openSkill(player, "adapt:agility")).isTrue();
    Goldens.check("skill-top", dump());
  }

  @Test
  void skillsTheViewerMayNotOpenStayClosed() {
    assertThat(screens.openSkill(player, "unknown")).isFalse();
    assertThat(screens.openAdaptation(player, "unknown")).isFalse();
    assertThat(windows.opened).isEmpty();
  }

  @Test
  void shiftClicksLearnAndUnlearnWhenTheRulesAllowIt() {
    screens.openSkill(player, "adapt:agility");

    clickCard("skill", 0, ClickKind.SHIFT_LEFT);
    clickCard("skill", 0, ClickKind.SHIFT_RIGHT);
    clickCard("skill", 0, ClickKind.SHIFT_RIGHT);
    clickCard("skill", 1, ClickKind.SHIFT_LEFT);
    clickCard("skill", 1, ClickKind.SHIFT_RIGHT);

    assertThat(server.calls).containsExactly("learn adapt:agility/super_jump 2", "unlearn adapt:agility/super_jump 0");
  }

  @Test
  void aShiftRightUnlearnOnTheSkillPageArmsThenConfirms() {
    screens.openSkill(player, "adapt:agility");

    clickCard("skill", 0, ClickKind.SHIFT_RIGHT);
    assertThat(server.calls).isEmpty();
    assertThat(noticeShown("Confirm unlearn")).isTrue();

    clickCard("skill", 0, ClickKind.SHIFT_RIGHT);
    assertThat(server.calls).containsExactly("unlearn adapt:agility/super_jump 0");
  }

  @Test
  void aShiftRightUnlearnLapsesWhenItsWindowRunsOut() {
    screens.openSkill(player, "adapt:agility");

    clickCard("skill", 0, ClickKind.SHIFT_RIGHT);
    clock.now += PackScreens.SEAL_WINDOW_MS + 1;
    clickCard("skill", 0, ClickKind.SHIFT_RIGHT);

    assertThat(server.calls).isEmpty();
  }

  @Test
  void aPermanentLearnOnTheSkillPageArmsThenConfirms() {
    server.catalog = ScreenFixtures.catalog(true);
    screens.openSkill(player, "adapt:agility");

    clickCard("skill", 0, ClickKind.SHIFT_LEFT);
    assertThat(server.calls).isEmpty();
    assertThat(noticeShown("Confirm learn")).isTrue();

    clickCard("skill", 0, ClickKind.SHIFT_LEFT);
    assertThat(server.calls).containsExactly("learn adapt:agility/super_jump 2");
  }

  @Test
  void aPermanentLearnInDebugModeTakesOneClick() {
    server.catalog = ScreenFixtures.catalog(true);
    server.player = ScreenFixtures.debugPlayer(1, 3L);
    screens.openSkill(player, "adapt:agility");

    clickCard("skill", 0, ClickKind.SHIFT_LEFT);

    assertThat(server.calls).containsExactly("learn adapt:agility/super_jump 2");
  }

  @Test
  void aLearnTheViewerCannotAffordIsRefusedBeforeItIsSent() {
    server.player = ScreenFixtures.player(1, 2L);
    screens.openSkill(player, "adapt:agility");

    clickCard("skill", 0, ClickKind.SHIFT_LEFT);

    assertThat(server.calls).isEmpty();
    assertThat(player.actionBars()).anyMatch((String line) -> line.contains("Needs 1 more Kn"));
  }

  @Test
  void aPlainClickOnACellOpensTheAdaptation() {
    screens.openSkill(player, "adapt:agility");

    clickCard("skill", 1, ClickKind.LEFT);

    assertThat(server.routes).containsExactly(new Route.Adaptation("adapt:agility/wall_jump"));
    assertThat(window().menu.title()).isEqualTo("Wall Jump");
  }

  @Test
  void groupsGetHeadingCellsAndKeptOriginalsFollowFourToACell() {
    List<PackSkillScreen.Cell> cells = List.of(heading(), adaptation(), adaptation(), adaptation(), adaptation(), adaptation(),
        adaptation(), heading(), adaptation());

    List<List<PackSkillScreen.Cell>> leaves = PackSkillScreen.leaves(cells, 16);

    assertThat(leaves).hasSize(1);
    assertThat(leaves.get(0).get(7)).isNull();
    assertThat(leaves.get(0).get(8).heading()).isEqualTo("H");
    assertThat(PackSkillScreen.leaves(List.of(adaptation(), adaptation(), adaptation()), 2)).hasSize(2);
  }

  @Test
  void aGroupThatWouldRunIntoTheRightColumnStartsItWhenItFitsThere() {
    List<PackSkillScreen.Cell> cells = List.of(heading(), adaptation(), adaptation(), adaptation(), heading(), adaptation(),
        adaptation(), adaptation(), adaptation(), adaptation());

    List<PackSkillScreen.Cell> leaf = PackSkillScreen.leaves(cells, 16).get(0);

    assertThat(leaf.subList(4, 8)).containsOnlyNulls();
    assertThat(leaf.get(8).heading()).isEqualTo("H");
    assertThat(leaf).hasSize(14);
    List<PackSkillScreen.Cell> longGroup = new ArrayList<>(List.of(heading(), adaptation(), heading()));
    longGroup.addAll(Collections.nCopies(9, adaptation()));
    assertThat(PackSkillScreen.leaves(longGroup, 16).get(0).get(2).heading()).as("a group longer than a column runs on").isEqualTo("H");
  }

  @Test
  void theLevelTakesTheFreeSideForAnyNameThatFits() {
    assertThat(PackSkillScreen.identity(1, true)).isEqualTo(PackSkillScreen.Identity.SIDE);
    assertThat(PackSkillScreen.identity(2, true)).isEqualTo(PackSkillScreen.Identity.SIDE);
    assertThat(PackSkillScreen.identity(1, false)).isEqualTo(PackSkillScreen.Identity.BESIDE);
    assertThat(PackSkillScreen.identity(2, false)).isEqualTo(PackSkillScreen.Identity.ALONE);
    assertThat(PackSkillScreen.identity(0, true)).isEqualTo(PackSkillScreen.Identity.ALONE);
  }

  private static PackSkillScreen.Cell heading() {
    return new PackSkillScreen.Cell("H", 0, null);
  }

  private static PackSkillScreen.Cell adaptation() {
    return new PackSkillScreen.Cell(null, 0, ScreenFixtures.superJump());
  }

  @Test
  void theSkillSwitchSetsTheSkillPreference() {
    screens.openSkill(player, "adapt:agility");

    click("skill", "switch", ClickKind.LEFT);

    assertThat(server.calls).containsExactly("set SKILL adapt:agility enabled OFF");
  }

  @Test
  void mutateOpensTheSpreadAndBackReturnsToTheSkill() {
    screens.openSkill(player, "adapt:agility");

    click("skill", "mutate", ClickKind.LEFT);
    assertThat(server.routes).containsExactly(new Route.Mutate("adapt:agility"));
    assertThat(window().menu.title()).isEqualTo("Mutate");

    click("mutate", "foot.back", ClickKind.LEFT);
    assertThat(window().menu.title()).isEqualTo("Agility");
  }

  @Test
  void splitArmsThenConfirms() {
    server.unmutateOffered = true;
    screens.openSkill(player, "adapt:agility");

    click("skill", "mutate", ClickKind.LEFT);
    assertThat(server.calls).isEmpty();
    assertThat(window().oneShots).containsExactly(layout("skill").slot("mutate") + "/" + PackScreens.SEAL_WINDOW_TICKS);
    assertThat(button("skill", "mutate").icon().name()).isEqualTo("Confirm split");
    assertThat(noticeShown("Confirm split")).isTrue();

    click("skill", "mutate", ClickKind.LEFT);
    assertThat(server.calls).containsExactly("unmutate adapt:agility");
    assertThat(window().menu.title()).isEqualTo("Skills");
  }

  @Test
  void adaptationGolden() {
    assertThat(screens.openAdaptation(player, "adapt:agility/super_jump")).isTrue();
    Goldens.check("adaptation-levels", dump());
  }

  @Test
  void adaptationTopRowsOnlyGolden() {
    windows.support = PackSupport.NONE;
    assertThat(screens.openAdaptation(player, "adapt:agility/super_jump")).isTrue();
    Goldens.check("adaptation-levels-top", dump());
  }

  @Test
  void learnGoesToTheTargetAndUnlearnArmsFirst() {
    screens.openAdaptation(player, "adapt:agility/super_jump");

    click("adaptation.levels", "learn", ClickKind.LEFT);
    click("adaptation.levels", "unlearn", ClickKind.LEFT);
    assertThat(server.calls).containsExactly("learn adapt:agility/super_jump 2");
    assertThat(button("adaptation.levels", "unlearn").icon().name()).isEqualTo("Confirm unlearn");
    assertThat(button("adaptation.levels", "learn").icon().name()).isEqualTo("Learn II");
    assertThat(noticeShown("Confirm unlearn")).isTrue();
    click("adaptation.levels", "unlearn", ClickKind.LEFT);

    assertThat(server.calls).containsExactly("learn adapt:agility/super_jump 2", "unlearn adapt:agility/super_jump 0");
  }

  @Test
  void aPermanentLearnArmsThenConfirmsInsideItsWindow() {
    server.catalog = ScreenFixtures.catalog(true);
    screens.openAdaptation(player, "adapt:agility/super_jump");

    click("adaptation.levels", "learn", ClickKind.LEFT);
    assertThat(server.calls).isEmpty();
    assertThat(button("adaptation.levels", "learn").icon().name()).isEqualTo("Confirm learn");
    assertThat(window().oneShots).containsExactly(layout("adaptation.levels").slot("learn") + "/" + PackScreens.SEAL_WINDOW_TICKS);

    clock.now += PackScreens.SEAL_WINDOW_MS + 1;
    click("adaptation.levels", "learn", ClickKind.LEFT);
    assertThat(server.calls).isEmpty();

    click("adaptation.levels", "learn", ClickKind.LEFT);
    assertThat(server.calls).containsExactly("learn adapt:agility/super_jump 2");
  }

  @Test
  void armingOneControlDoesNotConfirmAnother() {
    screens.openAdaptation(player, "adapt:agility/super_jump");

    click("adaptation.levels", "unlearn", ClickKind.LEFT);
    window().click(layout("adaptation.levels").slots("foot.tabs").get(2), ClickKind.LEFT);
    click("adaptation.settings", "pref.reset", ClickKind.LEFT);

    assertThat(server.calls).isEmpty();
    assertThat(button("adaptation.settings", "pref.reset").icon().name()).isEqualTo("Confirm reset");
  }

  @Test
  void aPipMakesItsLevelTheTarget() {
    server.player = ScreenFixtures.player(1, 10L);
    screens.openAdaptation(player, "adapt:agility/super_jump");
    List<PackSlot> pips = layout("adaptation.levels").slots("pips");

    window().click(pips.get(2), ClickKind.LEFT);
    assertThat(button("adaptation.levels", "learn").icon().name()).isEqualTo("Learn III");
    assertThat(window().surface.look(pips.get(1)).itemModel().path()).isEqualTo("adapt_ui/art/pip/target_2");
    click("adaptation.levels", "learn", ClickKind.LEFT);

    assertThat(server.calls).containsExactly("learn adapt:agility/super_jump 3");
  }

  @Test
  void aTargetBeyondWhatTheViewerCanAffordIsRefused() {
    screens.openAdaptation(player, "adapt:agility/super_jump");

    window().click(layout("adaptation.levels").slots("pips").get(2), ClickKind.LEFT);
    click("adaptation.levels", "learn", ClickKind.LEFT);

    assertThat(server.calls).isEmpty();
  }

  @Test
  void recipesGetATabAndAlternativesCycle() {
    screens.openAdaptation(player, "adapt:agility/super_jump");

    window().click(layout("adaptation.levels").slots("foot.tabs").get(1), ClickKind.LEFT);

    assertThat(window().surface.cycles()).isNotEmpty()
        .allSatisfy((art.arcane.adapt.pack.surface.SlotCycle cycle) -> assertThat(cycle.periodTicks()).isEqualTo(24));
    Goldens.check("adaptation-recipes", dump());

    window().click(layout("adaptation.recipes").slots("recipe.pick").get(1), ClickKind.LEFT);
    assertThat(window().surface.cycles()).isEmpty();
    assertThat(window().surface.titleText()).contains("Pads");
  }

  @Test
  void aSingleRecipeDrawsNoCountAndNoPick() {
    server.recipes = ScreenFixtures.recipes().subList(2, 3);
    screens.openAdaptation(player, "adapt:agility/super_jump");

    window().click(layout("adaptation.levels").slots("foot.tabs").get(1), ClickKind.LEFT);

    assertThat(window().surface.titleText()).contains("Pads").doesNotContain("recipes", "recipe");
    for (PackSlot pick : layout("adaptation.recipes").slots("recipe.pick")) {
      MenuButton button = window().button(pick);
      assertThat(button == null || !button.clickable()).as(pick.toString()).isTrue();
    }
  }

  @Test
  void settingsStepThroughChoicesWithinTheRulesAndResetArmsFirst() {
    screens.openAdaptation(player, "adapt:agility/super_jump");
    window().click(layout("adaptation.levels").slots("foot.tabs").get(2), ClickKind.LEFT);
    Goldens.check("adaptation-settings", dump());
    PackSlot mode = layout("adaptation.settings").slots("pref.rows").get(0);

    window().click(mode, ClickKind.LEFT);
    assertThat(server.calls).isEmpty();

    server.player = ScreenFixtures.player(3, 3L);
    screens.changed(player);
    clock.run();
    window().click(mode, ClickKind.LEFT);
    window().click(mode, ClickKind.RIGHT);
    click("adaptation.settings", "pref.reset", ClickKind.LEFT);
    assertThat(button("adaptation.settings", "pref.reset").icon().name()).isEqualTo("Confirm reset");
    click("adaptation.settings", "pref.reset", ClickKind.LEFT);

    assertThat(server.calls).containsExactly("set ADAPTATION adapt:agility/super_jump mode HARD",
        "set ADAPTATION adapt:agility/super_jump mode WILD", "reset ADAPTATION adapt:agility/super_jump");
  }

  @Test
  void aMutatedPageHeadsItsGroupsByOriginAndKeepsItsOriginalsAndToolsOnTheNextLeaf() {
    String mutated = "adapt:agility+adapt:rift";
    AdaptationView own = new AdaptationView("adapt:mutated/agility/adapt/rift/graft", mutated, "Graft", "Grafted.", ScreenFixtures.PLAIN,
        true, 3, false, List.of(1, 2, 3), List.of(1, 2, 3), List.of(0, 0, 0), List.of(), List.of(List.of("+1"), List.of("+2"), List.of("+3")),
        List.of(), List.of());
    List<List<String>> rows = List.of(List.of("adapt:agility/super_jump"), List.of("adapt:rift/gate"), List.of(own.id()));
    SkillView skill = new SkillView(mutated, "Feral Blinkstrider", "Both.", ScreenFixtures.AGILITY_ICON, 0x55FFFF, true,
        rows, List.of(), List.of(ScreenFixtures.superJump(), ScreenFixtures.gate(), own));
    List<SkillView> skills = new ArrayList<>(server.catalog.catalog().skills());
    skills.add(skill);
    server.catalog = new CatalogIndex(new CatalogView(43L, "en_US", skills), Map.of());
    PlayerView base = server.player;
    Map<String, List<List<String>>> access = new LinkedHashMap<>(base.access().adaptationRows());
    access.put(mutated, rows);
    List<String> listed = new ArrayList<>(base.access().skills());
    listed.add(mutated);
    server.player = new PlayerView(base.header(), base.skills(), base.adaptationLevels(), base.preferences(),
        new PlayerAccess(false, true, true, listed, access));
    server.owners = Map.of(mutated, List.of("adapt:agility", "adapt:rift", mutated));
    server.kept = List.of(new KeptView(mutated, "adapt:agility/wall_jump", 1));
    server.tools = List.of(new ToolView(own.id(), "adapt:agility/tool/hook", "Hook", ScreenFixtures.PLAIN, 2,
        List.of(new ToolView.Ingredient("minecraft:string", 2))));

    assertThat(screens.openSkill(player, mutated)).isTrue();
    String first = window().surface.titleText();
    assertThat(first).contains("Agility", "Rift", "Feral", "Blinkstrider", "Level").doesNotContain("..").doesNotContain("Kept without learning");

    screens.page(player, 1);
    String shelf = window().surface.titleText();
    assertThat(shelf).contains("Kept without learning", "Tools");
    assertThat(window().button(layout(PackSkillScreen.SHELF).slots("kept").get(0)).icon().name()).contains("Wall Jump");
    assertThat(window().button(layout(PackSkillScreen.SHELF).slots("kept").get(0)).icon().lore())
        .anyMatch((String line) -> line.contains("Kept without learning"))
        .anyMatch((String line) -> line.contains("Jump off walls."))
        .anyMatch((String line) -> line.contains("+1 jump"))
        .noneMatch((String line) -> line.contains("+2 jumps"));
    assertThat(window().button(layout(PackSkillScreen.SHELF).slots("tools").get(0)).icon().lore()).anyMatch((String line) -> line.contains("2 × String"));
    assertThat(PackResources.kit(mutated, 1).kit()).isEqualTo("adapt:rift");
    assertThat(PackResources.kit(mutated, 1).partner()).isTrue();
  }

  @Test
  void aNameTooLongForItsLabelFallsToTheCondensedFontBeforeItIsCut() {
    TitleComposer composer = resources.composer();
    TextSpec label = new TextSpec(0, 20, 56, Align.LEFT, 0x2E2216, 1, 0);

    assertThat(resources.name(composer, label, "Mephistopheles").status()).isEqualTo(Placement.Status.PLACED);
    assertThat(resources.name(composer, label, "Agility").status()).isEqualTo(Placement.Status.PLACED);

    assertThat(composer.runs()).anyMatch((TitleRun run) -> run.font().contains("/condensed/") && run.text().equals("Mephistopheles"));
    assertThat(composer.runs()).anyMatch((TitleRun run) -> !run.font().contains("/condensed/") && run.text().equals("Agility"));
  }

  @Test
  void anAdaptationRowTooltipReadsLikeTheNativePage() {
    screens.openSkill(player, "adapt:agility");

    List<String> lore = window().button(layout("skill").cards().get(0).icon()).icon().lore();

    assertThat(lore.get(0)).contains("Level I of III");
    assertThat(lore.get(1)).contains("Learn II for 3 knowledge");
    assertThat(LegacyText.plain(lore.get(2))).isEqualTo("Jump higher.");
    assertThat(lore.get(3)).contains("+2 blocks");
  }

  @Test
  void aLongDescriptionEndsAtASentenceOnThePageAndContinuesOnTheIconTooltip() {
    String sentence = "Every jump lifts you a little higher than the last one did.";
    String description = String.join(" ", java.util.Collections.nCopies(14, sentence));
    AdaptationView jump = ScreenFixtures.superJump();
    AdaptationView longJump = new AdaptationView(jump.id(), jump.skillId(), jump.name(), description, jump.icon(), true, jump.maxLevel(),
        false, jump.knowledgeCosts(), jump.refunds(), jump.powerCosts(), jump.requirements(), jump.statLines(), jump.preferences(),
        jump.preferenceGroups());
    SkillView agility = server.catalog.skill("adapt:agility");
    SkillView longer = new SkillView(agility.id(), agility.name(), agility.description(), agility.icon(), agility.color(), true,
        agility.rowGroups(), agility.preferences(), List.of(longJump, ScreenFixtures.wallJump()));
    server.catalog = new CatalogIndex(new CatalogView(44L, "en_US", List.of(longer, server.catalog.skill("adapt:rift"))), Map.of());

    screens.openAdaptation(player, jump.id());

    String title = window().surface.titleText();
    assertThat(title).contains("Every jump lifts").doesNotContain("..");
    List<String> rest = new ArrayList<>();
    for (String line : window().button(layout("adaptation.levels").slot("icon")).icon().lore()) {
      String plain = LegacyText.plain(line);
      assertThat(PackFontMetrics.vanilla().width(plain)).isLessThanOrEqualTo(PackLabels.TOOLTIP_WIDTH);
      rest.add(plain);
    }
    assertThat(rest.size()).isGreaterThan(1);
    String joined = String.join(" ", rest);
    assertThat(joined).startsWith("Every jump lifts").endsWith("last one did.");
    assertThat(description).endsWith(joined);
    assertThat(title.replace(" ", "")).contains("lastonedid.");
  }

  @Test
  void mutateGolden() {
    screens.openMutate(player, "adapt:agility");
    pick(0);
    Goldens.check("mutate", dump());
  }

  @Test
  void mutateTopRowsOnlyPicksAPartnerFromNamedCardsAndReturnsToThemByTab() {
    windows.support = PackSupport.NONE;
    screens.openMutate(player, "adapt:agility");
    pick(0);
    Goldens.check("mutate-top", dump());

    click("mutate", "tab.partner", ClickKind.LEFT);
    assertThat(window().surface.titleText()).contains("Rift");
    pick(0);

    assertThat(server.calls).containsExactly("spread adapt:agility ", "spread adapt:agility adapt:rift", "spread adapt:agility adapt:rift");
    assertThat(button("mutate", "seal.mark")).isNotNull();
  }

  @Test
  void aPartnerIsPickedFromNamedCardsAndOnlyEligibleOnesClick() {
    screens.openMutate(player, "adapt:agility");

    assertThat(window().surface.titleText()).contains("Rift", "Agility");
    assertThat(window().button(layout(PackMutateScreen.PICK).cards().get(1).icon()).clickable()).isFalse();
    pick(0);

    assertThat(server.calls).containsExactly("spread adapt:agility ", "spread adapt:agility adapt:rift");
    assertThat(button("mutate", "seal.mark").clickable()).isTrue();
  }

  @Test
  void theLeadPickerListsTheLeadsByNameAndOpensTheChosenSpread() {
    assertThat(screens.openMutateLeads(player)).isTrue();
    assertThat(window().surface.titleText()).contains("Agility");

    pick(0);

    assertThat(server.calls).containsExactly("spread adapt:agility ");
    assertThat(window().surface.titleText()).contains("Rift");
  }

  @Test
  void theSealArmsThenMutatesInsideItsWindow() {
    screens.openMutate(player, "adapt:agility");
    pick(1);
    pick(0);
    assertThat(server.calls).containsExactly("spread adapt:agility ", "spread adapt:agility adapt:rift");

    click("mutate", "seal.mark", ClickKind.LEFT);
    assertThat(server.calls).hasSize(2);
    assertThat(window().surface.look(layout("mutate").slot("seal.mark")).sprite()).isEqualTo(PackArt.SEAL_ARMED);
    assertThat(button("mutate", "seal.mark").icon().name()).isEqualTo("Confirm mutate");
    assertThat(window().oneShots).hasSize(1);

    clock.now += PackScreens.SEAL_WINDOW_MS + 1;
    click("mutate", "seal.mark", ClickKind.LEFT);
    assertThat(server.calls).hasSize(2);

    window().click(layout("mutate").slots("seal").get(0), ClickKind.LEFT);
    assertThat(server.calls).last().isEqualTo("mutate adapt:agility adapt:rift");
  }

  @Test
  void anArmedSealDisarmsWhenItsWindowRunsOut() {
    screens.openMutate(player, "adapt:agility");
    pick(0);
    click("mutate", "seal.mark", ClickKind.LEFT);

    clock.run();

    assertThat(window().surface.look(layout("mutate").slot("seal.mark")).sprite()).isEqualTo(PackArt.SEAL_IDLE);
  }

  @Test
  void theArmedSealFillsFromItsOuterEndsAndDrainsBeforeItsWindowEnds() {
    screens.openMutate(player, "adapt:agility");
    pick(0);
    click("mutate", "seal.mark", ClickKind.LEFT);
    assertThat(filled()).containsExactly(true, false, false, false, false, true);

    clock.now += PackScreens.SEAL_STEP_MS * 2;
    screens.page(player, 0);
    assertThat(filled()).containsOnly(true);

    clock.now += PackScreens.SEAL_WINDOW_MS - (PackScreens.SEAL_STEP_MS * 3);
    screens.page(player, 0);
    assertThat(filled()).containsExactly(true, false, false, false, false, true);
  }

  @Test
  void bookSettingsSetTheViewersMenusAndReopenSkills() {
    screens.openSkills(player);

    click("skills", "book.settings", ClickKind.LEFT);

    assertThat(windows.opened).hasSize(2);
    assertThat(window().menu.button(2).icon().name()).endsWith("Book");
    assertThat(window().menu.button(4).icon().name()).endsWith("Chest");
    assertThat(window().menu.button(6).icon().name()).endsWith("> Auto");
    window().click(4, ClickKind.LEFT);
    assertThat(server.calls).containsExactly("menus CHEST");
    assertThat(server.routes).containsExactly(new Route.Skills());
  }

  @Test
  void changesRedrawTheOpenPageOnceOnTheNextTick() {
    screens.openSkills(player);

    screens.changed(player);
    screens.changed(player);
    assertThat(clock.later).hasSize(1);
    clock.run();
    assertThat(window().updates).isEqualTo(1);

    screens.refresh(player);
    assertThat(clock.later).isEmpty();
    server.fingerprint = 2L;
    screens.refresh(player);
    clock.run();
    assertThat(window().updates).isEqualTo(2);
  }

  @Test
  void aSkillLeavingTheCatalogClosesItsOpenPageAndSkillsOpensNext() {
    screens.openSkill(player, "adapt:rift");
    FakePackWindows.Window page = window();

    server.catalog = new CatalogIndex(new CatalogView(43L, "en_US", List.of(server.catalog.skill("adapt:agility"))), Map.of());
    screens.refresh(player);
    clock.run();

    assertThat(page.open).isFalse();
    assertThat(screens.openSkills(player)).isTrue();
    assertThat(window()).isNotSameAs(page);
  }

  @Test
  void closingForgetsTheViewer() {
    screens.openSkills(player);

    window().close();
    screens.changed(player);

    assertThat(clock.later).isEmpty();
  }

  @Test
  void artSlotsHoldOnlyTheKitsDressing() {
    for (PackSupport support : List.of(PackSupport.FULL, PackSupport.NONE)) {
      windows.support = support;
      screens.openSkill(player, "adapt:agility");
      String kind = support.lowerSlots() ? "single" : "fallback";
      List<PackManifest.Host> hosts = resources.manifest().page("adapt:agility", kind).hosts();
      for (PackSlot slot : layout("skill").slots("art")) {
        MenuButton button = window().button(slot);
        PackManifest.Host host = resources.host(hosts, slot);
        if (host == null) {
          assertThat(button == null || !button.clickable()).as(slot.toString()).isTrue();
          continue;
        }
        assertThat(button.clickable()).isFalse();
        assertThat(window().surface.look(slot).itemModel()).isEqualTo(host.art().model());
        assertThat(window().surface.look(slot).hideTooltip()).isTrue();
      }
      window().close();
    }
  }

  @Test
  void skillsSeatEverySkillTheViewerMayOpenAndGreyTheOnesNotStarted() {
    screens.openSkills(player);

    assertThat(window().button(layout("skills").cards().get(1).icon()).icon().name()).contains("Rift");
    assertThat(window().surface.look(layout("skills").cards().get(1).icon()).itemModel())
        .isEqualTo(resources.manifest().sprite("adapt:gui/skill/rift", true, PackArt.CHEST_SHIFT).model());
    window().close();

    server.inactive = false;
    screens.openSkills(player);

    assertThat(window().button(layout("skills").cards().get(0).icon()).icon().name()).contains("Agility");
    assertThat(window().button(layout("skills").cards().get(1).icon())).isNull();
  }

  @Test
  void theHotbarRowHoldsOnlyBlankItemsWithTheirFacesInTheTitleAndOnlyTheSelectedOneUnnamed() {
    Id blank = resources.manifest().item(PackArt.BLANK).model();
    List<Runnable> opens = List.of(() -> screens.openSkills(player), () -> screens.openSkill(player, "adapt:agility"),
        () -> screens.openAdaptation(player, "adapt:agility/super_jump"), () -> screens.openMutate(player, "adapt:agility"));
    for (Runnable open : opens) {
      open.run();
      List<PackManifest.Host> hosts = new ArrayList<>();
      resources.manifest().pages().values().forEach((PackManifest.PageArt art) -> hosts.addAll(art.hosts()));
      for (int index = PageBuilder.HOTBAR; index < PackSlot.LOWER_SLOTS; index++) {
        PackSlot slot = PackSlot.lower(index);
        Id model = window().surface.look(slot).itemModel();
        boolean host = hosts.stream().anyMatch((PackManifest.Host each) -> each.art().model().equals(model));
        assertThat(model.equals(blank) || host).as(window().menu.title() + " " + slot + " " + model).isTrue();
        if (index - PageBuilder.HOTBAR == player.inventory().heldSlot()) {
          assertThat(window().surface.lower().get(index).icon().name()).as(slot.toString()).isNullOrEmpty();
        }
      }
      window().close();
    }

    player.inventory().setHeldSlot(8);
    screens.openSkills(player);
    PackSlot settings = layout("skills").slot("book.settings");
    assertThat(window().surface.lower().get(settings.index()).icon().name()).isEmpty();
    assertThat(window().surface.lower().get(settings.index()).icon().lore().get(0)).isEqualTo("Settings");
    screens.openSkill(player, "adapt:agility");
    assertThat(window().surface.lower().get(layout("skill").slot("foot.back").index()).icon().name()).isNotEmpty();
  }

  @Test
  void learningByClickRedrawsCountCostAndChipsAndARefusalStaysOnThePage() {
    server.onLearn = (int level) -> server.player = ScreenFixtures.player(level, 0L);
    screens.openSkill(player, "adapt:agility");
    assertThat(window().surface.titleText()).contains("1/3").doesNotContain("2/3");

    clickCard("skill", 0, ClickKind.SHIFT_LEFT);

    assertThat(server.calls).containsExactly("learn adapt:agility/super_jump 2");
    assertThat(window().surface.titleText()).contains("2/3");
    assertThat(window().open).isTrue();
    screens.openAdaptation(player, "adapt:agility/super_jump");
    assertThat(window().surface.titleText()).contains("Learn III").contains("4");

    click("adaptation.levels", "learn", ClickKind.LEFT);

    assertThat(server.calls).hasSize(1);
    assertThat(window().open).isTrue();
    assertThat(windows.opened).hasSize(1);
    assertThat(player.actionBars()).anyMatch((String line) -> line.contains("Needs 4 more"));
    assertThat(button("adaptation.levels", "learn").icon().lore()).anyMatch((String line) -> line.contains("Needs 4 more"));
  }

  /** Whether {@code text} shows as the page's notice, on the action bar. */
  private boolean noticeShown(String text) {
    return player.actionBars().stream().anyMatch((String line) -> line.contains(text));
  }

  /** Whether each seal segment beside the mark draws its filled art, left to right. */
  private List<Boolean> filled() {
    List<Boolean> filled = new ArrayList<>();
    for (PackSlot slot : layout("mutate").slots("seal")) {
      if (!slot.equals(layout("mutate").slot("seal.mark"))) {
        Id model = window().surface.look(slot).itemModel();
        filled.add(PackArt.SEAL_PARTS.stream().anyMatch((String part) -> model.equals(resources.manifest().item(PackArt.seal(true, part)).model())));
      }
    }
    return filled;
  }

  @Test
  void theDeliveredAudienceIsEmptyWhileDeliveryIsOff() {
    assertThat(PackAudience.DELIVERED.book(player)).isFalse();
  }

  @Test
  void adaptScreensRoutesPackViewersToThePackScreen() {
    AdaptPorts.install(PackScreens.class, screens);

    AdaptScreens.openSkills(player);

    assertThat(window()).isNotNull();
    assertThat(delegate.opened).isEmpty();
  }
}
