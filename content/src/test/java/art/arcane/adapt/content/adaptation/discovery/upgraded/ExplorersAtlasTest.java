package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestClocks;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerLootGenerateEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.ChunkStructures;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Explorer's Atlas: private finds, pins and waypoints capped by level, and an honest, non-loading search at level 3. */
class ExplorersAtlasTest {
  private static final Id VILLAGE = Id.minecraft("jigsaw");
  private static final Box HERE = new Box(-8D, 50D, -8D, 24D, 80D, 24D);

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private SkillRegistry registry;
  private FakePlayer player;
  private AdaptPlayer profile;
  private Omniscience omniscience;
  private final List<ChestMenu> opened = new ArrayList<>();

  @BeforeEach
  void start() {
    HostMenus menus = mock(HostMenus.class);
    when(menus.open(any(), any())).thenAnswer(invocation -> {
      opened.add(invocation.getArgument(1));
      return mock(MenuSession.class);
    });
    platform = TestPlatform.builder(data).menus(menus).build();
    player = platform.player("Seeker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillDiscovery.class, SkillSwords.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    omniscience = SkillHost.of(registry.getSkill("discovery")).getUpgrades().stream().filter(Omniscience.class::isInstance)
        .map(Omniscience.class::cast).findFirst().orElseThrow();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theAtlasIsCraftedFromABookAMapAndACompassThroughOmniscience() {
    assertThat(omniscience.getTools()).contains(omniscience.atlas());
    assertThat(omniscience.getRecipes()).anySatisfy((RecipeSpec recipe) -> {
      assertThat(recipe.key()).isEqualTo(ExplorersAtlas.ID);
      assertThat(omniscience.atlas().is(recipe.result())).isTrue();
    });
    platform.scheduler().advance(200);
    assertThat(registry.getRequiredAdaptation(ExplorersAtlas.ID)).isSameAs(omniscience);
  }

  @Test
  void theAtlasIsAnInertBookForAPlayerWithoutOmniscience() {
    player.holding(omniscience.atlas().create(CustomItem.Plain.INSTANCE));

    PlayerInteractEvent use = use();

    assertThat(use.useItem()).isEqualTo(UseResult.DENY);
    assertThat(opened).isEmpty();
  }

  @Test
  void aHolderOpensTheirAtlas() {
    learn(1);
    player.holding(omniscience.atlas().create(CustomItem.Plain.INSTANCE));

    use();

    assertThat(opened).hasSize(1);
  }

  @Test
  void itFillsWithTheBiomeAndStructureYouStandInOnce() {
    learn(1);
    standInVillage();

    omniscience.recordOwned(player);
    omniscience.recordOwned(player);

    AtlasRecord record = omniscience.record(player);
    assertThat(record.biomes).hasSize(1);
    assertThat(record.structures).hasSize(1);
    assertThat(record.structures.getFirst().looted).isFalse();
  }

  @Test
  void lootingAChestInsideAStructureMarksItLooted() {
    learn(1);
    standInVillage();

    adapt.post(new PlayerLootGenerateEvent(player, new Loc(platform.world(), 4D, 62D, 4D)));

    AtlasRecord record = omniscience.record(player);
    assertThat(record.structures).hasSize(1);
    assertThat(record.structures.getFirst().looted).isTrue();
  }

  @Test
  void lootingWithoutTheUpgradeRecordsNothing() {
    standInVillage();

    adapt.post(new PlayerLootGenerateEvent(player, new Loc(platform.world(), 4D, 62D, 4D)));

    assertThat(omniscience.record(player)).isNull();
  }

  @Test
  void pinsAreCappedByLevel() {
    learn(1);
    standInVillage();
    omniscience.recordOwned(player);
    AtlasRecord record = omniscience.record(player);
    String biome = record.biomes.getFirst().id;
    String village = record.structures.getFirst().id;

    assertThat(omniscience.togglePin(player, biome)).isEqualTo(AtlasRecord.Change.DONE);
    assertThat(omniscience.togglePin(player, village)).isEqualTo(AtlasRecord.Change.FULL);
    assertThat(omniscience.togglePin(player, "waypoint:404")).isEqualTo(AtlasRecord.Change.MISSING);
    assertThat(record.pinned(omniscience.pinsAllowed(1))).extracting((AtlasRecord.Place place) -> place.id).containsExactly(biome);
    assertThat(omniscience.pinsAllowed(2)).isEqualTo(3);
    assertThat(omniscience.pinsAllowed(3)).isEqualTo(5);
  }

  @Test
  void clickingAnEntryInTheAtlasPinsIt() {
    learn(1);
    omniscience.addWaypoint(player, "Camp");
    AtlasRecord record = omniscience.record(player);

    ChestMenu menu = AtlasGui.open(omniscience, player, 1, record, 0);
    menu.button(ChestMenu.slot(1, 0)).action().click(new MenuClick(player, null, 0, ClickKind.LEFT));

    assertThat(record.pins).containsExactly(record.waypoints.getFirst().id);
  }

  @Test
  void aPinShowsOnTheReadoutWithItsDirectionAndDistance() {
    learn(1);
    omniscience.addWaypoint(player, "Camp");
    AtlasRecord record = omniscience.record(player);
    player.moveTo(new Loc(platform.world(), 0.5D, 61D, 40.5D));

    omniscience.togglePin(player, record.waypoints.getFirst().id);

    assertThat(omniscience.readout(player, 1)).contains("Camp").contains("N").contains("40");
  }

  @Test
  void waypointsAreCappedByLevelAndNamedInPrivateChat() {
    learn(1);
    for (int index = 0; index < 5; index++) {
      assertThat(omniscience.addWaypoint(player, "Spot " + index)).isEqualTo(AtlasRecord.Change.DONE);
    }
    assertThat(omniscience.addWaypoint(player, "One too many")).isEqualTo(AtlasRecord.Change.FULL);
    assertThat(omniscience.waypointsAllowed(2)).isEqualTo(10);
    assertThat(omniscience.waypointsAllowed(3)).isEqualTo(20);

    omniscience.removeWaypoint(player, omniscience.record(player).waypoints.getFirst().id);
    omniscience.promptWaypoint(player);
    PlayerChatEvent answer = adapt.post(new PlayerChatEvent(player, "&cHome §lbase", false));
    PlayerChatEvent later = adapt.post(new PlayerChatEvent(player, "hello", false));

    assertThat(answer.isCancelled()).isTrue();
    assertThat(later.isCancelled()).isFalse();
    AtlasRecord.Place named = omniscience.record(player).waypoints.getLast();
    assertThat(named.name).contains("Home").doesNotContain("&").doesNotContain("§");
  }

  @Test
  void anExpiredWaypointPromptLeavesChatAlone() {
    learn(1);
    AtomicLong now = new AtomicLong(1_000_000L);
    AdaptCore stubbed = spy(adapt.core());
    when(stubbed.clock()).thenReturn(TestClocks.reading(now::get));
    TestCores.use(stubbed);
    try {
      omniscience.promptWaypoint(player);
      now.addAndGet(60_000L);

      PlayerChatEvent late = adapt.post(new PlayerChatEvent(player, "hello", false));

      assertThat(late.isCancelled()).isFalse();
    } finally {
      TestCores.use(adapt.core());
    }
    assertThat(omniscience.record(player).waypoints).isEmpty();
  }

  @Test
  void anotherPlayersAtlasNeverSeesYourWaypoints() {
    learn(1);
    omniscience.addWaypoint(player, "Secret");
    FakePlayer other = platform.player("Other", 4.5D, 61D, 0.5D);
    AdaptPlayer otherProfile = adapt.join(other);
    mutate(otherProfile);
    otherProfile.getData().getSkillLine("discovery").setAdaptation(omniscience, 1);

    assertThat(omniscience.record(other).waypoints).isEmpty();
    assertThat(omniscience.record(player).waypoints).hasSize(1);
  }

  @Test
  void theAtlasRemembersAcrossSessions() {
    learn(1);
    omniscience.addWaypoint(player, "Camp");

    AtlasRecord reread = AtlasRecord.parse(omniscience.getStorageString(player, Omniscience.ATLAS_STORAGE, null));

    assertThat(reread.waypoints).extracting((AtlasRecord.Place place) -> place.name).containsExactly("Camp");
  }

  @Test
  void theSearchOpensOnlyAtLevelThree() {
    learn(2);
    giveSearchCost();

    assertThat(omniscience.search(player)).isFalse();
    assertThat(paper()).isEqualTo(1);
    assertThat(player.foodLevel()).isEqualTo(20);
    ChestMenu menu = AtlasGui.open(omniscience, player, 2, omniscience.record(player), 0);
    assertThat(menu.button(ChestMenu.slot(0, 4))).isNull();
  }

  @Test
  void theSearchCostsOnePaperAndThreeHungerAndCoolsDown() {
    learn(3);
    giveSearchCost();

    assertThat(omniscience.search(player)).isTrue();
    assertThat(paper()).isZero();
    assertThat(player.foodLevel()).isEqualTo(17);

    player.inventory().addItem(FakeItem.of("paper"));
    assertThat(omniscience.search(player)).isFalse();
    assertThat(paper()).isEqualTo(1);
    assertThat(player.foodLevel()).isEqualTo(17);
  }

  @Test
  void theSearchPaysOnlyWithPlainPaper() {
    learn(3);
    player.setFoodLevel(20);
    FakeItem named = FakeItem.of("paper");
    named.setDisplayName("Treaty");
    FakeItem marked = FakeItem.of("paper");
    marked.data().set(DataKey.adapt("test-mark", DataCodecs.STRING), "kept");
    player.inventory().setItem(0, named);
    player.inventory().setItem(1, marked);

    assertThat(omniscience.search(player)).isFalse();
    assertThat(paper()).isEqualTo(2);

    player.inventory().setItem(5, FakeItem.of("paper"));
    assertThat(omniscience.search(player)).isTrue();
    assertThat(player.inventory().item(0).displayName()).isEqualTo("Treaty");
    assertThat(player.inventory().item(1).data().isEmpty()).isFalse();
    assertThat(paper()).isEqualTo(2);
  }

  @Test
  void theSearchRefusesWithoutPaperOrFood() {
    learn(3);
    player.setFoodLevel(20);

    assertThat(omniscience.search(player)).isFalse();

    player.inventory().addItem(FakeItem.of("paper"));
    player.setFoodLevel(2);
    assertThat(omniscience.search(player)).isFalse();
    assertThat(paper()).isEqualTo(1);
    assertThat(player.foodLevel()).isEqualTo(2);
  }

  @Test
  void theSearchSkipsStructuresAlreadyInTheAtlasAndReadsOnlyGeneratedChunks() {
    learn(3);
    standInVillage();
    omniscience.recordOwned(player);
    Box far = new Box(56D, 50D, -8D, 88D, 80D, 24D);
    platform.world().generated(new ChunkPos(0, 0), CompletableFuture.completedFuture(
        new ChunkStructures(List.of(new GeneratedStructure(Id.minecraft("village_plains"), VILLAGE, HERE)), List.of())));
    platform.world().generated(new ChunkPos(4, 0), CompletableFuture.completedFuture(
        new ChunkStructures(List.of(new GeneratedStructure(Id.minecraft("village_plains"), VILLAGE, far)), List.of())));
    giveSearchCost();
    omniscience.record(player).searchType = Omniscience.SEARCHABLE.indexOf(VILLAGE);

    assertThat(omniscience.search(player)).isTrue();
    for (int tick = 0; tick < 40 && omniscience.searchRunning(player); tick++) {
      omniscience.onTick();
      platform.scheduler().advance(1);
    }

    AtlasRecord record = omniscience.record(player);
    assertThat(record.lead).isNotNull();
    assertThat(record.lead.x).isEqualTo(72D);
    assertThat(platform.world().generatedRequests()).isNotEmpty();
    assertThat(omniscience.searchRunning(player)).isFalse();
  }

  @Test
  void aSearchThatFindsNothingSaysSo() {
    learn(3);
    omniscience.getConfig().searchRange = 32D;
    giveSearchCost();

    assertThat(omniscience.search(player)).isTrue();
    for (int tick = 0; tick < 40; tick++) {
      omniscience.onTick();
      platform.scheduler().advance(1);
    }

    assertThat(omniscience.searchRunning(player)).isFalse();
    assertThat(omniscience.record(player).lead).isNull();
  }

  private void standInVillage() {
    platform.world().structuresAt(new ChunkPos(0, 0), List.of(new GeneratedStructure(Id.minecraft("village_plains"), VILLAGE, HERE)));
  }

  private void giveSearchCost() {
    player.inventory().addItem(FakeItem.of("paper"));
    player.setFoodLevel(20);
  }

  private int paper() {
    int count = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && item.type().equals(Id.minecraft("paper"))) {
        count += item.amount();
      }
    }
    return count;
  }

  private PlayerInteractEvent use() {
    ItemRef item = player.inventory().mainHand();
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, item, null, BlockFace.UP,
        null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void learn(int level) {
    mutate(profile);
    profile.getData().getSkillLine("discovery").setAdaptation(omniscience, level);
  }

  private void mutate(AdaptPlayer target) {
    target.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("discovery", "swords")) {
      PlayerSkillLine line = target.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(target.getData(), registry.getSkill("discovery"), registry.getSkill("swords"), "Seeker"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

}
