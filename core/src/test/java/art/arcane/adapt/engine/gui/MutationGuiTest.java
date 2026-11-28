package art.arcane.adapt.engine.gui;

import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.PairKeys;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.mutation.MutationNotice;
import art.arcane.adapt.engine.mutation.MutationOffers;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.view.MutationWords;
import art.arcane.adapt.client.view.MutationSpreadViews;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.server.panel.PanelViews;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class MutationGuiTest {
  @TempDir
  Path data;

  private final Menus menus = new Menus();
  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).menus(menus).build();
    player = platform.player("Mutant", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class, SkillAxes.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void mutateIsOfferedOnlyWhenTheSkillAndAnotherFreeSkillQualifyAndASlotIsLeft() {
    PlayerData playerData = profile.getData();
    AdaptConfig.Mutations config = AdaptConfig.get().getMutations();
    level("swords", 15, 0);
    level("stealth", 14, 0);

    assertThat(MutationOffers.offered(playerData, skill("swords"), registry.getSkills(), config)).isFalse();
    level("stealth", 15, 0);
    assertThat(MutationOffers.offered(playerData, skill("swords"), registry.getSkills(), config)).isTrue();
    assertThat(MutationOffers.offered(playerData, skill("axes"), registry.getSkills(), config)).isFalse();
    assertThat(MutationOffers.partners(playerData, skill("swords"), registry.getSkills(), config))
        .containsExactly(skill("stealth"));

    level("axes", 20, 0);
    SkillMutations.mutate(playerData, skill("swords"), skill("stealth"), "Mutant");
    assertThat(MutationOffers.offered(playerData, skill("axes"), registry.getSkills(), config)).isFalse();
    assertThat(MutationOffers.offered(playerData, registry.getMutatedSkill(PairKeys.parse("swords+stealth")),
        registry.getSkills(), config)).isFalse();
  }

  @Test
  void thePreviewMatchesTheMergeAndListsEverythingLost() {
    PlayerData playerData = profile.getData();
    PlayerSkillLine swords = level("swords", 22, 9);
    level("stealth", 17, 5);
    Adaptation<?> stealthHeld = skill("stealth").getAdaptations().getFirst();
    Adaptation<?> swordsHeld = skill("swords").getAdaptations().getFirst();
    playerData.getSkillLine("stealth").setAdaptation(stealthHeld, 2);
    swords.setAdaptation(swordsHeld, 1);

    MutationOffers.Preview preview = MutationOffers.preview(playerData, skill("swords"), skill("stealth"));

    assertThat(preview.name()).isEqualTo("Silent Duelist");
    assertThat(preview.level()).isEqualTo(17);
    assertThat(preview.knowledge()).isEqualTo(5L + AdaptationHost.refundCostFor(stealthHeld, 0, 2));
    assertThat(preview.higher()).isSameAs(skill("swords"));
    assertThat(preview.lostLevels()).isEqualTo(5);
    assertThat(preview.lostKnowledge()).isEqualTo(9L + AdaptationHost.refundCostFor(swordsHeld, 0, 1));
    assertThat(preview.unlearned()).extracting(SkillMutations.HeldAdaptation::adaptation)
        .containsExactlyInAnyOrder(swordsHeld, stealthHeld);
    assertThat(preview.upgrades()).containsExactlyElementsOf(
        Stream.concat(SkillHost.of(skill("swords")).getUpgrades().stream(), SkillHost.of(skill("stealth")).getUpgrades().stream()).toList());
    List<MutationSummary.Section> losses = MutationSummary.losses(
        MutationSpreadViews.build(registry, playerData, skill("swords"), skill("stealth"), AdaptConfig.get().getMutations(), ""), registry);
    assertThat(losses).extracting(MutationSummary.Section::skillId).containsExactly("adapt:swords", "adapt:stealth");
    assertThat(losses.get(0).detail()).isEqualTo("5 levels · " + preview.lostKnowledge() + " knowledge");
    assertThat(losses.get(0).entries()).extracting(MutationEntry::name).contains(C.stripColor(swordsHeld.getShortName()) + " I");
    assertThat(losses.get(1).detail()).isEmpty();
    assertThat(losses.get(1).entries()).extracting(MutationEntry::name).contains(C.stripColor(stealthHeld.getShortName()) + " II");

    SkillMutations.mutate(playerData, skill("swords"), skill("stealth"), "Mutant");
    PlayerSkillLine merged = playerData.getSkillLine("swords");
    assertThat(merged.getLevel()).isEqualTo(preview.level());
    assertThat(merged.getKnowledge()).isEqualTo(preview.knowledge());
  }

  @Test
  void theLossesKeepThePagesWordingForAPermanentAdaptation() {
    MutationEntry permanent = new MutationEntry("adapt:haste", IconRef.item("minecraft:sugar"), "Haste II (permanent, still unlearned)", "", 2);
    MutationSide lead = new MutationSide("adapt:swords", 20, List.of(), List.of(), List.of(), List.of(permanent), "");
    MutationSpreadView spread = new MutationSpreadView("adapt:swords", "adapt:stealth", "Silent Duelist", "", List.of(), lead,
        MutationSide.NONE, List.of(), new MutationWords("", "", "", "", "", "", "", "", ""), "");

    assertThat(MutationSummary.lore(MutationSummary.losses(spread, registry), registry)).extracting(C::stripColor)
        .containsExactly("Swords Lv 20", "Haste II (permanent, still unlearned)");
  }

  @Test
  void pickingAPartnerOpensTheConfirmStep() {
    level("swords", 20, 0);
    level("stealth", 20, 0);

    MutationGui.openPartners(player, skill("swords"));
    settle();
    assertThat(C.stripColor(menus.menu.title())).isEqualTo("Mutate");
    assertThat(menus.menu.rows()).isEqualTo(3);
    assertThat(C.stripColor(menus.menu.button(4).icon().name())).isEqualTo("Lead: Swords");

    click(ChestMenu.slot(1, 4));
    assertThat(C.stripColor(menus.menu.title())).isEqualTo("Silent Duelist");
    assertThat(C.stripColor(menus.menu.button(ChestMenu.slot(1, 1)).icon().name())).endsWith("Swords");
    assertThat(C.stripColor(menus.menu.button(ChestMenu.slot(1, 3)).icon().name())).endsWith("Stealth");
    assertThat(C.stripColor(menus.menu.button(ChestMenu.slot(1, 5)).icon().name())).isEqualTo("You lose");
    assertThat(C.stripColor(menus.menu.button(ChestMenu.slot(1, 7)).icon().name())).isEqualTo("You gain");
    assertThat(menus.menu.button(ChestMenu.slot(2, 7)).clickable()).isTrue();
    assertThat(profile.getData().getMutations()).isEmpty();

    click(ChestMenu.slot(2, 0));
    assertThat(C.stripColor(menus.menu.title())).isEqualTo("Mutate");
  }

  @Test
  void confirmingTakesTwoClicksAndSwappingTheLeadDisarmsIt() {
    level("swords", 20, 0);
    level("stealth", 20, 0);

    MutationGui.open(player, skill("swords"), skill("stealth"), false);
    settle();
    assertThat(C.stripColor(menus.menu.title())).isEqualTo("Silent Duelist");

    click(ChestMenu.slot(2, 7));
    assertThat(profile.getData().getMutations()).isEmpty();
    assertThat(menus.menu.button(ChestMenu.slot(2, 7)).icon().type()).isEqualTo(Id.minecraft("red_stained_glass_pane"));

    click(ChestMenu.slot(1, 2));
    assertThat(C.stripColor(menus.menu.title())).isEqualTo("Bladed Shade");
    assertThat(menus.menu.button(ChestMenu.slot(2, 7)).icon().type()).isEqualTo(Id.minecraft("nether_star"));
    assertThat(profile.getData().getMutations()).isEmpty();

    click(ChestMenu.slot(2, 7));
    click(ChestMenu.slot(2, 7));
    assertThat(profile.getData().getMutations()).extracting(mutation -> mutation.key()).containsExactly("adapt:stealth+adapt:swords");
  }

  @Test
  void theConfirmSaysItCannotBeUndoneBeforeAndAfterArming() {
    level("swords", 20, 0);
    level("stealth", 20, 0);
    for (boolean armed : List.of(false, true)) {
      MutationGui.open(player, skill("swords"), skill("stealth"), armed);
      settle();
      assertThat(menus.menu.button(ChestMenu.slot(2, 7)).icon().lore()).map(C::stripColor).contains("This cannot be undone");
      assertThat(menus.menu.button(ChestMenu.slot(1, 5)).icon().lore()).map(C::stripColor).contains("This cannot be undone");
    }
  }

  @Test
  void theConfirmStepBecomesANativePanelWithItsHeaderActionsAndArmedConfirm() {
    level("swords", 20, 0);
    level("stealth", 20, 0);
    MutationGui.open(player, skill("swords"), skill("stealth"), true);
    settle();

    PanelView view = PanelViews.view(1, 0, menus.menu);

    assertThat(view.layout()).isEqualTo(PanelLayout.GRID);
    assertThat(tile(view, 4).role()).isEqualTo(TileRole.HEADER);
    assertThat(tile(view, ChestMenu.slot(1, 2)).role()).isEqualTo(TileRole.ACTION);
    assertThat(tile(view, ChestMenu.slot(2, 7)).role()).isEqualTo(TileRole.ACTION);
    assertThat(tile(view, ChestMenu.slot(2, 7)).hint().kind()).isEqualTo(HintKind.DESTRUCTIVE);
    assertThat(view.tiles()).filteredOn((PanelTile tile) -> tile.slot() / 9 == 1 && tile.role() == TileRole.INFO).hasSize(4);
  }

  @Test
  void theGainItemNamesEveryUpgradeOwnAdaptationAndToolAndItsListShowsEachOne() {
    level("swords", 20, 0);
    level("stealth", 20, 0);
    MutationGui.open(player, skill("swords"), skill("stealth"), false);
    settle();

    MutationOffers.Preview preview = MutationOffers.preview(profile.getData(), skill("swords"), skill("stealth"));
    String gain = String.join(" ", menus.menu.button(ChestMenu.slot(1, 7)).icon().lore().stream().map(C::stripColor).toList());
    List<String> expected = new ArrayList<>();
    for (Adaptation<?> adaptation : preview.upgrades()) {
      expected.add(C.stripColor(adaptation.getShortName()));
    }
    for (Adaptation<?> adaptation : preview.mutated()) {
      expected.add(C.stripColor(adaptation.getShortName()));
    }
    for (Adaptation<?> adaptation : preview.upgrades()) {
      for (art.arcane.adapt.api.mutation.MutationTool<?> tool : MutationTools.handedOut(adaptation)) {
        expected.add(C.stripColor(MutationTools.name(tool)));
      }
    }
    assertThat(expected).allMatch(gain::contains);

    click(ChestMenu.slot(1, 7));
    assertThat(C.stripColor(menus.menu.title())).isEqualTo("You gain");
    List<String> listed = new ArrayList<>();
    while (true) {
      for (MenuButton button : menus.menu.buttons().values()) {
        listed.add(C.stripColor(button.icon().name()));
      }
      MenuButton next = menus.menu.button(ChestMenu.slot(menus.menu.rows() - 1, 5));
      if (next == null || !next.clickable()) {
        break;
      }
      click(ChestMenu.slot(menus.menu.rows() - 1, 5));
    }
    assertThat(listed).containsAll(expected);
    click(ChestMenu.slot(menus.menu.rows() - 1, 0));
    assertThat(C.stripColor(menus.menu.title())).isEqualTo("Silent Duelist");
  }

  @Test
  void eachSummaryListKeepsItsOwnIconAfterTheOtherWasOpened() {
    level("swords", 20, 0);
    level("stealth", 20, 0);
    MutationGui.open(player, skill("swords"), skill("stealth"), false);
    settle();

    click(ChestMenu.slot(1, 5));
    assertThat(menus.menu.button(4).icon().type()).isEqualTo(Id.minecraft("barrier"));
    click(ChestMenu.slot(menus.menu.rows() - 1, 0));
    click(ChestMenu.slot(1, 7));
    assertThat(menus.menu.button(4).icon().type()).isEqualTo(Id.minecraft("emerald"));
  }

  @Test
  void theSkillPageShowsMutateOnceASecondSkillQualifies() {
    level("swords", 20, 0);
    ComponentHost.of(skill("swords")).openGui(player);
    settle();
    assertThat(mutateButton()).isNull();

    level("stealth", 15, 0);
    ComponentHost.of(skill("swords")).openGui(player);
    settle();
    assertThat(mutateButton()).isNotNull();
  }

  @Test
  void theAvailabilityToastIsSentOnceAndRemembered() {
    AdaptConfig.Mutations config = AdaptConfig.get().getMutations();
    level("swords", 20, 0);
    assertThat(MutationNotice.sendIfAvailable(profile, registry.getSkills(), config)).isFalse();

    level("stealth", 15, 0);
    assertThat(MutationNotice.sendIfAvailable(profile, registry.getSkills(), config)).isTrue();
    assertThat(MutationNotice.sendIfAvailable(profile, registry.getSkills(), config)).isFalse();
    assertThat(PlayerData.fromJson(profile.getData().toJson(true)).isMutationNoticeSent()).isTrue();
    assertThat(PlayerData.fromJson(new PlayerData().toJson(true)).isMutationNoticeSent()).isFalse();
    assertThat(new PlayerData().toJson(true)).doesNotContain("mutationNoticeSent");
  }

  @Test
  void atTheMutationLimitTheOffersAreNotWalked() throws Exception {
    AdaptConfig.Mutations config = TomlCodec.fromToml("maxMutations = 1", AdaptConfig.Mutations.class);
    level("swords", 20, 0);
    level("stealth", 20, 0);
    SkillMutations.mutate(profile.getData(), skill("swords"), skill("stealth"), "Mutant");
    level("axes", 20, 0);

    try (MockedStatic<MutationOffers> offers = mockStatic(MutationOffers.class, CALLS_REAL_METHODS)) {
      assertThat(MutationNotice.available(profile.getData(), registry.getSkills(), config)).isFalse();
      offers.verify(() -> MutationOffers.leads(any(), any(), any()), never());
    }
  }

  private static PanelTile tile(PanelView view, int slot) {
    return view.tiles().stream().filter((PanelTile tile) -> tile.slot() == slot).findFirst().orElseThrow();
  }

  private MenuButton mutateButton() {
    for (Map.Entry<Integer, MenuButton> button : menus.menu.buttons().entrySet()) {
      if (button.getValue().icon().name() != null && C.stripColor(button.getValue().icon().name()).equals("Mutate")) {
        return button.getValue();
      }
    }
    return null;
  }

  private void click(int slot) {
    MenuButton button = menus.menu.button(slot);
    assertThat(button).as("button at %s", slot).isNotNull();
    assertThat(button.clickable()).as("clickable at %s", slot).isTrue();
    button.action().click(new MenuClick(player, menus.session, slot, ClickKind.LEFT));
    settle();
  }

  private void settle() {
    platform.scheduler().advance(1);
  }

  private Skill<?> skill(String name) {
    return registry.getSkill(name);
  }

  private PlayerSkillLine level(String skill, int level, long knowledge) {
    PlayerSkillLine line = profile.getData().getSkillLine(skill);
    line.setXp(XP.getXpForLevel(level) + 1D);
    line.setKnowledge(knowledge);
    line.settleWithoutRewards();
    return line;
  }

  private static final class Menus implements HostMenus {
    private volatile ChestMenu menu;
    private volatile Session session;

    @Override
    public MenuSession open(PlayerRef viewer, ChestMenu opened) {
      menu = opened;
      session = new Session(viewer, opened);
      return session;
    }

    @Override
    public MenuSession current(PlayerRef viewer) {
      return session;
    }

    @Override
    public void closeAll() {
      session = null;
    }

    @Override
    public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
      return null;
    }

    @Override
    public StorageView currentStorage(PlayerRef viewer) {
      return null;
    }

    private final class Session implements MenuSession {
      private final PlayerRef viewer;
      private ChestMenu shown;
      private boolean open = true;

      private Session(PlayerRef viewer, ChestMenu shown) {
        this.viewer = viewer;
        this.shown = shown;
      }

      @Override
      public PlayerRef viewer() {
        return viewer;
      }

      @Override
      public ChestMenu menu() {
        return shown;
      }

      @Override
      public boolean isOpen() {
        return open && session == this;
      }

      @Override
      public void update(ChestMenu updated) {
        shown = updated;
        menu = updated;
      }

      @Override
      public void close() {
        open = false;
      }
    }
  }
}
